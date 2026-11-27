/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcWrapperTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	@Test
	void arrayAndAsciiBindingsReachDriverIncludingNull() throws Exception {
		for (boolean logging : new boolean[] { false, true }) {
			List<String> calls = new ArrayList<>();
			Array array = proxy(Array.class, (p, m, a) -> "array");
			var stream = new ByteArrayInputStream(new byte[] { 65 });
			var nativeStatement = proxy(PreparedStatement.class, (p, m, a) -> {
				calls.add(m.getName());
				assertEquals(1, a[0]);
				if (a[1] != null) {
					assertSame(m.getName().equals("setArray") ? array : stream, a[1]);
				}
				return null;
			});
			var statement = statement(nativeStatement, logging);
			statement.setArray(1, array);
			statement.setArray(1, null);
			statement.setAsciiStream(1, stream);
			statement.setAsciiStream(1, null);
			assertEquals(List.of("setArray", "setArray", "setAsciiStream", "setAsciiStream"), calls);
			assertEquals(1, stream.available());
		}
	}

	private SqlappPreparedStatement statement(PreparedStatement delegate, boolean logging) {
		return new SqlappPreparedStatement(delegate, "SELECT ?", null) {
			@Override
			protected boolean isSqlLogEnabled() {
				return logging;
			}
		};
	}

	@Test
	void lengthBasedStreamBindingsPreserveObjectAndLengthWithoutReading() throws Exception {
		for (boolean logging : new boolean[] { false, true }) {
			for (Method method : PreparedStatement.class.getMethods()) {
				Class<?>[] types = method.getParameterTypes();
				if (types.length != 3 || types[0] != int.class
						|| (types[1] != java.io.InputStream.class && types[1] != java.io.Reader.class)
						|| (types[2] != int.class && types[2] != long.class)) {
					continue;
				}
				for (boolean nullValue : new boolean[] { false, true }) {
					var stream = new ByteArrayInputStream(new byte[] { 65 });
					var reader = new StringReader("A");
					Object value = nullValue ? null : types[1] == java.io.InputStream.class ? stream : reader;
					Object length;
					if (types[2] == int.class) {
						length = Integer.valueOf(1000);
					} else {
						length = Long.valueOf((long) Integer.MAX_VALUE + 1);
					}
					List<Method> calls = new ArrayList<>();
					var delegate = proxy(PreparedStatement.class, (p, m, a) -> {
						calls.add(m);
						assertSame(value, a[1]);
						assertEquals(length, a[2]);
						return null;
					});
					method.invoke(statement(delegate, logging), 1, value, length);
					assertEquals(List.of(method), calls);
					assertEquals(1, stream.available());
					assertEquals('A', reader.read());
				}
			}
		}
	}

	@Test
	void clearWarningsDoesNotCancelStatement() throws Exception {
		List<String> calls = new ArrayList<>();
		var delegate = proxy(Statement.class, (p, m, a) -> {
			calls.add(m.getName());
			return null;
		});
		new SqlappStatement(delegate, null).clearWarnings();
		assertEquals(List.of("clearWarnings"), calls);
	}

	@Test
	void builderPreservesFluentOptionsAndWrapsConnection() throws Exception {
		List<String> calls = new ArrayList<>();
		var nativeConnection = proxy(Connection.class, (p, m, a) -> null);
		var key = proxy(ShardingKey.class, (p, m, a) -> null);
		var nativeBuilder = proxy(ConnectionBuilder.class, (p, m, a) -> {
			calls.add(m.getName());
			return switch (m.getName()) {
			case "build" -> nativeConnection;
			case "user" -> {
				assertEquals("user", a[0]);
				yield p;
			}
			case "password" -> {
				assertEquals("password", a[0]);
				yield p;
			}
			default -> {
				assertSame(key, a[0]);
				yield p;
			}
			};
		});
		var ds = new SqlappDataSource(proxy(DataSource.class, (p, m, a) -> nativeBuilder));
		ds.setDialectOptions(Map.of("oracle.maxStringSize", "EXTENDED"));
		ds.setDebug(true);
		var builder = ds.createConnectionBuilder();
		assertSame(builder, builder.user("user").password("password").shardingKey(key).superShardingKey(key));
		var connection = assertInstanceOf(SqlappConnection.class, builder.build());
		assertSame(nativeConnection, connection.getNativeObject());
		assertEquals(ds.getDialectOptions(), connection.getDialectOptions());
		assertTrue(connection.isDebug());
		assertEquals(List.of("user", "password", "shardingKey", "superShardingKey", "build"), calls);
	}

	@Test
	void builderFailureIsPropagated() throws Exception {
		var failure = new SQLException("build failed");
		var builder = proxy(ConnectionBuilder.class, (p, m, a) -> {
			throw failure;
		});
		var ds = new SqlappDataSource(proxy(DataSource.class, (p, m, a) -> builder));
		assertSame(failure, assertThrows(SQLException.class, () -> ds.createConnectionBuilder().build()));
	}

	@Test
	void batchSnapshotsAreReleasedAfterExecutionAndClear() throws Exception {
		List<String> logs = new ArrayList<>();
		var delegate = proxy(PreparedStatement.class, (p, m, a) ->
			m.getName().equals("executeBatch") ? new int[] { 1 } : null);
		var statement = new SqlappPreparedStatement(delegate, "INSERT INTO T VALUES (?)", null) {
			@Override protected boolean isSqlLogEnabled() { return true; }
			@Override protected void logSql(String sql, long start, long end) { logs.add(sql); }
		};
		statement.setInt(1, 123);
		statement.addBatch();
		statement.executeBatch();
		assertTrue(logs.stream().anyMatch(sql -> sql.contains("123")));
		logs.clear();
		statement.executeBatch();
		assertFalse(logs.stream().anyMatch(sql -> sql.contains("123")));
		statement.addBatch();
		statement.clearBatch();
		statement.executeBatch();
		assertFalse(logs.stream().anyMatch(sql -> sql.contains("123")));
	}

	@Test
	void metadataFailureDoesNotHideExecutionResultOrException() throws Exception {
		var connection = new SqlappConnection(proxy(Connection.class, (p, m, a) -> {
			throw new SQLException("metadata unavailable");
		}));
		var failure = new SQLException("execution failed");
		var delegate = proxy(Statement.class, (p, m, a) -> {
			if (a[0].equals("fail")) { throw failure; }
			return 7;
		});
		var statement = new SqlappStatement(delegate, connection) {
			@Override protected boolean isSqlLogEnabled() { return true; }
		};
		assertEquals(7, statement.executeUpdate("ok"));
		assertSame(failure, assertThrows(SQLException.class, () -> statement.executeUpdate("fail")));
	}

	@Test
	void typedObjectBindingsAndLogsUseTheRequestedTypeAndLatestValue() throws Exception {
		for (boolean logging : new boolean[] { false, true }) {
			for (boolean sqlType : new boolean[] { false, true }) {
				for (boolean scaled : new boolean[] { false, true }) {
					List<String> logs = new ArrayList<>();
					List<Object[]> bindings = new ArrayList<>();
					var delegate = proxy(PreparedStatement.class, (p, m, a) -> {
						if (m.getName().equals("setObject")) { bindings.add(a); }
						return m.getName().equals("executeUpdate") ? 1 : null;
					});
					var statement = new SqlappPreparedStatement(delegate, "INSERT INTO T VALUES (?, ?, ?)", null) {
						@Override protected boolean isSqlLogEnabled() { return logging; }
						@Override protected void logSql(String sql, long start, long end) { logs.add(sql); }
					};
					statement.setInt(1, 1);
					statement.setInt(2, 2);
					statement.setInt(3, 10);
					for (Object value : new Object[] { "abc", null }) {
						if (sqlType) {
							if (scaled) { statement.setObject(3, value, JDBCType.VARCHAR, 0); }
							else { statement.setObject(3, value, JDBCType.VARCHAR); }
						} else {
							if (scaled) { statement.setObject(3, value, Types.VARCHAR, 0); }
							else { statement.setObject(3, value, Types.VARCHAR); }
						}
						assertEquals(1, statement.executeUpdate());
						var binding = bindings.getLast();
						assertEquals(3, binding[0]);
						assertEquals(value, binding[1]);
						assertEquals(sqlType ? JDBCType.VARCHAR : Types.VARCHAR, binding[2]);
						assertEquals(scaled ? 4 : 3, binding.length);
						if (logging) {
							assertEquals("INSERT INTO T VALUES (1, 2, " + (value == null ? "null" : "'abc'") + ")", logs.getLast());
						}
					}
				}
			}
		}
	}

	@Test
	void namedCallableObjectWithScalePreservesAllArguments() throws Exception {
		List<Object[]> calls = new ArrayList<>();
		var delegate = proxy(CallableStatement.class, (p, m, a) -> {
			assertEquals("setObject", m.getName());
			assertEquals(String.class, m.getParameterTypes()[0]);
			calls.add(a);
			return null;
		});
		var statement = new SqlappCallableStatement(delegate, "{call P(?)}", null);
		statement.setObject("amount", 123, JDBCType.DECIMAL, 2);
		statement.setObject("amount", null, JDBCType.DECIMAL, 0);
		assertArrayEquals(new Object[] { "amount", 123, JDBCType.DECIMAL, 2 }, calls.get(0));
		assertArrayEquals(new Object[] { "amount", null, JDBCType.DECIMAL, 0 }, calls.get(1));
	}

	@Test
	void ambiguousSqlLogsOriginalTextAndBindingsWithoutTruncation() throws Exception {
		for (String sql : List.of("SELECT '?' AS marker, ? AS value", "SELECT 1 /* ? */ WHERE X = ?",
				"SELECT 1 -- ?\n WHERE X = ?", "SELECT $$?$$, ?", "SELECT [a?], ?", "SELECT ?")) {
			List<String> logs = new ArrayList<>();
			var delegate = proxy(PreparedStatement.class, (p, m, a) ->
				m.getName().equals("executeUpdate") ? 1 : null);
			var statement = new SqlappPreparedStatement(delegate, sql, null) {
				@Override protected boolean isSqlLogEnabled() { return true; }
				@Override protected void logSql(String text, long start, long end) { logs.add(text); }
			};
			statement.setInt(1, 123);
			assertEquals(1, statement.executeUpdate());
			if (sql.equals("SELECT ?")) { assertEquals("SELECT 123", logs.getLast()); }
			else { assertEquals(sql + "\n/* parameters: [123] */", logs.getLast()); }
		}
	}

	@Test
	void failedSetterAndBatchRegistrationDoNotChangeLogState() throws Exception {
		List<String> logs = new ArrayList<>();
		var failure = new SQLException("rejected");
		var delegate = proxy(PreparedStatement.class, (p, m, a) -> {
			if (m.getName().equals("addBatch") || (m.getName().equals("setInt") && a[1].equals(20))) {
				throw failure;
			}
			return m.getName().equals("executeUpdate") ? 1 : m.getName().equals("executeBatch") ? new int[0] : null;
		});
		var statement = new SqlappPreparedStatement(delegate, "SELECT ?", null) {
			@Override protected boolean isSqlLogEnabled() { return true; }
			@Override protected void logSql(String sql, long start, long end) { logs.add(sql); }
		};
		statement.setInt(1, 10);
		assertSame(failure, assertThrows(SQLException.class, () -> statement.setInt(1, 20)));
		statement.executeUpdate();
		assertEquals("SELECT 10", logs.getLast());
		assertSame(failure, assertThrows(SQLException.class, statement::addBatch));
		logs.clear();
		statement.executeBatch();
		assertFalse(logs.stream().anyMatch(sql -> sql.contains("SELECT")));
	}

	@Test
	void releaseFailuresAreReportedAndDoNotReplaceOperationFailure() {
		for (boolean transaction : new boolean[] { false, true }) {
			for (boolean closeSource : new boolean[] { false, true }) {
				var closeFailure = new SQLException("close failed");
				var connection = proxy(Connection.class, (p, m, a) -> {
					if (m.getName().equals("close")) { throw closeFailure; }
					return null;
				});
				var source = proxy(DataSource.class, (p, m, a) -> connection);
				for (boolean failOperation : new boolean[] { false, true }) {
					var operationFailure = new IllegalStateException("operation failed");
					com.sqlapp.jdbc.function.ExceptionConsumer<Connection> action = c -> {
						if (failOperation) { throw operationFailure; }
					};
					var result = assertThrows(RuntimeException.class, () -> {
						if (transaction && closeSource) { DataSourceConnectionUtils.executeTranAndCloseDataSource(source, action); }
						else if (transaction) { DataSourceConnectionUtils.executeTran(source, action); }
						else if (closeSource) { DataSourceConnectionUtils.executeAndCloseDataSource(source, action); }
						else { DataSourceConnectionUtils.execute(source, action); }
					});
					if (failOperation) {
						assertSame(operationFailure, result);
						assertEquals(1, result.getSuppressed().length);
						assertSame(closeFailure, result.getSuppressed()[0].getCause());
					} else { assertSame(closeFailure, result.getCause()); }
				}
			}
		}
	}
}
