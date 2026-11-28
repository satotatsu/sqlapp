package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.CommentNode;
import com.sqlapp.jdbc.sql.node.ForNode;
import com.sqlapp.jdbc.sql.node.SqlNode;

class JdbcSqlEvaluationOrderTest {
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
	void declarationsDoNotDisableClientPagingWhenNativeClauseIsInactive() throws Exception {
		SqlNode node = parse("/*@name=_row;type=int@*/ /*@name=_offset;type=int@*/ "
				+ "SELECT v FROM (VALUES(1),(2),(3),(4),(5)) t(v) ORDER BY v "
				+ "/*if nativePaging*/ OFFSET /*_offset*/0 ROWS FETCH NEXT /*_row*/10 ROWS ONLY /*end*/");
		try (Connection connection = database()) {
			for (boolean enabled : new boolean[] { false, true }) {
				ParametersContext context = new ParametersContext();
				context.put("nativePaging", enabled);
				List<Integer> rows = new ArrayList<>();
				new JdbcQueryHandler(node, collect(rows)).execute(connection, context, 2, 1);
				assertEquals(List.of(2, 3), rows);
				assertEquals(enabled, context.containsKeyInternal("_row"));
			}
		}
		assertFalse(parse("/*@name=_row;type=int@*/ VALUES(1)").eval(Map.of()).hasEvaluatedParameter("_row"));
	}

	@Test
	void contextChangesMadeByNormalAndScrollableHooksAreEvaluatedOnce() throws Exception {
		for (Integer offset : new Integer[] { null, 0 }) {
			AtomicInteger evaluations = new AtomicInteger();
			SqlNode template = parse("SELECT v FROM (VALUES(1),(2),(3)) t(v) WHERE v >= /*value*/0 ORDER BY v");
			SqlNode counted = new SqlNode("") {
				@Override
				public boolean eval(Object context, SqlParameterCollection parameters) {
					evaluations.incrementAndGet();
					return template.eval(context, parameters);
				}
			};
			List<Integer> rows = new ArrayList<>();
			JdbcQueryHandler handler = new JdbcQueryHandler(counted, collect(rows)) {
				@Override
				protected StatementSqlParametersHolder createStatement(Connection connection, Object context, Integer limit)
						throws SQLException {
					((ParametersContext) context).put("value", 2);
					return super.createStatement(connection, context, limit);
				}
				@Override
				protected StatementSqlParametersHolder createStatementScrollInsensitive(Connection connection, ParametersContext context)
						throws SQLException {
					context.put("value", 2);
					return super.createStatementScrollInsensitive(connection, context);
				}
			};
			ParametersContext context = new ParametersContext();
			context.put("value", 1);
			try (Connection connection = database()) { handler.execute(connection, context, 1, offset); }
			assertEquals(List.of(2), rows);
			assertEquals(1, evaluations.get());
		}
	}

	@Test
	void legacyFactoryThatEvaluatesItsOwnSqlRunsOnlyOnce() throws Exception {
		AtomicInteger evaluations = new AtomicInteger();
		SqlNode node = new SqlNode("") {
			@Override
			public boolean eval(Object context, SqlParameterCollection parameters) {
				parameters.addSql("VALUES(");
				parameters.add("value", evaluations.incrementAndGet());
				parameters.addSql(")");
				return true;
			}
		};
		List<Integer> rows = new ArrayList<>();
		JdbcQueryHandler handler = new JdbcQueryHandler(node, collect(rows)) {
			@Override
			protected StatementSqlParametersHolder createStatement(Connection connection, Object context, Integer limit)
					throws SQLException {
				SqlParameterCollection parameters = getNode().eval(context);
				return new StatementSqlParametersHolder(createStatement(connection, parameters, limit), parameters);
			}
		};
		try (Connection connection = database()) { handler.execute(connection, new ParametersContext()); }
		assertEquals(1, evaluations.get());
		assertEquals(List.of(1), rows);
	}

	@Test
	void failedCreationRestoresPagingSeedsAndDoesNotLeakTheRequest() throws Exception {
		ParametersContext context = new ParametersContext();
		context.put("_row", 99);
		boolean[] fail = { true };
		List<Integer> rows = new ArrayList<>();
		JdbcQueryHandler handler = new JdbcQueryHandler(new SqlNode("VALUES(1),(2),(3)"), collect(rows)) {
			@Override
			protected StatementSqlParametersHolder createStatementScrollInsensitive(Connection connection, ParametersContext actual)
					throws SQLException {
				if (fail[0]) { fail[0] = false; throw new SQLException("injected"); }
				return super.createStatementScrollInsensitive(connection, actual);
			}
		};
		try (Connection connection = database()) {
			assertThrows(RuntimeException.class, () -> handler.execute(connection, context, 2, 1));
			assertEquals(99, context.get("_row"));
			assertFalse(context.containsKeyInternal("_offset"));
			handler.execute(connection, context, 2, null);
			assertEquals(List.of(1, 2), rows);
			assertEquals(99, context.get("_row"));
		}
	}

	@Test
	void loopMapRemovalAndViewsChangeOnlyTheLoopScope() {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("items", List.of(1));
		source.put("value", 7);
		source.put("other", 8);
		source.put("nullable", null);
		Map<String, Object> before = new LinkedHashMap<>(source);
		SqlNode root = parse("/*for x:items*/ /*end*/");
		ForNode loop = (ForNode) root.getChildNodes().stream().filter(ForNode.class::isInstance).findFirst().orElseThrow();
		loop.addChildNode(new CommentNode() {
			@Override
			@SuppressWarnings("unchecked")
			public boolean eval(Object context, SqlParameterCollection parameters) {
				Map<Object, Object> local = (Map<Object, Object>) context;
				assertEquals(7, local.remove("value"));
				assertFalse(local.containsKey("value"));
				assertNull(local.get("value"));
				assertNull(local.remove("nullable"));
				assertFalse(local.containsKey("nullable"));
				assertTrue(local.keySet().remove("other"));
				local.put("value", 9);
				Map.Entry<Object, Object> entry = local.entrySet().stream().filter(e -> e.getKey().equals("value")).findFirst().orElseThrow();
				assertEquals(9, entry.setValue(10));
				assertEquals(10, local.get("value"));
				assertTrue(local.values().remove(10));
				assertFalse(local.containsKey("value"));
				local.clear();
				assertTrue(local.isEmpty());
				local.put("new", 11);
				assertEquals(Map.of("new", 11), local);
				return true;
			}
		});
		root.eval(source);
		assertEquals(before, source);
	}

	@Test
	void mergePreservesBindCountsAndRefreshesWithoutChangingSql() {
		SqlParameterCollection left = parse("a=/*a*/0").eval(Map.of("a", 1));
		SqlParameterCollection right = parse("b=/*b*/0").eval(Map.of("b", 2));
		left.addSql(" AND ").merge(right);
		String sql = left.getSql();
		assertEquals(2, left.getParameterSize());
		assertTrue(parse("a=/*a*/0 AND b=/*b*/0").reEval(Map.of("a", 3, "b", 4), left));
		assertEquals(sql, left.getSql());
		assertEquals(List.of(3, 4), left.getBindParameters().stream().flatMap(BindParameterHolder::stream).map(BindParameter::getValue).toList());
		BindParameterHolder group = new BindParameterHolder();
		group.getBindParameters().add(new BindParameter());
		group.getBindParameters().add(new BindParameter());
		SqlParameterCollection grouped = new SqlParameterCollection(null);
		grouped.add(group);
		SqlParameterCollection merged = new SqlParameterCollection(null).merge(grouped);
		assertEquals(2, merged.getParameterSize());
		merged.merge(merged);
		assertEquals(4, merged.getParameterSize());
		assertEquals(4, merged.getBindParameters().stream().flatMap(BindParameterHolder::stream).count());
	}
}
