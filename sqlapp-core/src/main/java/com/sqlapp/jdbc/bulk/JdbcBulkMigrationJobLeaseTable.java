/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

/** Shared Schema model and row mapping for the JDBC job-lease table. */
final class JdbcBulkMigrationJobLeaseTable {
	private JdbcBulkMigrationJobLeaseTable() {
	}

	static Table table(final String tableName) {
		final Table table = new Table(tableName);
		final Column fingerprint = varchar("PLAN_FINGERPRINT");
		table.getColumns().add(fingerprint);
		table.getColumns().add(varchar("OWNER_ID"));
		table.getColumns().add(new Column("EXPIRES_AT").setDataType(DataType.VARCHAR)
				.setLength(40).setNotNull(true));
		table.setPrimaryKey((String) null, fingerprint);
		return table;
	}

	static ParametersContext parameters(final String fingerprint) {
		final ParametersContext parameters = new ParametersContext();
		parameters.put("PLAN_FINGERPRINT", fingerprint);
		return parameters;
	}

	static ParametersContext parameters(final BulkMigrationJobLease lease) {
		final ParametersContext parameters = parameters(lease.planFingerprint());
		parameters.put("OWNER_ID", lease.ownerId());
		parameters.put("EXPIRES_AT", lease.expiresAt().toString());
		return parameters;
	}

	static BulkMigrationJobLease lease(final ExResultSet resultSet,
			final String fingerprint) throws SQLException {
		final Map<String, Integer> columns = new LinkedHashMap<>();
		final var metadata = resultSet.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++) {
			columns.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
		}
		return new BulkMigrationJobLease(fingerprint,
				resultSet.getString(columns.get("OWNER_ID")),
				Instant.parse(resultSet.getString(columns.get("EXPIRES_AT"))));
	}

	static void validateFingerprint(final String fingerprint) {
		new BulkMigrationJobLease(fingerprint, "validation", Instant.MAX);
	}

	private static Column varchar(final String name) {
		return new Column(name).setDataType(DataType.VARCHAR)
				.setLength(BulkMigrationJobLease.ID_MAX_LENGTH).setNotNull(true);
	}
}
