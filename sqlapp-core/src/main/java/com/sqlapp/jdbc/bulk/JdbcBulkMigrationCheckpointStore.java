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
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;

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
		final String sql = "SELECT SOURCE_FINGERPRINT, TARGET_FINGERPRINT, PROCESSED_ROWS, "
				+ "COMPLETED_CHUNKS, LAST_CHUNK_HASH, RESUME_TOKEN, COMPLETE_FLAG FROM " + tableName
				+ " WHERE MIGRATION_ID = ?";
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
		final String update = "UPDATE " + tableName + " SET SOURCE_FINGERPRINT = ?, "
				+ "TARGET_FINGERPRINT = ?, PROCESSED_ROWS = ?, COMPLETED_CHUNKS = ?, "
				+ "LAST_CHUNK_HASH = ?, RESUME_TOKEN = ?, COMPLETE_FLAG = ? "
				+ "WHERE MIGRATION_ID = ?";
		try (var statement = connection.prepareStatement(update)) {
			bind(statement, checkpoint);
			if (statement.executeUpdate() != 0) {
				return;
			}
		}
		final String insert = "INSERT INTO " + tableName + " (SOURCE_FINGERPRINT, "
				+ "TARGET_FINGERPRINT, PROCESSED_ROWS, COMPLETED_CHUNKS, LAST_CHUNK_HASH, "
				+ "RESUME_TOKEN, COMPLETE_FLAG, MIGRATION_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
		try (var statement = connection.prepareStatement(insert)) {
			bind(statement, checkpoint);
			statement.executeUpdate();
		}
	}

	@Override
	public void delete(final String migrationId) throws SQLException {
		try (var statement = connection.prepareStatement(
				"DELETE FROM " + tableName + " WHERE MIGRATION_ID = ?")) {
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
		String generatedDdl = null;
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT MIGRATION_ID FROM " + tableName + " WHERE 1 = 0").close();
		} catch (SQLException missing) {
			try {
				final Table table = checkpointTable();
				final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(table, State.Added);
				final var operations = factory.createSql(table);
				generatedDdl = operations.stream().map(op -> op.getSqlText())
						.collect(java.util.stream.Collectors.joining("; "));
				try {
					new ConnectionSqlExecutor(connection, false).execute(operations);
				} catch (SQLException e) {
					e.addSuppressed(new SQLException("Generated checkpoint DDL: " + generatedDdl));
					throw e;
				}
			} catch (SQLException createFailure) {
				createFailure.addSuppressed(missing);
				throw createFailure;
			}
		}
		try {
			ensureResumeTokenColumn();
		} catch (SQLException e) {
			if (generatedDdl != null) {
				e.addSuppressed(new SQLException("Generated checkpoint DDL: " + generatedDdl));
			}
			throw e;
		}
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT SOURCE_FINGERPRINT, TARGET_FINGERPRINT, "
					+ "PROCESSED_ROWS, COMPLETED_CHUNKS, LAST_CHUNK_HASH, COMPLETE_FLAG FROM "
					+ tableName + " WHERE 1 = 0").close();
		} catch (SQLException e) {
			if (generatedDdl != null) {
				e.addSuppressed(new SQLException("Generated checkpoint DDL: " + generatedDdl));
			}
			throw e;
		}
	}

	private Table checkpointTable() {
		final Table table = new Table(rawTableName);
		final Column migrationId = column("MIGRATION_ID", DataType.VARCHAR, 255, true);
		table.getColumns().add(migrationId);
		table.getColumns().add(column("SOURCE_FINGERPRINT", DataType.VARCHAR, 255, false));
		table.getColumns().add(column("TARGET_FINGERPRINT", DataType.VARCHAR, 255, false));
		table.getColumns().add(new Column("PROCESSED_ROWS").setDataType(DataType.DECIMAL)
				.setLength(19).setScale(0).setNotNull(true));
		table.getColumns().add(new Column("COMPLETED_CHUNKS").setDataType(DataType.DECIMAL)
				.setLength(19).setScale(0).setNotNull(true));
		table.getColumns().add(column("LAST_CHUNK_HASH", DataType.VARCHAR, 64, false));
		table.getColumns().add(new Column("RESUME_TOKEN").setDataType(DataType.VARCHAR)
				.setDataTypeName(resumeTokenType.startsWith("LVARCHAR") ? "LVARCHAR" : "VARCHAR")
				.setLength(4000));
		table.getColumns().add(column("COMPLETE_FLAG", DataType.CHAR, 1, true));
		table.setPrimaryKey((String) null, migrationId);
		return table;
	}

	private static Column column(final String name, final DataType type,
			final long length, final boolean notNull) {
		return new Column(name).setDataType(type).setLength(length).setNotNull(notNull);
	}

	private void ensureResumeTokenColumn() throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT RESUME_TOKEN FROM " + tableName + " WHERE 1 = 0").close();
			return;
		} catch (SQLException missing) {
			try (var statement = connection.createStatement()) {
				statement.execute("ALTER TABLE " + tableName + " ADD RESUME_TOKEN " + resumeTokenType);
			} catch (SQLException alterFailure) {
				alterFailure.addSuppressed(missing);
				throw alterFailure;
			}
		}
	}
}
