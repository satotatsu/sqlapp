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
}
