/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.bulk;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounterProvider;
import com.sqlapp.data.db.dialect.db2.Db2;
import com.sqlapp.data.db.dialect.db2.common.AbstractDb2HostDialect;

/**
 * Db2 for LUW identity columns. Db2 never restarts a generator automatically,
 * so this provider acts only when the caller requested it explicitly.
 * <p>
 * {@code NEXTCACHEFIRSTVALUE} reflects {@code RESTART WITH} only after the next
 * value was generated. Until then a repeated synchronization with a smaller
 * source value can move an unused restart point back; it never goes below the
 * imported keys.
 */
public class Db2GeneratedKeyCounterProvider implements GeneratedKeyCounterProvider {
	private static final String QUERY = "SELECT NEXTCACHEFIRSTVALUE FROM SYSCAT.COLIDENTATTRIBUTES"
			+ " WHERE TABSCHEMA = COALESCE(CAST(? AS VARCHAR(128)), CURRENT SCHEMA) AND TABNAME = ? AND COLNAME = ?";

	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Db2 && !(dialect instanceof AbstractDb2HostDialect);
	}

	@Override
	public boolean requiresExplicitRequest() {
		return true;
	}

	@Override
	public boolean hasGenerator(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		try (var statement = prepare(connection, table, column); var rows = statement.executeQuery()) {
			return rows.next();
		}
	}

	@Override
	public BigInteger currentNext(final Connection connection, final Dialect dialect, final Table table,
			final Column column) throws SQLException {
		try (var statement = prepare(connection, table, column); var rows = statement.executeQuery()) {
			if (!rows.next() || rows.getBigDecimal(1) == null) {
				return null;
			}
			return rows.getBigDecimal(1).toBigIntegerExact();
		}
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

	private static java.sql.PreparedStatement prepare(final Connection connection, final Table table,
			final Column column) throws SQLException {
		final var statement = connection.prepareStatement(QUERY);
		statement.setString(1, table.getSchemaName());
		statement.setString(2, table.getName());
		statement.setString(3, column.getName());
		return statement;
	}
}
