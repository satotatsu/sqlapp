/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.ConnectionSqlExecutor;

/** Checkpoint store backed by a control table in the target database. */
public class JdbcBulkMigrationCheckpointStore implements TransactionalBulkMigrationCheckpointStore {
	private final Connection connection;
	private final String tableName;
	private final String rawTableName;
	private final String resumeTokenType;
	private final Dialect dialect;

	public JdbcBulkMigrationCheckpointStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid checkpoint table name: " + tableName);
		}
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		this.rawTableName = tableName;
		this.tableName = dialect.quote(tableName);
		this.resumeTokenType = connection.getMetaData().getDatabaseProductName()
				.toLowerCase(java.util.Locale.ROOT).contains("informix")
						? "LVARCHAR(4000)" : "VARCHAR(4000)";
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
			try {
				final Table table = checkpointTable();
				final var factory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(table, State.Added);
				new ConnectionSqlExecutor(connection, false).execute(factory.createSql(table));
			} catch (SQLException createFailure) {
				createFailure.addSuppressed(missing);
				throw createFailure;
			}
			return;
		}
		ensureResumeTokenColumn();
	}

	private Table checkpointTable() {
		final Table table = new Table(rawTableName);
		final Column migrationId = column("migration_id", DataType.VARCHAR, 255, true);
		table.getColumns().add(migrationId);
		table.getColumns().add(column("source_fingerprint", DataType.VARCHAR, 255, false));
		table.getColumns().add(column("target_fingerprint", DataType.VARCHAR, 255, false));
		table.getColumns().add(new Column("processed_rows").setDataType(DataType.DECIMAL)
				.setLength(19).setScale(0).setNotNull(true));
		table.getColumns().add(new Column("completed_chunks").setDataType(DataType.DECIMAL)
				.setLength(19).setScale(0).setNotNull(true));
		table.getColumns().add(column("last_chunk_hash", DataType.VARCHAR, 64, false));
		table.getColumns().add(column("resume_token", DataType.LONGVARCHAR, 4000, false));
		table.getColumns().add(column("complete_flag", DataType.CHAR, 1, true));
		table.setPrimaryKey("PK_" + rawTableName, migrationId);
		return table;
	}

	private static Column column(final String name, final DataType type,
			final long length, final boolean notNull) {
		return new Column(name).setDataType(type).setLength(length).setNotNull(notNull);
	}

	private void ensureResumeTokenColumn() throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT resume_token FROM " + tableName + " WHERE 1 = 0").close();
			return;
		} catch (SQLException missing) {
			try (var statement = connection.createStatement()) {
				statement.execute("ALTER TABLE " + tableName + " ADD resume_token " + resumeTokenType);
			} catch (SQLException alterFailure) {
				alterFailure.addSuppressed(missing);
				throw alterFailure;
			}
		}
	}
}
