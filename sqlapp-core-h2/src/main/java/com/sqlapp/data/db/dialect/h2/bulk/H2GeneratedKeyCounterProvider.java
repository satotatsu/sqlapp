/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounterProvider;
import com.sqlapp.data.db.dialect.h2.H2_200;

/** H2 2.x identity columns. */
public class H2GeneratedKeyCounterProvider implements GeneratedKeyCounterProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof H2_200;
	}

	@Override
	public boolean hasGenerator(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		return query(connection, table, column, "IS_IDENTITY") != null
				&& "YES".equals(query(connection, table, column, "IS_IDENTITY"));
	}

	@Override
	public BigInteger currentNext(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		final String base = query(connection, table, column, "IDENTITY_BASE");
		return base == null ? null : new BigInteger(base);
	}

	@Override
	public List<String> restart(final Connection connection, final Dialect dialect, final Table table,
			final Column column, final BigInteger next) throws SQLException {
		final String sql = dialect.createSqlBuilder()._add("ALTER TABLE ").name(table, true)._add(" ALTER COLUMN ")
				.name(column)._add(" RESTART WITH ")._add(next).toString();
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
		return List.of(sql);
	}

	private static String query(final Connection connection, final Table table, final Column column,
			final String field) throws SQLException {
		try (var statement = connection.prepareStatement("SELECT CAST(" + field
				+ " AS VARCHAR) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = COALESCE(?, CURRENT_SCHEMA)"
				+ " AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
			statement.setString(1, table.getSchemaName());
			statement.setString(2, table.getName());
			statement.setString(3, column.getName());
			try (var rows = statement.executeQuery()) {
				return rows.next() ? rows.getString(1) : null;
			}
		}
	}
}
