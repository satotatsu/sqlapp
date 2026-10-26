/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.*;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.*;

class FirebirdSchemaDependencyReaderTest {
	@Test
	void bulkQueriesNormalizeOwnersAndScopePackagedProviders() throws Exception {
		var data = List.<Object[]>of(new Object[] { "RDB$FIELD", 3, "F", 15, "T", null, null, null, null },
				new Object[] { "IX", 6, "F", 15, null, "T", null, null, null },
				new Object[] { "CHECK_TRIGGER", 2, "F", 15, null, null, "T", 3, null },
				new Object[] { "BODY", 19, "MEMBER", 15, null, null, null, null, "PROVIDER" },
				new Object[] { "CALLER", 5, "D", 9, null, null, null, null, null },
				new Object[] { "RDB$VIEW_EXPR", 3, "F", 15, "V", null, null, null, null, 1 });
		var queries = new AtomicInteger();
		var result = new FirebirdSchemaDependencyReader(DialectHolder.defaultDialect30)
				.read(connection(data, queries, true));
		assertEquals(2, queries.get());
		assertEquals(6, result.size());
		assertEquals(new Dependency(new ObjectId(Kind.TABLE, "T"), new ObjectId(Kind.FUNCTION, "F")), result.get(0));
		assertEquals(new ObjectId(Kind.PACKAGE, "PROVIDER"), result.get(1).prerequisite());
		assertEquals(new ObjectId(Kind.PACKAGE_BODY, "BODY"), result.get(1).dependent());
		assertEquals(new ObjectId(Kind.DOMAIN, "D"), result.get(2).prerequisite());
		assertEquals(new ObjectId(Kind.VIEW, "V"), result.get(3).dependent());
		assertEquals(new Dependency(new ObjectId(Kind.PACKAGE, "BODY"), new ObjectId(Kind.TABLE, "T")), result.get(4));
		assertEquals(new ObjectId(Kind.DOMAIN, "D"), result.get(5).prerequisite());
	}

	@Test
	void legacyQueryAvoidsPackageColumnAndBulkSizeDoesNotChangeQueryCount() throws Exception {
		var data = new ArrayList<Object[]>();
		for (int i = 0; i < 100; i++)
			data.add(new Object[] { "P" + i, 5, "T", 0, null, null, null, null, null });
		var queries = new AtomicInteger();
		assertEquals(100, new FirebirdSchemaDependencyReader(DialectHolder.defaultDialect25)
				.read(connection(data, queries, false)).size());
		assertEquals(1, queries.get());
		assertThrows(UnsupportedOperationException.class,
				() -> new FirebirdSchemaDependencyReader(DialectHolder.defaultDialect20));
	}

	private Connection connection(List<Object[]> data, AtomicInteger queries, boolean packages) {
		return proxy(Connection.class, (method, args) -> {
			if (!method.equals("prepareStatement"))
				throw new AssertionError(method);
			boolean signature = ((String) args[0]).contains("FROM RDB$FUNCTION_ARGUMENTS A");
			if (!signature)
				assertEquals(packages, ((String) args[0]).contains("D.RDB$PACKAGE_NAME"));
			else
				assertTrue(packages);
			List<Object[]> selected = signature
					? List.<Object[]>of(new Object[] { "BODY", "T", null, 0 }, new Object[] { "BODY", null, "D", 0 })
					: data;
			queries.incrementAndGet();
			var position = new AtomicInteger(-1);
			var rows = proxy(ResultSet.class, (call, values) -> switch (call) {
			case "next" -> position.incrementAndGet() < selected.size();
			case "getString" -> {
				Object value = selected.get(position.get())[(int) values[0] - 1];
				yield value == null ? null : value + "  ";
			}
			case "getInt" -> {
				Object[] row = selected.get(position.get());
				int index = (int) values[0] - 1;
				Object value = index >= row.length ? null : row[index];
				yield value == null ? 0 : (int) value;
			}
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
