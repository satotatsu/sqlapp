/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.informix.Informix;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.AtomicTableReplacementProvider;

/**
 * Logged DELETE/INSERT replacement under transaction-scoped exclusive table
 * locks.
 */
public final class InformixAtomicTableReplacementProvider implements AtomicTableReplacementProvider {
	@Override
	public boolean supports(final Dialect dialect) {
		return dialect instanceof Informix;
	}

	@Override
	public void validate(final Connection connection) throws SQLException {
		if (!connection.getMetaData().supportsTransactions()
				|| !connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))
			throw new SQLException("Informix staged replacement requires SERIALIZABLE transactions");
		try (var query = connection.prepareStatement(
				"SELECT is_logging FROM sysmaster:\"informix\".sysdatabases WHERE name = DBINFO('dbname')");
				var rows = query.executeQuery()) {
			if (!rows.next() || rows.getInt(1) != 1 || rows.wasNull() || rows.next())
				throw new SQLException("Informix staged replacement requires a transaction-logged current database");
		}
	}

	@Override
	public List<String> lockSql(final Dialect dialect, final List<Table> tables) {
		// Informix permits at most 32 explicit table locks in one transaction.
		if (tables.size() > 32)
			throw new IllegalArgumentException(
					"Informix staged replacement supports at most 32 tables per transaction");
		validateIdentities(tables);
		if (!supports(dialect))
			throw new IllegalArgumentException("Informix replacement requires the Informix dialect");
		return tables.stream().map(table -> "LOCK TABLE " + identifier(table.getSchemaName()) + "."
				+ identifier(table.getName()) + " IN EXCLUSIVE MODE").sorted().toList();
	}

	@Override
	public void validateTables(final Connection connection, final List<Table> tables) throws SQLException {
		validateIdentities(tables);
		if (tables.isEmpty())
			return;
		// Resolve the actual database, rather than trusting a potentially stale JDBC
		// catalog label.
		final String database;
		try (var query = connection.prepareStatement("SELECT DBINFO('dbname') FROM TABLE(SET{1})");
				var rows = query.executeQuery()) {
			if (!rows.next())
				throw new SQLException("Cannot resolve the connected Informix database");
			database = rows.getString(1);
			if (database == null || database.isBlank() || rows.next())
				throw new SQLException("Cannot resolve the connected Informix database");
		}
		for (final var table : tables) {
			if (table.getCatalogName() != null && !table.getCatalogName().equals(database.stripTrailing()))
				throw new IllegalArgumentException(
						"Informix staged replacement tables must belong to the connected database");
		}
		final var selected = new HashSet<List<String>>();
		for (final var table : tables)
			selected.add(List.of(table.getSchemaName(), table.getName()));
		final String filter = String.join(" OR ",
				java.util.Collections.nCopies(tables.size(), "(t.owner = ? AND t.tabname = ?)"));
		// Only ROWID is a harmless flag. Reject RAW, replication, UNDER, external,
		// audit and unknown flags rather than guessing transaction/side-effect
		// semantics.
		final String sql = """
				SELECT TRIM(t.owner), t.tabname FROM "informix".systables t
				WHERE t.tabtype = 'T' AND t.tabid >= 100 AND t.type_xid = 0
				  AND t.flags IN (0, 1)
				  AND NOT EXISTS (SELECT 1 FROM "informix".systriggers tr WHERE tr.tabid = t.tabid)
				  AND NOT EXISTS (SELECT 1 FROM "informix".sysinherits h WHERE h.class = 'T'
				    AND (h.child = t.tabid OR h.parent = t.tabid))
				  AND (
				""" + filter + ")";
		try (var query = connection.prepareStatement(sql)) {
			int index = 1;
			for (final var table : tables) {
				query.setString(index++, table.getSchemaName());
				query.setString(index++, table.getName());
			}
			try (var rows = query.executeQuery()) {
				while (rows.next())
					selected.remove(List.of(rows.getString(1), rows.getString(2)));
			}
		}
		if (!selected.isEmpty())
			throw new IllegalArgumentException("Informix staged replacement requires existing, "
					+ "ordinary logged tables without user triggers, typed-table inheritance, replication, audit or unknown flags: "
					+ selected);
	}

	private void validateIdentities(final List<Table> tables) {
		final var identities = new HashSet<List<String>>();
		for (final var table : tables) {
			if (table.getSchemaName() == null || table.getSchemaName().isBlank() || table.getName() == null
					|| table.getName().isBlank())
				throw new IllegalArgumentException(
						"Informix staged replacement requires resolved table owner and name");
			if (!identities.add(List.of(table.getSchemaName(), table.getName())))
				throw new IllegalArgumentException("Duplicate Informix replacement table: " + table.getName());
		}
	}

	private String identifier(final String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
