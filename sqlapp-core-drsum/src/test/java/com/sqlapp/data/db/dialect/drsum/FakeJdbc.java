/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

/**
 * Minimal in-memory JDBC used to test the Dr.Sum readers without a server. The
 * answer for each query is chosen by the system table its SQL references.
 */
final class FakeJdbc {

	private FakeJdbc() {
	}

	static Connection connection(final AtomicInteger calls, final List<String> sqls,
			final Function<String, List<Map<String, Object>>> answer) {
		return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
		case "getMetaData" -> metadata("Dr.Sum", "5.7");
		case "prepareStatement" -> statement((String) a[0], calls, sqls, answer);
		default -> fallback(m.getReturnType());
		});
	}

	private static PreparedStatement statement(final String sql, final AtomicInteger calls, final List<String> sqls,
			final Function<String, List<Map<String, Object>>> answer) {
		return proxy(PreparedStatement.class, (p, m, a) -> {
			if (m.getName().equals("executeQuery")) {
				calls.incrementAndGet();
				sqls.add(sql);
				return rows(answer.apply(sql));
			}
			return fallback(m.getReturnType());
		});
	}

	static DatabaseMetaData metadata(final String product, final String version) {
		return proxy(DatabaseMetaData.class, (p, m, a) -> switch (m.getName()) {
		case "getDatabaseProductName" -> product;
		case "getDatabaseProductVersion" -> version;
		case "getDatabaseMajorVersion" -> 5;
		case "getDatabaseMinorVersion" -> 7;
		default -> fallback(m.getReturnType());
		});
	}

	static ResultSet rows(final List<Map<String, Object>> values) throws Exception {
		final CachedRowSet rs = RowSetProvider.newFactory().createCachedRowSet();
		final RowSetMetaDataImpl md = new RowSetMetaDataImpl();
		final List<String> names = new ArrayList<>(
				values.isEmpty() ? List.of("EMPTY") : values.get(0).keySet());
		md.setColumnCount(names.size());
		for (int i = 0; i < names.size(); i++) {
			md.setColumnName(i + 1, names.get(i));
			md.setColumnLabel(i + 1, names.get(i));
			md.setColumnType(i + 1, Types.JAVA_OBJECT);
		}
		rs.setMetaData(md);
		for (int n = values.size() - 1; n >= 0; n--) {
			rs.moveToInsertRow();
			for (int i = 0; i < names.size(); i++) {
				rs.updateObject(i + 1, values.get(n).get(names.get(i)));
			}
			rs.insertRow();
			rs.moveToCurrentRow();
		}
		rs.beforeFirst();
		return rs;
	}

	static Map<String, Object> row(final Object... pairs) {
		final Map<String, Object> row = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			row.put((String) pairs[i], pairs[i + 1]);
		}
		return row;
	}

	static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
	}

	static Object fallback(final Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		return null;
	}
}
