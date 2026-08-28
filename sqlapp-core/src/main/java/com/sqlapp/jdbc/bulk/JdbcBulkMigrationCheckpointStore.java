/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.ConnectionSqlExecutor;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Checkpoint store backed by a control table in the target database. */
public class JdbcBulkMigrationCheckpointStore implements TransactionalBulkMigrationCheckpointStore {
	private final Connection connection;
	private final String tableName;
	private final String rawTableName;
	private final String resumeTokenType;
	private final Dialect dialect;
	private final Map<SqlType, SqlNode> sqlNodes = new EnumMap<>(SqlType.class);

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
		final var registry = dialect.createSqlFactoryRegistry();
		final Table checkpointTable = checkpointTable();
		for (final SqlType sqlType : new SqlType[] { SqlType.SELECT, SqlType.UPDATE,
				SqlType.INSERT, SqlType.DELETE }) {
			sqlNodes.put(sqlType, registry.createSqlNodes(checkpointTable, sqlType).get(0));
		}
		ensureTable();
	}

	@Override
	public boolean participatesIn(final Connection candidate) {
		return connection == candidate;
	}

	@Override
	public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
		final BulkMigrationCheckpoint[] result = new BulkMigrationCheckpoint[1];
		new JdbcHandler(sqlNode(SqlType.SELECT), rs -> result[0] = checkpoint(rs, migrationId))
				.execute(connection, parameters(migrationId));
		return Optional.ofNullable(result[0]);
	}

	private static BulkMigrationCheckpoint checkpoint(final ExResultSet resultSet,
			final String migrationId) throws SQLException {
		final Map<String, Integer> columns = new LinkedHashMap<>();
		final var metadata = resultSet.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++) {
			columns.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
		}
		return BulkMigrationCheckpoint.builder().migrationId(migrationId)
				.sourceFingerprint(resultSet.getString(columns.get("SOURCE_FINGERPRINT")))
				.targetFingerprint(resultSet.getString(columns.get("TARGET_FINGERPRINT")))
				.processedRows(resultSet.getLong(columns.get("PROCESSED_ROWS")))
				.completedChunks(resultSet.getLong(columns.get("COMPLETED_CHUNKS")))
				.lastChunkHash(resultSet.getString(columns.get("LAST_CHUNK_HASH")))
				.resumeToken(resultSet.getString(columns.get("RESUME_TOKEN")))
				.complete("1".equals(resultSet.getString(columns.get("COMPLETE_FLAG")))).build();
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
		final ParametersContext parameters = parameters(checkpoint);
		final JdbcHandler update = new JdbcHandler(sqlNode(SqlType.UPDATE));
		update.execute(connection, parameters);
		if (update.getUpdateCount() == 0) {
			new JdbcHandler(sqlNode(SqlType.INSERT)).execute(connection, parameters);
		}
	}

	@Override
	public void delete(final String migrationId) throws SQLException {
		new JdbcHandler(sqlNode(SqlType.DELETE)).execute(connection, parameters(migrationId));
	}

	private SqlNode sqlNode(final SqlType sqlType) {
		return sqlNodes.get(sqlType);
	}

	private static ParametersContext parameters(final String migrationId) {
		final ParametersContext parameters = new ParametersContext();
		parameters.put("MIGRATION_ID", migrationId);
		return parameters;
	}

	private static ParametersContext parameters(final BulkMigrationCheckpoint checkpoint) {
		final ParametersContext parameters = parameters(checkpoint.getMigrationId());
		parameters.put("SOURCE_FINGERPRINT", checkpoint.getSourceFingerprint());
		parameters.put("TARGET_FINGERPRINT", checkpoint.getTargetFingerprint());
		parameters.put("PROCESSED_ROWS", checkpoint.getProcessedRows());
		parameters.put("COMPLETED_CHUNKS", checkpoint.getCompletedChunks());
		parameters.put("LAST_CHUNK_HASH", checkpoint.getLastChunkHash());
		parameters.put("RESUME_TOKEN", checkpoint.getResumeToken());
		parameters.put("COMPLETE_FLAG", checkpoint.isComplete() ? "1" : "0");
		return parameters;
	}

	private void ensureTable() throws SQLException {
		String generatedDdl = null;
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT " + columnName("MIGRATION_ID") + " FROM "
					+ tableName + " WHERE 1 = 0").close();
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
			statement.executeQuery("SELECT " + columnName("SOURCE_FINGERPRINT") + ", "
					+ columnName("TARGET_FINGERPRINT") + ", " + columnName("PROCESSED_ROWS") + ", "
					+ columnName("COMPLETED_CHUNKS") + ", " + columnName("LAST_CHUNK_HASH") + ", "
					+ columnName("COMPLETE_FLAG") + " FROM "
					+ tableName + " WHERE 1 = 0").close();
		} catch (SQLException e) {
			if (generatedDdl != null) {
				e.addSuppressed(new SQLException("Generated checkpoint DDL: " + generatedDdl));
			}
			throw e;
		}
	}

	private Table checkpointTable() {
		return checkpointTable(true);
	}

	private Table checkpointTable(final boolean includeResumeToken) {
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
		if (includeResumeToken) {
			table.getColumns().add(new Column("RESUME_TOKEN").setDataType(DataType.VARCHAR)
					.setDataTypeName(resumeTokenType.startsWith("LVARCHAR") ? "LVARCHAR" : "VARCHAR")
					.setLength(4000));
		}
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
			statement.executeQuery("SELECT " + columnName("RESUME_TOKEN") + " FROM "
					+ tableName + " WHERE 1 = 0").close();
			return;
		} catch (SQLException missing) {
			try {
				final Table original = checkpointTable(false);
				final Table target = checkpointTable(true);
				final var difference = original.diff(target);
				final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(target, SqlType.ALTER);
				final var operations = factory.createDiffSql(difference);
				new ConnectionSqlExecutor(connection, false).execute(operations);
			} catch (SQLException alterFailure) {
				alterFailure.addSuppressed(missing);
				throw alterFailure;
			}
		}
	}

	private String columnName(final String name) {
		return dialect.quote(name);
	}
}
