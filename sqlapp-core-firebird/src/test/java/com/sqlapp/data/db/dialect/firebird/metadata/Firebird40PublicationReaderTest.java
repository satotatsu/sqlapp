/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.*;

class Firebird40PublicationReaderTest {
	@Test
	void readsOneJoinedQueryForAllMembers() throws Exception {
		var queries = new AtomicInteger();
		var result = new Firebird40PublicationReader(DialectHolder.defaultDialect40)
				.read(connection(100, 1, "RDB$DEFAULT", queries));
		assertEquals(1, queries.get());
		assertEquals(100, result.tables().size());
		assertTrue(result.active());
		assertTrue(result.autoEnable());
		assertTrue(result.tables().contains("table100"));
	}

	@Test
	void emptyMembershipAndInvalidCatalogStatesAreDistinct() throws Exception {
		var reader = new Firebird40PublicationReader(DialectHolder.defaultDialect50);
		assertTrue(reader.read(connection(1, 0, "RDB$DEFAULT", new AtomicInteger(), true)).tables().isEmpty());
		assertThrows(SQLException.class, () -> reader.read(connection(0, 0, "RDB$DEFAULT", new AtomicInteger())));
		assertThrows(SQLException.class, () -> reader.read(connection(1, 2, "RDB$DEFAULT", new AtomicInteger())));
		assertThrows(SQLException.class,
				() -> reader.read(connection(1, 0, "RDB$DEFAULT", new AtomicInteger(), true, true)));
		assertThrows(SQLException.class,
				() -> reader.read(connection(1, 0, "future publication", new AtomicInteger())));
		assertThrows(UnsupportedOperationException.class,
				() -> new Firebird40PublicationReader(DialectHolder.defaultDialect30));
	}

	private Connection connection(int count, int flag, String publication, AtomicInteger queries, boolean... empty) {
		return proxy(Connection.class, (method, args) -> {
			if (!method.equals("prepareStatement"))
				throw new AssertionError(method);
			queries.incrementAndGet();
			assertTrue(((String) args[0]).contains("LEFT JOIN RDB$PUBLICATION_TABLES"));
			var position = new AtomicInteger();
			var rows = proxy(ResultSet.class, (call, values) -> switch (call) {
			case "next" -> position.incrementAndGet() <= count;
			case "getString" ->
				(int) values[0] == 1 ? publication + "  " : empty.length > 0 ? null : "table" + position.get() + "  ";
			case "getInt" -> flag;
			case "wasNull" -> empty.length > 1 && empty[1];
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
