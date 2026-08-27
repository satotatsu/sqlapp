/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

/** Checkpoint store backed by a control table in the target database. */
public class JdbcBulkMigrationCheckpointStore implements TransactionalBulkMigrationCheckpointStore {
	private final Connection connection;
	private final String tableName;

	public JdbcBulkMigrationCheckpointStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid checkpoint table name: " + tableName);
		}
		final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		this.tableName = dialect.quote(tableName);
		ensureTable();
	}

	@Override
	public boolean participatesIn(final Connection candidate) {
		return connection == candidate;
	}

	@Override
	public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
		final String sql = "SELECT source_fingerprint, target_fingerprint, processed_rows, "
				+ "completed_chunks, last_chunk_hash, resume_token, complete_flag FROM " + tableName
				+ " WHERE migration_id = ?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, migrationId);
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(BulkMigrationCheckpoint.builder()
						.migrationId(migrationId)
						.sourceFingerprint(resultSet.getString(1))
						.targetFingerprint(resultSet.getString(2))
						.processedRows(resultSet.getLong(3))
						.completedChunks(resultSet.getLong(4))
						.lastChunkHash(resultSet.getString(5))
						.resumeToken(resultSet.getString(6))
						.complete("1".equals(resultSet.getString(7))).build());
			}
		}
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
		final String update = "UPDATE " + tableName + " SET source_fingerprint = ?, "
				+ "target_fingerprint = ?, processed_rows = ?, completed_chunks = ?, "
				+ "last_chunk_hash = ?, resume_token = ?, complete_flag = ? "
				+ "WHERE migration_id = ?";
		try (var statement = connection.prepareStatement(update)) {
			bind(statement, checkpoint);
			if (statement.executeUpdate() != 0) {
				return;
			}
		}
		final String insert = "INSERT INTO " + tableName + " (source_fingerprint, "
				+ "target_fingerprint, processed_rows, completed_chunks, last_chunk_hash, "
				+ "resume_token, complete_flag, migration_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
		try (var statement = connection.prepareStatement(insert)) {
			bind(statement, checkpoint);
			statement.executeUpdate();
		}
	}

	@Override
	public void delete(final String migrationId) throws SQLException {
		try (var statement = connection.prepareStatement(
				"DELETE FROM " + tableName + " WHERE migration_id = ?")) {
			statement.setString(1, migrationId);
			statement.executeUpdate();
		}
	}

	private void bind(final java.sql.PreparedStatement statement,
			final BulkMigrationCheckpoint checkpoint) throws SQLException {
		setNullable(statement, 1, checkpoint.getSourceFingerprint());
		setNullable(statement, 2, checkpoint.getTargetFingerprint());
		statement.setLong(3, checkpoint.getProcessedRows());
		statement.setLong(4, checkpoint.getCompletedChunks());
		setNullable(statement, 5, checkpoint.getLastChunkHash());
		setNullable(statement, 6, checkpoint.getResumeToken());
		statement.setString(7, checkpoint.isComplete() ? "1" : "0");
		statement.setString(8, checkpoint.getMigrationId());
	}

	private static void setNullable(final java.sql.PreparedStatement statement,
			final int index, final String value) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.VARCHAR);
		} else {
			statement.setString(index, value);
		}
	}

	private void ensureTable() throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT migration_id FROM " + tableName + " WHERE 1 = 0").close();
		} catch (SQLException missing) {
			final String create = "CREATE TABLE " + tableName + " ("
					+ "migration_id VARCHAR(255) NOT NULL PRIMARY KEY, "
					+ "source_fingerprint VARCHAR(255) NULL, target_fingerprint VARCHAR(255) NULL, "
					+ "processed_rows DECIMAL(19, 0) NOT NULL, completed_chunks DECIMAL(19, 0) NOT NULL, "
					+ "last_chunk_hash VARCHAR(64) NULL, resume_token VARCHAR(4000) NULL, "
					+ "complete_flag CHAR(1) NOT NULL)";
			try (var statement = connection.createStatement()) {
				statement.execute(create);
			} catch (SQLException createFailure) {
				createFailure.addSuppressed(missing);
				throw createFailure;
			}
			return;
		}
		ensureResumeTokenColumn();
	}

	private void ensureResumeTokenColumn() throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT resume_token FROM " + tableName + " WHERE 1 = 0").close();
			return;
		} catch (SQLException missing) {
			try (var statement = connection.createStatement()) {
				statement.execute("ALTER TABLE " + tableName + " ADD resume_token VARCHAR(4000) NULL");
			} catch (SQLException alterFailure) {
				alterFailure.addSuppressed(missing);
				throw alterFailure;
			}
		}
	}
}
