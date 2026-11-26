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
}
