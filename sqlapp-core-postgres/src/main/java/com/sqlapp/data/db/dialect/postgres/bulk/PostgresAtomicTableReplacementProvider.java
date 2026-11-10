/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import java.sql.*;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.Postgres;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.AtomicTableReplacementProvider;

/** PostgreSQL transactional DELETE and table locks, including empty tables. */
public final class PostgresAtomicTableReplacementProvider implements AtomicTableReplacementProvider {
	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof Postgres;
	}

	@Override
	public void validate(Connection connection) throws SQLException {
		if (!connection.getMetaData().supportsTransactions()
				|| !connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))
			throw new SQLException("PostgreSQL staged replacement requires SERIALIZABLE transactions.");
	}

	@Override
	public List<String> lockSql(Dialect dialect, List<Table> tables) {
		var names = tables.stream()
				.map(table -> dialect.getObjectFullName(null, table.getSchemaName(), table.getName())).sorted()
				.toList();
		return names.isEmpty() ? List.of() : List.of("LOCK TABLE " + String.join(", ", names) + " IN EXCLUSIVE MODE");
	}

	@Override
	public void validateTables(Connection connection, List<Table> tables) throws SQLException {
		if (tables.isEmpty())
			return;
		var metadata = connection.getMetaData();
		int major = metadata.getDatabaseMajorVersion();
		boolean hasRowSecurity = major >= 10 || major == 9 && metadata.getDatabaseMinorVersion() >= 5;
		var selected = new java.util.HashSet<List<String>>();
		for (var table : tables)
			selected.add(List.of(table.getSchemaName(), table.getName()));
		try (var query = connection.prepareStatement(
				"""
						SELECT n.nspname, c.relname, c.relkind = 'r'
						  AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits i WHERE i.inhparent = c.oid OR i.inhrelid = c.oid)
						  AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_trigger t WHERE t.tgrelid = c.oid AND NOT t.tgisinternal)
						  AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_rewrite r WHERE r.ev_class = c.oid)
						"""
						+ (hasRowSecurity ? " AND NOT c.relrowsecurity\n" : "") + """
								FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
								WHERE n.nspname = ?
								""")) {
			for (String schema : tables.stream().map(Table::getSchemaName).distinct().toList()) {
				query.setString(1, schema);
				try (var rows = query.executeQuery()) {
					while (rows.next()) {
						if (selected.remove(List.of(rows.getString(1), rows.getString(2))) && !rows.getBoolean(3))
							throw new IllegalArgumentException(
									"PostgreSQL staged replacement requires ordinary standalone tables without row-level security, user triggers or rewrite rules.");
					}
				}
			}
		}
		if (!selected.isEmpty())
			throw new IllegalArgumentException("PostgreSQL replacement table metadata changed. Review again.");
	}
}
