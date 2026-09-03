/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.dialect.DialectResolver;

/** JDBC checkpoint reader that never creates, upgrades, updates, or deletes tables. */
public final class ReadOnlyJdbcBulkMigrationCheckpointStore
		implements BulkMigrationCheckpointStore {
	private final Connection connection;
	private final String rawTableName;
	private final String tableName;

	public ReadOnlyJdbcBulkMigrationCheckpointStore(final Connection connection,
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
		try (ResultSet tables = connection.getMetaData().getTables(
				connection.getCatalog(), null, "%", new String[] { "TABLE" })) {
			while (tables.next()) {
				if (rawTableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
					return true;
				}
			}
		}
		return false;
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
}
