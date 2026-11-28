package com.sqlapp.data.db.dialect.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;

class MySqlDatetimeBindingTest {
	@Test
	void readsDatetimeAsLocalFieldsByIndexAndLabel() throws Exception {
		var value = LocalDateTime.parse("2026-11-18T12:34:56.654321");
		var handler = DialectResolver.getInstance().getDialect("mysql", 8, 4, 0).getDbDataTypes()
				.getDbType(DataType.TIMESTAMP).getJdbcTypeHandler();
		var rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
				(proxy, method, args) -> {
					assertEquals("getObject", method.getName());
					assertEquals(LocalDateTime.class, args[1]);
					return args[0].equals("missing") ? null : value;
				});
		assertEquals(value, ((Timestamp) handler.getObject(rows, 1)).toLocalDateTime());
		assertEquals(value, ((Timestamp) handler.getObject(rows, "dt")).toLocalDateTime());
		org.junit.jupiter.api.Assertions.assertNull(handler.getObject(rows, "missing"));
	}

	@Test
	void bindsDatetimeAsWallClockText() throws Exception {
		for (int[] version : new int[][] { { 5, 5 }, { 5, 7 }, { 8, 4 }, { 9, 0 } }) {
			var dialect = DialectResolver.getInstance().getDialect("mysql", version[0], version[1], 0);
			var handler = dialect.getDbDataTypes().getDbType(DataType.TIMESTAMP).getJdbcTypeHandler();
			var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
						assertEquals("setString", method.getName());
						assertEquals(1, args[0]);
						assertEquals("2026-11-18 12:34:56.654321", args[1]);
						return null;
					});
			handler.setObject(statement, 1, Timestamp.valueOf("2026-11-18 12:34:56.654321"));
		}
	}
}
