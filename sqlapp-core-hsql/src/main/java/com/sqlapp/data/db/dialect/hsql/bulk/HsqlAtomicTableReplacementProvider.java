/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import java.sql.*;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.hsql.Hsql2_0_0;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.AtomicTableReplacementProvider;

/**
 * HSQLDB 2.x locking transactions; MVCC's LOCK TABLE is not an exclusion lock.
 */
public final class HsqlAtomicTableReplacementProvider implements AtomicTableReplacementProvider {
	@Override
	public void validateTables(Connection connection, List<Table> tables) throws SQLException {
		if (tables.isEmpty())
			return;
		var selected = new java.util.HashSet<List<String>>();
		for (var table : tables)
			selected.add(List.of(table.getSchemaName(), table.getName()));
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery(
						"SELECT EVENT_OBJECT_SCHEMA, EVENT_OBJECT_TABLE FROM INFORMATION_SCHEMA.TRIGGERS")) {
			while (rows.next()) {
				if (selected.contains(List.of(rows.getString(1), rows.getString(2))))
					throw new IllegalArgumentException(
							"HSQLDB staged replacement requires tables without user triggers.");
			}
		}
	}

	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof Hsql2_0_0;
	}

	@Override
	public void validate(Connection connection) throws SQLException {
		if (!connection.getMetaData().supportsTransactions()
				|| !connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))
			throw new SQLException("HSQLDB staged replacement requires SERIALIZABLE transactions.");
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery(
						"SELECT PROPERTY_VALUE FROM INFORMATION_SCHEMA.SYSTEM_PROPERTIES WHERE PROPERTY_NAME = 'hsqldb.tx'")) {
			if (!rows.next() || !java.util.Set.of("LOCKS", "MVLOCKS")
					.contains(rows.getString(1).toUpperCase(java.util.Locale.ROOT)))
				throw new IllegalArgumentException(
						"HSQLDB staged replacement requires LOCKS or MVLOCKS transaction control. MVCC does not honor explicit table exclusion locks.");
		}
	}

	@Override
	public List<String> lockSql(Dialect dialect, List<Table> tables) {
		var names = tables.stream()
				.map(table -> dialect.getObjectFullName(null, table.getSchemaName(), table.getName()) + " WRITE")
				.sorted().toList();
		return names.isEmpty() ? List.of() : List.of("LOCK TABLE " + String.join(", ", names));
	}
}
