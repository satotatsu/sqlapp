/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
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

/** Maintenance state store backed by a control table in the target database. */
public class JdbcBulkMigrationMaintenanceStateStore
		implements BulkMigrationMaintenanceStateStore {
	public static final String DEFAULT_TABLE_NAME = "sqlapp_bulk_maintenance";
	private static final Set<String> REQUIRED_COLUMNS = Set.of("JOB_ID", "PLAN_FINGERPRINT",
			"STATUS_NAME", "UPDATED_AT", "FAILURE_MESSAGE");

	private final Connection connection;
	private final String rawTableName;
	private final String tableName;
	private final Dialect dialect;
	private final Map<SqlType, SqlNode> sqlNodes = new EnumMap<>(SqlType.class);

	/** Returns a reader that does not create or modify the maintenance table. */
	public static BulkMigrationMaintenanceStateStore readOnly(
			final Connection connection, final String tableName) throws SQLException {
		return new ReadOnlyStore(connection, tableName);
	}

	public JdbcBulkMigrationMaintenanceStateStore(final Connection connection)
			throws SQLException {
		this(connection, DEFAULT_TABLE_NAME);
	}

	public JdbcBulkMigrationMaintenanceStateStore(final Connection connection,
			final String tableName) throws SQLException {
		this.connection = Objects.requireNonNull(connection, "connection");
		if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid maintenance table name: " + tableName);
		}
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		this.rawTableName = tableName;
		this.tableName = dialect.quote(tableName);
		final var registry = dialect.createSqlFactoryRegistry();
		final Table table = stateTable();
		for (final SqlType type : new SqlType[] { SqlType.SELECT, SqlType.UPDATE,
				SqlType.INSERT, SqlType.DELETE }) {
			sqlNodes.put(type, registry.createSqlNodes(table, type).get(0));
		}
		ensureTable();
	}

	@Override
	public Optional<BulkMigrationMaintenanceState> load(final String jobId)
			throws SQLException {
		validateJobId(jobId);
		final BulkMigrationMaintenanceState[] result = new BulkMigrationMaintenanceState[1];
		new JdbcHandler(sqlNodes.get(SqlType.SELECT),
				rs -> result[0] = state(rs, jobId))
				.execute(connection, parameters(jobId));
		return Optional.ofNullable(result[0]);
	}

	@Override
	public void save(final BulkMigrationMaintenanceState state) throws SQLException {
		Objects.requireNonNull(state, "state");
		final ParametersContext parameters = parameters(state);
		final JdbcHandler update = new JdbcHandler(sqlNodes.get(SqlType.UPDATE));
		update.execute(connection, parameters);
		if (update.getUpdateCount() == 0) {
			new JdbcHandler(sqlNodes.get(SqlType.INSERT)).execute(connection, parameters);
		}
	}

	@Override
	public void delete(final String jobId) throws SQLException {
		validateJobId(jobId);
		new JdbcHandler(sqlNodes.get(SqlType.DELETE))
				.execute(connection, parameters(jobId));
	}

	private static BulkMigrationMaintenanceState state(final ExResultSet resultSet,
			final String jobId) throws SQLException {
		final Map<String, Integer> columns = new LinkedHashMap<>();
		final var metadata = resultSet.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++) {
			columns.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
		}
		if (!columns.keySet().containsAll(REQUIRED_COLUMNS)) {
			throw new SQLException("Migration maintenance result is missing required columns");
		}
		try {
			return new BulkMigrationMaintenanceState(jobId,
					resultSet.getString(columns.get("PLAN_FINGERPRINT")),
					BulkMigrationMaintenanceStatus.valueOf(
							resultSet.getString(columns.get("STATUS_NAME"))),
					Instant.parse(resultSet.getString(columns.get("UPDATED_AT"))),
					resultSet.getString(columns.get("FAILURE_MESSAGE")));
		} catch (RuntimeException failure) {
			throw new SQLException("Invalid migration maintenance data for job "
					+ jobId, failure);
		}
	}

	private void ensureTable() throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT " + column("JOB_ID") + ", "
					+ column("PLAN_FINGERPRINT") + ", "
					+ column("STATUS_NAME") + ", " + column("UPDATED_AT") + ", "
					+ column("FAILURE_MESSAGE") + " FROM " + tableName
					+ " WHERE 1 = 0").close();
			return;
		} catch (SQLException missing) {
			try {
				final Table table = stateTable();
				final SqlFactory<Table> factory = dialect.createSqlFactoryRegistry()
						.getSqlFactory(table, State.Added);
				new ConnectionSqlExecutor(connection, false).execute(factory.createSql(table));
			} catch (SQLException createFailure) {
				createFailure.addSuppressed(missing);
				throw createFailure;
			}
		}
	}

	private Table stateTable() {
		return stateTable(rawTableName);
	}

	private static Table stateTable(final String tableName) {
		final Table table = new Table(tableName);
		final Column jobId = varchar("JOB_ID",
				BulkMigrationMaintenanceState.JOB_ID_MAX_LENGTH, true);
		final Column fingerprint = varchar("PLAN_FINGERPRINT",
				BulkMigrationMaintenanceState.FINGERPRINT_MAX_LENGTH, true);
		table.getColumns().add(jobId);
		table.getColumns().add(fingerprint);
		table.getColumns().add(varchar("STATUS_NAME", 32, true));
		table.getColumns().add(varchar("UPDATED_AT", 40, true));
		table.getColumns().add(varchar("FAILURE_MESSAGE",
				BulkMigrationMaintenanceState.FAILURE_MESSAGE_MAX_LENGTH, false));
		table.setPrimaryKey((String) null, jobId);
		return table;
	}

	private static Column varchar(final String name, final long length,
			final boolean notNull) {
		return new Column(name).setDataType(DataType.VARCHAR).setLength(length)
				.setNotNull(notNull);
	}

	private String column(final String name) {
		return dialect.quote(name);
	}

	private static ParametersContext parameters(final String jobId) {
		final ParametersContext parameters = new ParametersContext();
		parameters.put("JOB_ID", jobId);
		return parameters;
	}

	private static ParametersContext parameters(final BulkMigrationMaintenanceState state) {
		final ParametersContext parameters = parameters(state.jobId());
		parameters.put("PLAN_FINGERPRINT", state.planFingerprint());
		parameters.put("STATUS_NAME", state.status().name());
		parameters.put("UPDATED_AT", state.updatedAt().toString());
		parameters.put("FAILURE_MESSAGE", state.failureMessage());
		return parameters;
	}

	private static void validateJobId(final String jobId) {
		new BulkMigrationMaintenanceState(jobId, "validation",
				BulkMigrationMaintenanceStatus.PREPARING, Instant.EPOCH, null);
	}

	private static final class ReadOnlyStore
			implements BulkMigrationMaintenanceStateStore {
		private final Connection connection;
		private final String rawTableName;
		private final SqlNode selectNode;

		private ReadOnlyStore(final Connection connection, final String tableName)
				throws SQLException {
			this.connection = Objects.requireNonNull(connection, "connection");
			if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
				throw new IllegalArgumentException(
						"Invalid maintenance table name: " + tableName);
			}
			this.rawTableName = tableName;
			this.selectNode = DialectResolver.getInstance().getDialect(connection)
					.createSqlFactoryRegistry()
					.createSqlNodes(stateTable(tableName), SqlType.SELECT).get(0);
		}

		@Override
		public Optional<BulkMigrationMaintenanceState> load(
				final String jobId) throws SQLException {
			validateJobId(jobId);
			final Optional<TableIdentity> identity = resolveTable();
			if (identity.isEmpty()) {
				return Optional.empty();
			}
			validateStructure(identity.orElseThrow());
			final BulkMigrationMaintenanceState[] result =
					new BulkMigrationMaintenanceState[1];
			new JdbcHandler(selectNode,
					rs -> result[0] = state(rs, jobId)).execute(connection,
						parameters(jobId));
			return Optional.ofNullable(result[0]);
		}

		@Override
		public void save(final BulkMigrationMaintenanceState state) {
			throw new UnsupportedOperationException("Read-only maintenance store");
		}

		@Override
		public void delete(final String jobId) {
			throw new UnsupportedOperationException("Read-only maintenance store");
		}

		private Optional<TableIdentity> resolveTable() throws SQLException {
			final String schema = currentSchema();
			final Set<TableIdentity> candidates = new HashSet<>();
			try (ResultSet tables = connection.getMetaData().getTables(
					connection.getCatalog(), schemaPattern(schema), "%",
					new String[] { "TABLE" })) {
				while (tables.next()) {
					if (rawTableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))
							&& (schema == null
									|| schema.equals(tables.getString("TABLE_SCHEM")))) {
						candidates.add(new TableIdentity(tables.getString("TABLE_CAT"),
								tables.getString("TABLE_SCHEM"),
								tables.getString("TABLE_NAME")));
					}
				}
			}
			if (candidates.size() > 1) {
				throw new SQLException("Ambiguous migration maintenance table "
						+ rawTableName + "; multiple catalog, schema or case-sensitive "
						+ "table matches exist");
			}
			return candidates.stream().findFirst();
		}

		private void validateStructure(final TableIdentity identity)
				throws SQLException {
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
				throw new SQLException("Migration maintenance table " + rawTableName
						+ " is missing required columns: " + missing);
			}
			final Set<String> primaryKey = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			try (ResultSet keys = connection.getMetaData().getPrimaryKeys(
					identity.catalog(), identity.schema(), identity.name())) {
				while (keys.next()) {
					primaryKey.add(keys.getString("COLUMN_NAME"));
				}
			}
			if (primaryKey.size() != 1 || !primaryKey.contains("JOB_ID")) {
				throw new SQLException("Migration maintenance table " + rawTableName
						+ " must have a primary key on JOB_ID alone");
			}
		}

		private String currentSchema() throws SQLException {
			try {
				final String schema = connection.getMetaData()
						.supportsSchemasInTableDefinitions() ? connection.getSchema() : null;
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

		private record TableIdentity(String catalog, String schema, String name) {
		}
	}
}
