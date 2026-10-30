/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

final class SybaseReaderTestSupport {
	static ExResultSet row(Map<String, Object> values) {
		final boolean[] wasNull = { false };
		return new ExResultSet((ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
				new Class<?>[] { ResultSet.class }, (p, m, args) -> {
					if (m.getName().equals("wasNull"))
						return wasNull[0];
					final Object value = values.get(args[0]);
					wasNull[0] = value == null;
					return switch (m.getName()) {
					case "getString" -> value == null ? null : value.toString();
					case "getInt" -> value == null ? 0 : ((Number) value).intValue();
					case "getLong" -> value == null ? 0L : ((Number) value).longValue();
					case "getBoolean" ->
						value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0;
					case "getTimestamp", "getObject" -> value;
					default -> throw new UnsupportedOperationException(m.getName());
					};
				})) {
			@Override
			public boolean contains(String name) {
				return values.containsKey(name);
			}
		};
	}

	static void feed(ResultSetNextHandler handler, List<Map<String, Object>> rows) {
		try {
			for (var values : rows)
				handler.handleResultSetNext(row(values));
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}
}
