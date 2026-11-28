/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounterProvider;
import com.sqlapp.data.db.dialect.oracle.Oracle12c;

/**
 * Oracle 12c+ identity columns. {@code LAST_NUMBER} is a cache allocation
 * boundary at or above the next value, so a restart is only issued beyond it.
 */
public class OracleGeneratedKeyCounterProvider implements GeneratedKeyCounterProvider {
	private static final String QUERY = "SELECT s.LAST_NUMBER, i.GENERATION_TYPE FROM ALL_TAB_IDENTITY_COLS i"
			+ " JOIN ALL_SEQUENCES s ON s.SEQUENCE_OWNER = i.OWNER AND s.SEQUENCE_NAME = i.SEQUENCE_NAME"
			+ " WHERE i.OWNER = COALESCE(?, SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'))"
			+ " AND i.TABLE_NAME = ? AND i.COLUMN_NAME = ?";

	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Oracle12c;
	}

	@Override
	public boolean hasGenerator(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		return identity(connection, table, column) != null;
	}

	@Override
	public BigInteger currentNext(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		final String[] identity = identity(connection, table, column);
		return identity == null || identity[0] == null ? null : new BigInteger(identity[0]);
	}

	@Override
	public List<String> restart(final Connection connection, final Dialect dialect, final Table table,
			final Column column, final BigInteger next) throws SQLException {
		final String[] identity = identity(connection, table, column);
		// The generation clause must be restated when identity options change.
		final String sql = dialect.createSqlBuilder()._add("ALTER TABLE ").name(table, true)._add(" MODIFY (")
				.name(column)._add(" GENERATED ")._add(identity[1])._add(" AS IDENTITY (START WITH ")._add(next)
				._add("))").toString();
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
		return List.of(sql);
	}

	private static String[] identity(final Connection connection, final Table table, final Column column)
			throws SQLException {
		try (var statement = connection.prepareStatement(QUERY)) {
			statement.setString(1, table.getSchemaName());
			statement.setString(2, table.getName());
			statement.setString(3, column.getName());
			try (var rows = statement.executeQuery()) {
				return rows.next() ? new String[] { rows.getString(1), rows.getString(2) } : null;
			}
		}
	}
}
