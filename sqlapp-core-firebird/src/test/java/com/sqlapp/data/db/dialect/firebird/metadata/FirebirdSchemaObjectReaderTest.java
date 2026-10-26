/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;

class FirebirdSchemaObjectReaderTest {
	@Test
	void bulkReadsUseThreeQueriesAndPreserveRawQuotes() throws Exception {
		var queries = new AtomicInteger();
		Connection connection = proxy(Connection.class, (method, args) -> {
			if (!method.equals("prepareStatement"))
				throw new AssertionError(method);
			int query = queries.incrementAndGet();
			assertTrue(((String) args[0]).contains(query == 3 ? "WHERE COALESCE(C.RDB$SYSTEM_FLAG, 0) = 0"
					: "WHERE COALESCE(RDB$SYSTEM_FLAG, 0) = 0"));
			var position = new AtomicInteger();
			ResultSet rows = proxy(ResultSet.class, (call, values) -> switch (call) {
			case "next" -> position.incrementAndGet() <= 50;
			case "getString" -> query == 1 ? switch ((int) values[0]) {
			case 1 -> "exception\"" + position.get() + "  ";
			case 2 -> "'raw message'";
			default -> "comment's\nsecond line";
			} : query == 2 ? switch ((int) values[0]) {
			case 1 -> "filter" + position.get() + "  ";
			case 4 -> "entry'point  ";
			case 5 -> "module";
			default -> null;
			} : switch ((int) values[0]) {
			case 1 -> "collation\"" + position.get() + "  ";
			case 2 -> "UTF8  ";
			case 3 -> "external'base  ";
			case 5 -> "NUMERIC-SORT=1;CUSTOM='value'";
			default -> "collation's comment";
			};
			case "getShort" -> (short) -100;
			case "getInt" -> 7;
			case "close" -> null;
			default -> throw new AssertionError(call);
			});
			return proxy(PreparedStatement.class, (call, values) -> switch (call) {
			case "executeQuery" -> rows;
			case "close" -> null;
			default -> throw new AssertionError(call);
			});
		});
		var objects = new FirebirdSchemaObjectReader(DialectHolder.defaultDialect50).read(connection, true);
		assertEquals(3, queries.get());
		assertEquals(150, objects.size());
		assertEquals("CREATE EXCEPTION \"exception\"\"1\" '''raw message'''", objects.get(0).definition().get(0));
		assertTrue(objects.get(0).definition().get(1).contains("'comment''s\nsecond line'"));
		assertEquals(
				"DECLARE FILTER \"filter1\" INPUT_TYPE -100 OUTPUT_TYPE -100 ENTRY_POINT 'entry''point' MODULE_NAME 'module'",
				objects.get(50).definition().get(0));
		assertEquals(
				"CREATE COLLATION \"collation\"\"1\" FOR \"UTF8\" FROM EXTERNAL ('external''base') PAD SPACE CASE INSENSITIVE ACCENT INSENSITIVE 'NUMERIC-SORT=1;CUSTOM=''value'''",
				objects.get(100).definition().get(0));
		assertEquals("COMMENT ON COLLATION \"collation\"\"1\" IS 'collation''s comment'",
				objects.get(100).definition().get(1));
		assertEquals("DROP COLLATION \"collation\"\"1\"", objects.get(100).dropDefinition());
	}

	@Test
	void collationMasksAndAbsentOptionalMetadataArePreserved() throws Exception {
		for (int mask = 0; mask <= 7; mask++) {
			var object = new FirebirdSchemaObjectReader(DialectHolder.defaultDialect25).read(collationConnection(mask))
					.get(0);
			String sql = object.definition().get(0);
			assertEquals(1, object.definition().size());
			assertFalse(sql.contains("FROM"));
			assertTrue(sql.contains((mask & 1) == 0 ? " NO PAD" : " PAD SPACE"));
			assertTrue(sql.contains((mask & 2) == 0 ? " CASE SENSITIVE" : " CASE INSENSITIVE"));
			assertTrue(sql.contains((mask & 4) == 0 ? " ACCENT SENSITIVE" : " ACCENT INSENSITIVE"));
		}
		assertThrows(SQLException.class,
				() -> new FirebirdSchemaObjectReader(DialectHolder.defaultDialect50).read(collationConnection(8)));
	}

	private Connection collationConnection(int mask) {
		return proxy(Connection.class, (method, args) -> {
			if (!method.equals("prepareStatement"))
				throw new AssertionError(method);
			boolean collation = ((String) args[0]).contains("FROM RDB$COLLATIONS");
			if (collation)
				assertTrue(((String) args[0]).contains("WHERE COALESCE(C.RDB$SYSTEM_FLAG, 0) = 0"));
			var position = new AtomicInteger();
			var rows = proxy(ResultSet.class, (call, values) -> switch (call) {
			case "next" -> collation && position.incrementAndGet() == 1;
			case "getString" -> switch ((int) values[0]) {
			case 1 -> "C";
			case 2 -> "UTF8";
			default -> null;
			};
			case "getInt" -> mask;
			case "close" -> null;
			default -> throw new AssertionError(call);
			});
			return proxy(PreparedStatement.class, (call, values) -> switch (call) {
			case "executeQuery" -> rows;
			case "close" -> null;
			default -> throw new AssertionError(call);
			});
		});
	}

	@Test
	void legacyBoundaryAndCatalogFailuresAreExplicit() {
		assertThrows(UnsupportedOperationException.class,
				() -> new FirebirdSchemaObjectReader(DialectHolder.defaultDialect20));
		SQLException failure = new SQLException("catalog denied", "42000");
		Connection connection = proxy(Connection.class, (method, args) -> {
			throw failure;
		});
		assertSame(failure, assertThrows(SQLException.class,
				() -> new FirebirdSchemaObjectReader(DialectHolder.defaultDialect25).read(connection)));
	}

	@FunctionalInterface
	private interface Call {
		Object invoke(String method, Object[] args) throws Throwable;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Call call) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(object, method, args) -> call.invoke(method.getName(), args));
	}
}
