/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** JDBC checkpoint reader that never creates, upgrades, updates, or deletes tables. */
final class ReadOnlyJdbcBulkMigrationCheckpointStore
		implements BulkMigrationCheckpointStore {
	static final Set<String> REQUIRED_COLUMNS = Set.of("MIGRATION_ID",
			"SOURCE_FINGERPRINT", "TARGET_FINGERPRINT", "PROCESSED_ROWS",
			"COMPLETED_CHUNKS", "CHUNK_SIZE", "LAST_CHUNK_HASH", "RESUME_TOKEN",
			"COMPLETE_FLAG");
	private final Connection connection;
	private final String rawTableName;
	private final SqlNode selectNode;

	ReadOnlyJdbcBulkMigrationCheckpointStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid checkpoint table name: " + tableName);
		}
		this.rawTableName = tableName;
		this.selectNode = DialectResolver.getInstance().getDialect(connection)
				.createSqlFactoryRegistry().createSqlNodes(
						JdbcBulkMigrationCheckpointStore.readTable(tableName), SqlType.SELECT)
				.get(0);
	}

	@Override
	public Optional<BulkMigrationCheckpoint> load(final String migrationId)
			throws SQLException {
		BulkMigrationCheckpoint.validateMigrationId(migrationId);
		final var identity = resolveTable();
		if (identity.isEmpty()) {
			return Optional.empty();
		}
		validateStructure(identity.orElseThrow());
		final BulkMigrationCheckpoint[] result = new BulkMigrationCheckpoint[1];
		new JdbcHandler(selectNode,
				rs -> result[0] = checkpoint(rs, migrationId)).execute(connection,
					parameters(migrationId));
		return Optional.ofNullable(result[0]);
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) {
		throw new UnsupportedOperationException("Read-only checkpoint store");
	}

	@Override
	public void delete(final String migrationId) {
		throw new UnsupportedOperationException("Read-only checkpoint store");
	}

	Optional<TableIdentity> resolveTable() throws SQLException {
		final String schema = currentSchema();
		final Set<TableIdentity> candidates = new HashSet<>();
		try (ResultSet tables = connection.getMetaData().getTables(
				connection.getCatalog(), schemaPattern(schema), "%",
				new String[] { "TABLE" })) {
			while (tables.next()) {
				if (rawTableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))
						&& (schema == null || schema.equals(tables.getString("TABLE_SCHEM")))) {
					candidates.add(new TableIdentity(tables.getString("TABLE_CAT"),
							tables.getString("TABLE_SCHEM"), tables.getString("TABLE_NAME")));
				}
			}
		}
		if (candidates.size() > 1) {
			throw new SQLException("Ambiguous migration checkpoint table " + rawTableName
					+ "; multiple catalog, schema or case-sensitive table matches exist");
		}
		return candidates.stream().findFirst();
	}

	void validateStructure(final TableIdentity identity) throws SQLException {
		validateStructure(identity, Set.of());
	}

	void validateStructure(final TableIdentity identity,
			final Set<String> permittedMissingColumns) throws SQLException {
		final Set<String> columns = columns(identity);
		final Set<String> required = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		required.addAll(REQUIRED_COLUMNS);
		required.removeAll(permittedMissingColumns);
		if (!columns.containsAll(required)) {
			final Set<String> missing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			missing.addAll(required);
			missing.removeAll(columns);
			throw new SQLException("Migration checkpoint table " + rawTableName
					+ " is missing required columns: " + missing);
		}
		validatePrimaryKey(identity);
	}

	Set<String> columns(final TableIdentity identity) throws SQLException {
		final Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ResultSet result = connection.getMetaData().getColumns(identity.catalog(),
				identity.schema(), identity.name(), "%")) {
			while (result.next()) {
				columns.add(result.getString("COLUMN_NAME"));
			}
		}
		return columns;
	}

	private void validatePrimaryKey(final TableIdentity identity)
			throws SQLException {
		final Set<String> primaryKey = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ResultSet keys = connection.getMetaData().getPrimaryKeys(identity.catalog(),
				identity.schema(), identity.name())) {
			while (keys.next()) {
				primaryKey.add(keys.getString("COLUMN_NAME"));
			}
		}
		if (primaryKey.size() != 1 || !primaryKey.contains("MIGRATION_ID")) {
			throw new SQLException("Migration checkpoint table " + rawTableName
					+ " must have a primary key on MIGRATION_ID alone");
		}
	}

	private String currentSchema() throws SQLException {
		try {
			final String schema = connection.getMetaData().supportsSchemasInTableDefinitions()
					? connection.getSchema() : null;
			return schema == null || schema.isEmpty() ? null : schema;
		} catch (SQLFeatureNotSupportedException | AbstractMethodError unsupported) {
			return null;
		}
	}

	private String schemaPattern(final String schema) throws SQLException {
		if (schema == null) {
			return null;
		}
		final String escape = connection.getMetaData().getSearchStringEscape();
		if (escape == null || escape.isEmpty()) {
			return schema.contains("_") || schema.contains("%") ? null : schema;
		}
		return schema.replace(escape, escape + escape)
				.replace("_", escape + "_").replace("%", escape + "%");
	}

	private static BulkMigrationCheckpoint checkpoint(final ExResultSet resultSet,
			final String migrationId) throws SQLException {
		final Map<String, Integer> columns = new LinkedHashMap<>();
		final var metadata = resultSet.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++) {
			columns.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
		}
		if (!columns.keySet().containsAll(REQUIRED_COLUMNS)) {
			throw new SQLException("Migration checkpoint result is missing required columns");
		}
		try {
			return BulkMigrationCheckpoint.builder().migrationId(migrationId)
					.sourceFingerprint(resultSet.getString(columns.get("SOURCE_FINGERPRINT")))
					.targetFingerprint(resultSet.getString(columns.get("TARGET_FINGERPRINT")))
					.processedRows(resultSet.getLong(columns.get("PROCESSED_ROWS")))
					.completedChunks(resultSet.getLong(columns.get("COMPLETED_CHUNKS")))
					.chunkSize(resultSet.getInt(columns.get("CHUNK_SIZE")))
					.lastChunkHash(resultSet.getString(columns.get("LAST_CHUNK_HASH")))
					.resumeToken(resultSet.getString(columns.get("RESUME_TOKEN")))
					.complete("1".equals(resultSet.getString(columns.get("COMPLETE_FLAG"))))
					.build()
					.validate();
		} catch (IllegalArgumentException failure) {
			throw new SQLException("Invalid migration checkpoint data for migration "
					+ migrationId, failure);
		}
	}

	private static ParametersContext parameters(final String migrationId) {
		final ParametersContext parameters = new ParametersContext();
		parameters.put("MIGRATION_ID", migrationId);
		return parameters;
	}

	static record TableIdentity(String catalog, String schema, String name) {
	}
}
