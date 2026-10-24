/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.util.MySqlStoredObjectContext;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/** Explicit full-DDL snapshot for attributes not exposed by bulk catalog metadata. */
public final class MySqlSchemaSnapshot {
	private MySqlSchemaSnapshot() { }

	/**
	 * Uses bulk readers, then one SHOW CREATE per table/view. MySQL has no portable
	 * bulk catalog containing executable CREATE text, view ALGORITHM or parser plugins.
	 * Use the ordinary SchemaReader when component metadata is sufficient.
	 */
	public static Schema read(Connection connection, String schemaName) throws SQLException {
		if (schemaName == null || schemaName.isBlank()) throw new IllegalArgumentException("Snapshot requires a schema name");
		var dialect = DialectResolver.getInstance().getDialect(connection);
		if (!"MySQL".equalsIgnoreCase(dialect.getProductName()) && !"MariaDB".equalsIgnoreCase(dialect.getProductName())) {
			throw new IllegalArgumentException("Snapshot requires MySQL or MariaDB");
		}
		var reader = dialect.getCatalogReader().getSchemaReader();
		reader.setSchemaName(schemaName);
		var schemas = reader.getAllFull(connection);
		if (schemas.size() != 1) throw new SQLException("Snapshot schema is missing or ambiguous: " + schemaName);
		Schema schema = schemas.get(0);
		String mode;
		boolean quoted;
		try (Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("SELECT @@SESSION.sql_mode, @@SESSION.sql_quote_show_create")) {
			if (!rows.next()) throw new SQLException("Cannot read SHOW CREATE session settings");
			mode = rows.getString(1); quoted = rows.getBoolean(2);
		}
		if (!quoted) setQuote(connection, true);
		Throwable failure = null;
		try {
			for (Table table : schema.getTables()) readDefinition(connection, schema, table, "TABLE", mode);
			for (var view : schema.getViews()) readDefinition(connection, schema, view, "VIEW", mode);
		} catch (SQLException | RuntimeException | Error e) {
			failure = e; throw e;
		} finally {
			if (!quoted) {
				try { setQuote(connection, false); }
				catch (SQLException e) { if (failure == null) throw e; else failure.addSuppressed(e); }
			}
		}
		return schema;
	}

	private static void readDefinition(Connection connection, Schema schema, Table object, String kind, String mode) throws SQLException {
		String sql = "SHOW CREATE " + kind + " " + quote(schema.getName()) + "." + quote(object.getName());
		try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
			if (!rows.next()) throw new SQLException("Missing CREATE definition for " + schema.getName() + "." + object.getName());
			String definition = rows.getString(2);
			if (definition == null || definition.isBlank()) throw new SQLException("CREATE definition is unavailable for " + object.getName());
			object.setDefinition(definition);
			object.getSpecifics().putIfAbsent(MySqlStoredObjectContext.SQL_MODE, mode);
			MySqlStoredObjectContext.read(rows, object);
		}
	}
	private static void setQuote(Connection connection, boolean quote) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("SET SESSION sql_quote_show_create=" + (quote ? "1" : "0"));
		}
	}

	private static String quote(String name) { return "`" + name.replace("`", "``") + "`"; }
}
