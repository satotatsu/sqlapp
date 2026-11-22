/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2005;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounterProvider;

/**
 * SQL Server 2005+ identity columns, reseeded with {@code DBCC CHECKIDENT}.
 * RESEED sets the current value: the next value is the reseed value itself for
 * a table that never held rows, and the reseed value plus the increment
 * otherwise.
 */
public class SqlServerGeneratedKeyCounterProvider implements GeneratedKeyCounterProvider {
	private static final String QUERY = "SELECT CAST(last_value AS DECIMAL(38)), CAST(seed_value AS DECIMAL(38)),"
			+ " CAST(increment_value AS DECIMAL(38)) FROM sys.identity_columns WHERE object_id = OBJECT_ID(?)"
			+ " AND name = ?";

	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof SqlServer2005;
	}

	@Override
	public boolean hasGenerator(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		return identity(connection, dialect, table, column) != null;
	}

	@Override
	public BigInteger currentNext(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		final BigDecimal[] identity = identity(connection, dialect, table, column);
		if (identity == null) {
			return null;
		}
		// last_value is NULL until the first row was inserted; the seed is next then.
		return identity[0] == null ? identity[1].toBigIntegerExact()
				: identity[0].add(identity[2]).toBigIntegerExact();
	}

	@Override
	public List<String> restart(final Connection connection, final Dialect dialect, final Table table,
			final Column column, final BigInteger next) throws SQLException {
		final BigDecimal[] identity = identity(connection, dialect, table, column);
		final BigInteger reseed = identity[0] == null ? next : next.subtract(identity[2].toBigIntegerExact());
		final String name = qualified(dialect, table).replace("'", "''");
		final String sql = "DBCC CHECKIDENT ('" + name + "', RESEED, " + reseed + ") WITH NO_INFOMSGS";
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
		return List.of(sql);
	}

	private static BigDecimal[] identity(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		try (var statement = connection.prepareStatement(QUERY)) {
			statement.setString(1, qualified(dialect, table));
			statement.setString(2, column.getName());
			try (var rows = statement.executeQuery()) {
				return rows.next() ? new BigDecimal[] { rows.getBigDecimal(1), rows.getBigDecimal(2),
						rows.getBigDecimal(3) } : null;
			}
		}
	}

	private static String qualified(final Dialect dialect, final Table table) {
		final var builder = dialect.createSqlBuilder();
		if (table.getSchemaName() != null) {
			builder.name(table.getSchemaName())._add(".");
		}
		return builder.name(table.getName()).toString();
	}
}
