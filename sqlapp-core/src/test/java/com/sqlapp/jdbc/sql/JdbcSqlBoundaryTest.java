package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
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

class JdbcSqlBoundaryTest {
	private SqlNode parse(String sql) {
		return SqlParser.getInstance().parse(null, sql);
	}

	private Connection database() throws SQLException {
		return DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
	}

	private JdbcQueryHandler query(SqlNode node, List<Integer> rows) {
		return new JdbcQueryHandler(node, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet resultSet) throws SQLException {
				rows.add(resultSet.getInt(1));
			}
		});
	}

	@Test
	void nestedIncludePagingUsesExplicitValuesWithoutClientSideOffset() throws Exception {
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("page", "OFFSET /*_offset*/0 ROWS FETCH NEXT /*_row*/10 ROWS ONLY");
		registry.put("nested", "/*include 'page'*/");
		registry.put("query", "SELECT v FROM (VALUES(1),(2),(3),(4),(5)) t(v) ORDER BY v /*include 'nested'*/");
		SqlNode node = registry.get("query");
		try (Connection connection = database()) {
			List<Integer> rows = new ArrayList<>();
			ParametersContext context = new ParametersContext();
			context.put("_row", 99);
			context.put("_offset", 3);
			query(node, rows).execute(connection, context, 2, 1);
			assertEquals(List.of(2, 3), rows);
			assertEquals(2, context.get("_row"));
			assertEquals(1, context.get("_offset"));
			rows.clear();
			query(node, rows).execute(connection, node.eval(context), 2, 1);
			assertEquals(List.of(2, 3), rows);
		}
	}

	@Test
	void unselectedIncludeKeepsClientPagingAndPlainNodesExecute() throws Exception {
		MapSqlRegistry registry = new MapSqlRegistry();
		registry.put("page", "OFFSET /*_offset*/0 ROWS FETCH NEXT /*_row*/10 ROWS ONLY");
		registry.put("query", "SELECT v FROM (VALUES(1),(2),(3),(4)) t(v) ORDER BY v /*if enabled*/ /*include 'page'*/ /*end*/");
		try (Connection connection = database()) {
			List<Integer> rows = new ArrayList<>();
			ParametersContext context = new ParametersContext();
			context.put("enabled", false);
			query(registry.get("query"), rows).execute(connection, context, 2, 1);
			assertEquals(List.of(2, 3), rows);
			assertFalse(context.containsKeyInternal("_row"));
			rows.clear();
			query(new SqlNode("VALUES(1)"), rows).execute(connection);
			assertEquals(List.of(1), rows);
		}
	}

	private static class Stream extends ByteArrayInputStream {
		int closes;
		Stream() { super(new byte[] { 1 }); }
		@Override
		public void close() { closes++; }
	}

	private Object defaultValue(Class<?> type) {
		if (type == boolean.class) return false;
		if (type == int.class) return 0;
		if (type == long.class) return -1L;
		return null;
	}

	@Test
	void fixedBatchClosesRetiredStreamsAfterExecutionOnSuccessAndFailure() throws Exception {
		for (boolean fail : new boolean[] { false, true }) {
			List<Stream> streams = List.of(new Stream(), new Stream(), new Stream(), new Stream());
			AtomicInteger pending = new AtomicInteger();
			AtomicInteger executions = new AtomicInteger();
			PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
						if (method.getName().equals("setBinaryStream")) {
							assertEquals(0, ((Stream) args[1]).closes);
							if (args[1] == streams.get(2)) assertEquals(1, streams.get(0).closes);
						} else if (method.getName().equals("addBatch")) {
							pending.incrementAndGet();
						} else if (method.getName().equals("executeBatch")) {
							if (executions.incrementAndGet() == 2 && fail) throw new SQLException("batch failure");
							int[] counts = new int[pending.getAndSet(0)];
							java.util.Arrays.fill(counts, 1);
							return counts;
						}
						return defaultValue(method.getReturnType());
					});
			Connection connection = connection(statement, false);
			JdbcBatchUpdateHandler handler = new JdbcBatchUpdateHandler(parse("INSERT INTO t(v) VALUES (/*payload*/0)"));
			handler.setBatchSize(2);
			List<Map<String, Stream>> contexts = streams.stream().map(stream -> Map.of("payload", stream)).toList();
			if (fail) assertThrows(SQLException.class, () -> handler.execute(connection, contexts));
			else handler.execute(connection, contexts);
			streams.forEach(stream -> assertEquals(1, stream.closes));
		}
	}

	private Connection connection(PreparedStatement statement, boolean callable) {
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> method.getName().equals(callable ? "prepareCall" : "prepareStatement")
						? statement : defaultValue(method.getReturnType()));
	}

	@Test
	void bindingAndSetupFailuresClosePreparedCallableAndScrollableStatements() throws Exception {
		for (String path : List.of("prepared", "callable", "scroll", "fetchSize")) {
			AtomicInteger closes = new AtomicInteger();
			SQLException injected = new SQLException("injected " + path);
			PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { CallableStatement.class }, (proxy, method, args) -> {
						if (method.getName().equals("close")) closes.incrementAndGet();
						if (method.getName().equals(path.equals("fetchSize") ? "setFetchSize" : "setInt")) throw injected;
						return defaultValue(method.getReturnType());
					});
			Connection connection = connection(statement, path.equals("callable"));
			SqlNode node = parse("SELECT /*value*/0");
			ParametersContext context = new ParametersContext();
			context.put("value", 1);
			if (path.equals("scroll")) {
				RuntimeException error = assertThrows(RuntimeException.class,
						() -> query(node, new ArrayList<>()).execute(connection, context, 1, 1));
				assertSame(injected, error.getCause());
			} else {
				JdbcHandler handler = path.equals("callable") ? new CallableJdbcHandler(node) : new JdbcHandler(node);
				assertSame(injected, assertThrows(SQLException.class, () -> handler.execute(connection, context)));
			}
			assertEquals(1, closes.get(), path);
		}
	}
}
