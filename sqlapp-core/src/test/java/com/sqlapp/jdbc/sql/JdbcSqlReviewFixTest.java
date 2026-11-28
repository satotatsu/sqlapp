package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CaseInsensitiveGetMap;

class JdbcSqlReviewFixTest {
	private static final String ROWS = "SELECT v FROM (VALUES(1),(2),(3),(4),(5)) t(v) ORDER BY v ";

	private SqlNode parse(String sql) { return SqlParser.getInstance().parse(null, sql); }
	private Connection database() throws SQLException {
		return DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
	}
	private ResultSetNextHandler collect(List<Integer> rows) {
		return new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet resultSet) throws SQLException { rows.add(resultSet.getInt(1)); }
		};
	}

	@Test
	void inactiveNativeClauseRetainsClientPaging() throws Exception {
		SqlNode node = parse(ROWS + "/*if nativePaging*/ OFFSET /*_offset*/0 ROWS FETCH NEXT /*_row*/10 ROWS ONLY /*end*/");
		try (Connection connection = database()) {
			for (boolean enabled : new boolean[] { false, true }) {
				ParametersContext context = new ParametersContext();
				context.put("nativePaging", enabled);
				List<Integer> rows = new ArrayList<>();
				JdbcQueryHandler handler = new JdbcQueryHandler(node, collect(rows));
				handler.execute(connection, context, 2, 1);
				assertEquals(List.of(2, 3), rows);
				assertEquals(2, handler.getFetchSizeResult());
				assertEquals(enabled, context.containsKeyInternal("_row"));
			}
		}
	}

	@Test
	void evaluatedLiteralAndMarkerUsageSurvivesNestedIncludes() throws Exception {
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("page", "/*@name=_offset;type=int@*/ /*@name=_row;type=int@*/ "
				+ "OFFSET /*# _offset */ ROWS FETCH NEXT /*# _row */ ROWS ONLY");
		registry.put("nested", "/*include 'page'*/");
		registry.put("query", ROWS + "/*if enabled*/ /*include 'nested'*/ /*end*/");
		try (Connection connection = database()) {
			for (boolean enabled : new boolean[] { false, true }) {
				ParametersContext context = new ParametersContext();
				context.put("enabled", enabled);
				List<Integer> rows = new ArrayList<>();
				new JdbcQueryHandler(registry.get("query"), collect(rows)).execute(connection, context, 2, 1);
				assertEquals(List.of(2, 3), rows);
			}
		}
		SqlParameterCollection plainLiteral = parse("OFFSET /*# _offset */ ROWS").eval(Map.of("_offset", 1));
		assertTrue(plainLiteral.hasEvaluatedParameter("_offset"));
	}

	@Test
	void contextFactoryHooksRunAndDefaultEvaluationIsNotRepeated() throws Exception {
		AtomicInteger evaluations = new AtomicInteger();
		SqlNode node = new SqlNode("VALUES(1),(2),(3)") {
			@Override
			public boolean eval(Object context, SqlParameterCollection parameters) {
				evaluations.incrementAndGet();
				return super.eval(context, parameters);
			}
		};
		AtomicInteger normal = new AtomicInteger();
		AtomicInteger scroll = new AtomicInteger();
		ParametersContext context = new ParametersContext();
		List<Integer> rows = new ArrayList<>();
		JdbcQueryHandler handler = new JdbcQueryHandler(node, collect(rows)) {
			@Override
			protected StatementSqlParametersHolder createStatement(Connection connection, Object actual, Integer limit)
					throws SQLException {
				assertSame(context, actual);
				normal.incrementAndGet();
				return super.createStatement(connection, actual, limit);
			}
			@Override
			protected StatementSqlParametersHolder createStatementScrollInsensitive(Connection connection, ParametersContext actual)
					throws SQLException {
				assertSame(context, actual);
				scroll.incrementAndGet();
				return super.createStatementScrollInsensitive(connection, actual);
			}
		};
		try (Connection connection = database()) {
			handler.execute(connection, context);
			assertEquals(List.of(1, 2, 3), rows);
			rows.clear();
			handler.execute(connection, context, 1, 1);
			assertEquals(List.of(2), rows);
		}
		assertEquals(1, normal.get());
		assertEquals(1, scroll.get());
		assertEquals(2, evaluations.get());
		assertEquals(4, handler.getFetchSizeResult());
	}

	@Test
	void reentrantExecutionRestoresCreationScope() throws Exception {
		List<Integer> rows = new ArrayList<>();
		boolean[] nested = { false };
		JdbcQueryHandler[] handler = new JdbcQueryHandler[1];
		try (Connection connection = database()) {
			handler[0] = new JdbcQueryHandler(new SqlNode("VALUES(1),(2)"), new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet resultSet) throws SQLException {
					rows.add(resultSet.getInt(1));
					if (!nested[0]) {
						nested[0] = true;
						handler[0].execute(connection, new ParametersContext());
					}
				}
			});
			handler[0].execute(connection, new ParametersContext());
			assertEquals(List.of(1, 1, 2, 2), rows);
			assertEquals(4, handler[0].getFetchSizeResult());
		}
	}

	@Test
	void legacyFactoryOwnEvaluationReleasesSharedValuesOnce() throws Exception {
		AtomicInteger closes = new AtomicInteger();
		ByteArrayInputStream value = new ByteArrayInputStream(new byte[] { 1 }) {
			@Override
			public void close() { closes.incrementAndGet(); }
		};
		java.sql.ResultSet result = (java.sql.ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { java.sql.ResultSet.class }, (proxy, method, args) -> method.getName().equals("next") ? false : null);
		PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> method.getName().equals("executeQuery") ? result : null);
		Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> statement);
		JdbcQueryHandler handler = new JdbcQueryHandler(parse("VALUES(/*payload*/0)"), collect(new ArrayList<>())) {
			@Override
			protected StatementSqlParametersHolder createStatement(Connection actual, Object context, Integer limit)
					throws SQLException {
				SqlParameterCollection parameters = getNode().eval(context);
				return new StatementSqlParametersHolder(createStatement(actual, parameters, limit), parameters);
			}
		};
		ParametersContext context = new ParametersContext();
		context.put("payload", value);
		handler.execute(connection, context);
		assertEquals(1, closes.get());
	}

	@Test
	void loopContextsPreserveCaseInsensitiveLookupAndImmutableInput() {
		CaseInsensitiveGetMap<Object> context = new CaseInsensitiveGetMap<>();
		context.put("ITEMS", List.of(1, 2));
		context.put("VALUE", 7);
		context.put("x", 99);
		SqlNode node = parse("/*for x:ITEMS*/ /*value*/0 + /*x*/0 /*end*/");
		assertEquals(List.of(7, 1, 7, 2), node.eval(context).getBindParameters().stream()
				.flatMap(BindParameterHolder::stream).map(BindParameter::getValue).toList());
		assertEquals(99, context.get("x"));
		assertFalse(context.containsKey("x_index"));
		assertEquals(List.of(7, 1), node.eval(Map.of("ITEMS", List.of(1), "value", 7)).getBindParameters().stream()
				.flatMap(BindParameterHolder::stream).map(BindParameter::getValue).toList());
	}

	@Test
	void cloneMetadataIsIndependentAndOriginalCanStillRefreshBindings() {
		SqlNode node = parse("id IN /*ids*/(0)");
		SqlParameterCollection original = node.eval(Map.of("ids", List.of(1, 2)));
		SqlParameterCollection copy = original.clone();
		assertNotSame(original.getSqlParts(), copy.getSqlParts());
		copy.getSqlParts().clear();
		copy.registerEvaluatedParameter("copyOnly");
		assertFalse(original.hasEvaluatedParameter("copyOnly"));
		assertTrue(copy.hasEvaluatedParameter("ids"));
		assertTrue(node.reEval(Map.of("ids", List.of(3, 4)), original));
	}

	@Test
	void parameterCollectionClosesStatementOnSetupFailure() {
		for (String failingMethod : List.of("setFetchSize", "setFetchDirection")) {
			AtomicInteger closes = new AtomicInteger();
			SQLException injected = new SQLException(failingMethod);
			PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
						if (method.getName().equals("close")) closes.incrementAndGet();
						if (method.getName().equals(failingMethod)) throw injected;
						return null;
					});
			Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> statement);
			SqlParameterCollection parameters = new SqlParameterCollection(null).addSql("VALUES(1)");
			parameters.setFetchSize(2);
			assertSame(injected, assertThrows(SQLException.class, () -> parameters.createStatement(connection)));
			assertEquals(1, closes.get());
		}
	}

	@Test
	void ordinaryAndLimitedFetchStatisticsAccumulateOnlyHandledRows() throws Exception {
		AtomicInteger handled = new AtomicInteger();
		JdbcHandler handler = new JdbcHandler(parse("VALUES(1),(2)"), (ResultSetNext) resultSet -> handled.incrementAndGet());
		try (Connection connection = database()) {
			handler.execute(connection, new ParametersContext());
			handler.execute(connection, new ParametersContext());
			assertEquals(4, handled.get());
			assertEquals(4, handler.getFetchSizeResult());
			JdbcQueryHandler limited = new JdbcQueryHandler(parse("VALUES(1),(2)"), collect(new ArrayList<>()));
			limited.execute(connection, new ParametersContext(), 0, null);
			assertEquals(0, limited.getFetchSizeResult());
			limited.execute(connection, new ParametersContext(), 1, null);
			assertEquals(1, limited.getFetchSizeResult());
		}
	}
}
