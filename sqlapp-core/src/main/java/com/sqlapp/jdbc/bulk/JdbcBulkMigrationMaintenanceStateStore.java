/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

	private final Connection connection;
	private final String rawTableName;
	private final String tableName;
	private final Dialect dialect;
	private final Map<SqlType, SqlNode> sqlNodes = new EnumMap<>(SqlType.class);

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
	public Optional<BulkMigrationMaintenanceState> load(final String planFingerprint)
			throws SQLException {
		validateFingerprint(planFingerprint);
		final BulkMigrationMaintenanceState[] result = new BulkMigrationMaintenanceState[1];
		new JdbcHandler(sqlNodes.get(SqlType.SELECT),
				rs -> result[0] = state(rs, planFingerprint))
				.execute(connection, parameters(planFingerprint));
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
	public void delete(final String planFingerprint) throws SQLException {
		validateFingerprint(planFingerprint);
		new JdbcHandler(sqlNodes.get(SqlType.DELETE))
				.execute(connection, parameters(planFingerprint));
	}

	private BulkMigrationMaintenanceState state(final ExResultSet resultSet,
			final String fingerprint) throws SQLException {
		final Map<String, Integer> columns = new java.util.LinkedHashMap<>();
		final var metadata = resultSet.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++) {
			columns.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), i);
		}
		return new BulkMigrationMaintenanceState(fingerprint,
				BulkMigrationMaintenanceStatus.valueOf(
						resultSet.getString(columns.get("STATUS_NAME"))),
				Instant.parse(resultSet.getString(columns.get("UPDATED_AT"))),
				resultSet.getString(columns.get("FAILURE_MESSAGE")));
	}

	private void ensureTable() throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.executeQuery("SELECT " + column("PLAN_FINGERPRINT") + ", "
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
		final Table table = new Table(rawTableName);
		final Column fingerprint = varchar("PLAN_FINGERPRINT",
				BulkMigrationMaintenanceState.FINGERPRINT_MAX_LENGTH, true);
		table.getColumns().add(fingerprint);
		table.getColumns().add(varchar("STATUS_NAME", 32, true));
		table.getColumns().add(varchar("UPDATED_AT", 40, true));
		table.getColumns().add(varchar("FAILURE_MESSAGE",
				BulkMigrationMaintenanceState.FAILURE_MESSAGE_MAX_LENGTH, false));
		table.setPrimaryKey((String) null, fingerprint);
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

	private static ParametersContext parameters(final String fingerprint) {
		final ParametersContext parameters = new ParametersContext();
		parameters.put("PLAN_FINGERPRINT", fingerprint);
		return parameters;
	}

	private static ParametersContext parameters(final BulkMigrationMaintenanceState state) {
		final ParametersContext parameters = parameters(state.planFingerprint());
		parameters.put("STATUS_NAME", state.status().name());
		parameters.put("UPDATED_AT", state.updatedAt().toString());
		parameters.put("FAILURE_MESSAGE", state.failureMessage());
		return parameters;
	}

	private static void validateFingerprint(final String fingerprint) {
		new BulkMigrationMaintenanceState(fingerprint,
				BulkMigrationMaintenanceStatus.PREPARING, Instant.EPOCH, null);
	}
}
