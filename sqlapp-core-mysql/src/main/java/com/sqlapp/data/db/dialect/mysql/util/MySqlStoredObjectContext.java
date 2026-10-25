/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.util;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Trigger;

/**
 * Executes ordinary factory operations under the object's saved creation
 * context.
 */
public final class MySqlStoredObjectContext {
	public static final String SQL_MODE = "mysql_sql_mode";
	public static final String TIME_ZONE = "mysql_time_zone";
	public static final String CHARACTER_SET_CLIENT = "mysql_character_set_client";
	public static final String COLLATION_CONNECTION = "mysql_collation_connection";

	private MySqlStoredObjectContext() {
	}

	public static void read(ResultSet rows, AbstractSchemaObject<?> object) throws SQLException {
		Map<String, String> fields = Map.of("SQL_MODE", SQL_MODE, "TIME_ZONE", TIME_ZONE, "CHARACTER_SET_CLIENT",
				CHARACTER_SET_CLIENT, "COLLATION_CONNECTION", COLLATION_CONNECTION);
		ResultSetMetaData metadata = rows.getMetaData();
		for (int i = 1; i <= metadata.getColumnCount(); i++) {
			String label = metadata.getColumnLabel(i);
			if (label == null || label.isEmpty())
				label = metadata.getColumnName(i);
			String key = fields.get(label.toUpperCase(Locale.ROOT));
			if (key != null) {
				String value = rows.getString(i);
				if (value != null)
					object.getSpecifics().put(key, value);
			}
		}
	}

	/**
	 * DDL may commit implicitly. This restores session settings, not database
	 * changes.
	 */
	public static List<SqlOperation> create(Connection connection, AbstractSchemaObject<?> object) throws SQLException {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		if (!"MySQL".equalsIgnoreCase(dialect.getProductName())
				&& !"MariaDB".equalsIgnoreCase(dialect.getProductName())) {
			throw new IllegalArgumentException("Stored-object context requires a MySQL or MariaDB connection");
		}
		List<SqlOperation> operations = dialect.createSqlFactoryRegistry().createSql(object, SqlType.CREATE);
		String catalog = connection.getCatalog();
		String targetCatalog = object.getSchemaName();
		if (targetCatalog == null && object instanceof Trigger trigger)
			targetCatalog = trigger.getTableSchemaName();
		boolean changeCatalog = targetCatalog != null && !Objects.equals(catalog, targetCatalog);
		if (changeCatalog && (catalog == null || catalog.isEmpty())) {
			throw new IllegalArgumentException(
					"Select a current database before context-aware CREATE so it can be restored");
		}
		Map<String, String> requested = new LinkedHashMap<>();
		if (object.getSpecifics().containsKey(SQL_MODE))
			requested.put("sql_mode", object.getSpecifics().get(SQL_MODE));
		if (object.getSpecifics().containsKey(TIME_ZONE))
			requested.put("time_zone", object.getSpecifics().get(TIME_ZONE));
		Map<String, String> saved = new LinkedHashMap<>();
		if (!requested.isEmpty()) {
			try (Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery("SELECT @@SESSION.sql_mode, @@SESSION.time_zone")) {
				if (!rows.next())
					throw new SQLException("Cannot read MySQL session creation context");
				if (requested.containsKey("sql_mode"))
					saved.put("sql_mode", rows.getString(1));
				if (requested.containsKey("time_zone"))
					saved.put("time_zone", rows.getString(2));
			}
		}
		Throwable failure = null;
		try {
			if (changeCatalog)
				connection.setCatalog(targetCatalog);
			for (var setting : requested.entrySet())
				set(connection, setting.getKey(), setting.getValue());
			try (Statement statement = connection.createStatement()) {
				for (SqlOperation operation : operations)
					statement.execute(operation.getSqlText());
			}
			return operations;
		} catch (SQLException | RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			SQLException restoreFailure = null;
			for (var setting : saved.entrySet()) {
				try {
					set(connection, setting.getKey(), setting.getValue());
				} catch (SQLException e) {
					if (restoreFailure == null)
						restoreFailure = e;
					else
						restoreFailure.addSuppressed(e);
				}
			}
			if (changeCatalog) {
				try {
					connection.setCatalog(catalog);
				} catch (SQLException e) {
					if (restoreFailure == null)
						restoreFailure = e;
					else
						restoreFailure.addSuppressed(e);
				}
			}
			if (restoreFailure != null) {
				if (failure == null)
					throw restoreFailure;
				failure.addSuppressed(restoreFailure);
			}
		}
	}

	private static void set(Connection connection, String name, String value) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SET SESSION " + name + " = ?")) {
			statement.setString(1, value);
			statement.execute();
		}
	}
}
