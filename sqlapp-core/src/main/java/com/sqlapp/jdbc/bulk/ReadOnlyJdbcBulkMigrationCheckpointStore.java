/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** JDBC checkpoint reader that never creates, upgrades, updates, or deletes tables. */
final class ReadOnlyJdbcBulkMigrationCheckpointStore
		implements BulkMigrationCheckpointStore {
	private static final Set<String> REQUIRED_COLUMNS = Set.of("MIGRATION_ID",
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
				.createSqlFactoryRegistry().createSqlNodes(table(tableName), SqlType.SELECT)
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

	private Optional<TableIdentity> resolveTable() throws SQLException {
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

	private void validateStructure(final TableIdentity identity) throws SQLException {
		final Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		try (ResultSet result = connection.getMetaData().getColumns(identity.catalog(),
				identity.schema(), identity.name(), "%")) {
			while (result.next()) {
				columns.add(result.getString("COLUMN_NAME"));
			}
		}
		if (!columns.containsAll(REQUIRED_COLUMNS)) {
			final Set<String> missing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			missing.addAll(REQUIRED_COLUMNS);
			missing.removeAll(columns);
			throw new SQLException("Migration checkpoint table " + rawTableName
					+ " is missing required columns: " + missing);
		}
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
		try {
			return BulkMigrationCheckpoint.builder().migrationId(migrationId)
					.sourceFingerprint(resultSet.getString("SOURCE_FINGERPRINT"))
					.targetFingerprint(resultSet.getString("TARGET_FINGERPRINT"))
					.processedRows(resultSet.getLong("PROCESSED_ROWS"))
					.completedChunks(resultSet.getLong("COMPLETED_CHUNKS"))
					.chunkSize(resultSet.getInt("CHUNK_SIZE"))
					.lastChunkHash(resultSet.getString("LAST_CHUNK_HASH"))
					.resumeToken(resultSet.getString("RESUME_TOKEN"))
					.complete("1".equals(resultSet.getString("COMPLETE_FLAG"))).build()
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

	private static Table table(final String tableName) {
		final Table table = new Table(tableName);
		final Column migrationId = new Column("MIGRATION_ID")
				.setDataType(DataType.VARCHAR).setLength(255).setNotNull(true);
		table.getColumns().add(migrationId);
		table.getColumns().add(new Column("SOURCE_FINGERPRINT").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("TARGET_FINGERPRINT").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("PROCESSED_ROWS").setDataType(DataType.DECIMAL));
		table.getColumns().add(new Column("COMPLETED_CHUNKS").setDataType(DataType.DECIMAL));
		table.getColumns().add(new Column("CHUNK_SIZE").setDataType(DataType.INT));
		table.getColumns().add(new Column("LAST_CHUNK_HASH").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("RESUME_TOKEN").setDataType(DataType.VARCHAR));
		table.getColumns().add(new Column("COMPLETE_FLAG").setDataType(DataType.CHAR));
		table.setPrimaryKey((String) null, migrationId);
		return table;
	}

	private record TableIdentity(String catalog, String schema, String name) {
	}
}
