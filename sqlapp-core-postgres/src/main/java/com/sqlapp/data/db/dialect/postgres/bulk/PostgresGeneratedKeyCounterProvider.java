/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounterProvider;
import com.sqlapp.data.db.dialect.postgres.Postgres100;

/**
 * PostgreSQL 10+ identity and serial columns. The owned sequence is resolved
 * with {@code pg_get_serial_sequence} and restarted with a bound {@code setval},
 * so no identifier is interpolated from model values.
 */
public class PostgresGeneratedKeyCounterProvider implements GeneratedKeyCounterProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Postgres100;
	}

	@Override
	public boolean hasGenerator(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		return sequence(connection, dialect, table, column) != null;
	}

	@Override
	public BigInteger currentNext(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		final String sequence = sequence(connection, dialect, table, column);
		if (sequence == null) {
			return null;
		}
		// The name comes from the catalog function and is already quoted as needed.
		try (var statement = connection.prepareStatement("SELECT s.last_value, s.is_called, p.seqincrement FROM "
				+ sequence + " s, pg_catalog.pg_sequence p WHERE p.seqrelid = CAST(? AS regclass)")) {
			statement.setString(1, sequence);
			try (var rows = statement.executeQuery()) {
				if (!rows.next()) {
					return null;
				}
				final BigInteger last = rows.getBigDecimal(1).toBigIntegerExact();
				return rows.getBoolean(2) ? last.add(rows.getBigDecimal(3).toBigIntegerExact()) : last;
			}
		}
	}

	@Override
	public List<String> restart(final Connection connection, final Dialect dialect, final Table table,
			final Column column, final BigInteger next) throws SQLException {
		final String sequence = sequence(connection, dialect, table, column);
		final String sql = "SELECT setval(CAST(? AS regclass), ?, false)";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, sequence);
			// setval takes bigint; a numeric argument has no matching overload.
			statement.setLong(2, next.longValueExact());
			statement.executeQuery().close();
		}
		return List.of("SELECT setval('" + sequence.replace("'", "''") + "', " + next + ", false)");
	}

	private static String sequence(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		try (var statement = connection.prepareStatement("SELECT pg_get_serial_sequence(?, ?)")) {
			statement.setString(1, dialect.createSqlBuilder().name(table, true).toString());
			statement.setString(2, column.getName());
			try (var rows = statement.executeQuery()) {
				return rows.next() ? rows.getString(1) : null;
			}
		}
	}
}
