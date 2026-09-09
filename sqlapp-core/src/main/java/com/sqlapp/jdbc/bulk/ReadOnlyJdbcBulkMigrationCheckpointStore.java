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

import com.sqlapp.data.db.dialect.DialectResolver;

/** JDBC checkpoint reader that never creates, upgrades, updates, or deletes tables. */
final class ReadOnlyJdbcBulkMigrationCheckpointStore
		implements BulkMigrationCheckpointStore {
	private final Connection connection;
	private final String rawTableName;
	private final String tableName;

	ReadOnlyJdbcBulkMigrationCheckpointStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid checkpoint table name: " + tableName);
		}
		this.rawTableName = tableName;
		this.tableName = DialectResolver.getInstance().getDialect(connection).quote(tableName);
	}

	@Override
	public Optional<BulkMigrationCheckpoint> load(final String migrationId)
			throws SQLException {
		BulkMigrationCheckpoint.validateMigrationId(migrationId);
		if (!tableExists()) {
			return Optional.empty();
		}
		final String sql = "SELECT SOURCE_FINGERPRINT, TARGET_FINGERPRINT, "
				+ "PROCESSED_ROWS, COMPLETED_CHUNKS, CHUNK_SIZE, LAST_CHUNK_HASH, "
				+ "RESUME_TOKEN, COMPLETE_FLAG FROM " + tableName
				+ " WHERE MIGRATION_ID = ?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, migrationId);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(checkpoint(resultSet, migrationId));
			}
		}
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) {
		throw new UnsupportedOperationException("Read-only checkpoint store");
	}

	@Override
	public void delete(final String migrationId) {
		throw new UnsupportedOperationException("Read-only checkpoint store");
	}

	private boolean tableExists() throws SQLException {
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
		return !candidates.isEmpty();
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

	private static BulkMigrationCheckpoint checkpoint(final ResultSet resultSet,
			final String migrationId) throws SQLException {
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
	}

	private record TableIdentity(String catalog, String schema, String name) {
	}
}
