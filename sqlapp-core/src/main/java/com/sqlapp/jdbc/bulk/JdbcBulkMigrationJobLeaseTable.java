/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

/** Shared Schema model and row mapping for the JDBC job-lease table. */
final class JdbcBulkMigrationJobLeaseTable {
	private static final Set<String> REQUIRED_COLUMNS = Set.of("PLAN_FINGERPRINT",
			"OWNER_ID", "EXPIRES_AT");

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
		if (!columns.keySet().containsAll(REQUIRED_COLUMNS)) {
			throw new SQLException("Migration job lease result is missing required columns");
		}
		final String owner = resultSet.getString(columns.get("OWNER_ID"));
		final String expires = resultSet.getString(columns.get("EXPIRES_AT"));
		try {
			if (expires == null) {
				throw new IllegalArgumentException("EXPIRES_AT must not be null");
			}
			return new BulkMigrationJobLease(fingerprint, owner, Instant.parse(expires));
		} catch (IllegalArgumentException | DateTimeException failure) {
			throw new SQLException("Invalid migration job lease data for plan "
					+ fingerprint + "; check OWNER_ID and EXPIRES_AT", failure);
		}
	}

	static void validateFingerprint(final String fingerprint) {
		new BulkMigrationJobLease(fingerprint, "validation", Instant.MAX);
	}

	static boolean exists(final Connection connection, final String tableName)
			throws SQLException {
		final String schema = currentSchema(connection);
		try (ResultSet tables = connection.getMetaData().getTables(
				connection.getCatalog(), null, "%", new String[] { "TABLE" })) {
			while (tables.next()) {
				if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))
						&& matchesSchema(schema, tables.getString("TABLE_SCHEM"))) {
					return true;
				}
			}
		}
		return false;
	}

	static void validateStructure(final Connection connection,
			final String tableName) throws SQLException {
		final String schema = currentSchema(connection);
		final Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ResultSet result = connection.getMetaData().getColumns(
				connection.getCatalog(), null, "%", "%")) {
			while (result.next()) {
				if (tableName.equalsIgnoreCase(result.getString("TABLE_NAME"))
						&& matchesSchema(schema, result.getString("TABLE_SCHEM"))) {
					columns.add(result.getString("COLUMN_NAME"));
				}
			}
		}
		if (!columns.containsAll(REQUIRED_COLUMNS)) {
			final Set<String> missing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			missing.addAll(REQUIRED_COLUMNS);
			missing.removeAll(columns);
			throw new SQLException("Migration job lease table " + tableName
					+ " is missing required columns: " + missing);
		}
	}

	private static Column varchar(final String name) {
		return new Column(name).setDataType(DataType.VARCHAR)
				.setLength(BulkMigrationJobLease.ID_MAX_LENGTH).setNotNull(true);
	}

	private static String currentSchema(final Connection connection) throws SQLException {
		return connection.getMetaData().supportsSchemasInTableDefinitions()
				? connection.getSchema() : null;
	}

	private static boolean matchesSchema(final String current, final String actual) {
		return current == null || current.equals(actual);
	}
}
