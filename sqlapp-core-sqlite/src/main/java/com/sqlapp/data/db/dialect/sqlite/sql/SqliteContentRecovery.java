/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

/** Native, set-based validation of an explicitly authoritative FTS5 content source. */
final class SqliteContentRecovery {
	static record VerifiedContent(Table table, String rowId) { }
	static String rowId(Table source, String requested) {
		if (requested != null) {
			var column = source.getColumns().get(requested);
			if (column != null) return column.getName();
			if (hasRowid(source) && List.of("rowid", "_rowid_", "oid").contains(requested.toLowerCase(java.util.Locale.ROOT))) return requested;
			throw new IllegalArgumentException("SQLite recovery document ID column does not exist: " + requested);
		}
		var identities = source.getColumns().stream().filter(Column::isIdentity).toList();
		if (identities.size() == 1) return identities.get(0).getName();
		if (hasRowid(source)) for (var alias : List.of("rowid", "_rowid_", "oid"))
			if (source.getColumns().get(alias) == null) return alias;
		var primary = source.getPrimaryKeyConstraint();
		if (!(source instanceof View) && !hasRowid(source) && primary != null && primary.getColumns().size() == 1) {
			var column = source.getColumns().get(primary.getColumns().get(0).getName());
			if (column != null && column.getDataType() != null && List.of(java.sql.JDBCType.TINYINT, java.sql.JDBCType.SMALLINT,
					java.sql.JDBCType.INTEGER, java.sql.JDBCType.BIGINT).contains(column.getDataType().getJdbcType())) return column.getName();
		}
		throw new IllegalArgumentException("SQLite recovery requires an explicit document ID column for views, WITHOUT ROWID or inaccessible rowids");
	}

	static VerifiedContent verify(Connection connection, Sqlite dialect, Table original, Table source, String documentId, Map<String, String> mappings, List<SqlOperation> operations) throws SQLException {
		var columns = original.getColumns().stream().filter(column -> !column.isHidden()).map(Column::getName).toList();
		var resolved = new java.util.LinkedHashMap<String, String>();
		for (var entry : mappings.entrySet()) {
			var column = original.getColumns().get(entry.getKey());
			if (column == null || column.isHidden())
				throw new IllegalArgumentException("SQLite recovery mapping must name an original visible FTS column: " + entry.getKey());
			if (resolved.putIfAbsent(column.getName(), entry.getValue()) != null)
				throw new IllegalArgumentException("SQLite recovery mapping repeats an original FTS column: " + entry.getKey());
		}
		var inputs = new java.util.ArrayList<String>();
		for (var name : columns) {
			String input = resolved.getOrDefault(name, name);
			var column = source.getColumns().get(input);
			if (column == null) throw new IllegalArgumentException("SQLite recovery content source is missing column " + input + " for original FTS column " + name);
			inputs.add(quote(column.getName()));
		}
		try (var statement = connection.createStatement(); var result = statement.executeQuery("PRAGMA temp.journal_mode")) {
			if (!result.next() || "off".equalsIgnoreCase(result.getString(1)))
				throw new IllegalArgumentException("SQLite content recovery requires TEMP rollback journaling");
		}
		String suffix = UUID.randomUUID().toString().replace("-", "");
		var registry = dialect.createSqlFactoryRegistry(); registry.getOptions().setCreateIfNotExists(false);
		// Capture once before dependency views are dropped. Typeless columns avoid
		// coercing document IDs or authoritative values during materialization.
		String capturedId = "sqlapp_id_" + suffix;
		var captured = new Table("sqlapp_content_" + suffix).setSchemaName("temp").setDialect(dialect);
		for (String name : java.util.stream.Stream.concat(java.util.stream.Stream.of(capturedId), columns.stream()).toList()) {
			var column = new Column(name).setDataType(com.sqlapp.data.db.datatype.DataType.ANY_DATA);
			column.getSpecifics().put("sqlite.declaredType", ""); captured.getColumns().add(column);
		}
		for (var operation : registry.createSql(captured, SqlType.CREATE)) execute(connection, operation, operations);
		String names = String.join(",", columns.stream().map(SqliteContentRecovery::quote).toList());
		execute(connection, new SqlOperation("INSERT OR ABORT INTO " + identity(captured) + " SELECT " + quote(documentId) + "," + String.join(",", inputs) + " FROM " + identity(source), SqlType.INSERT, captured), operations);
		source = captured; documentId = capturedId;
		zero(connection, "SELECT count(*) FROM " + identity(source) + " WHERE typeof(" + quote(documentId) + ")<>'integer'", original, operations);
		zero(connection, "SELECT count(*)-count(DISTINCT " + quote(documentId) + ") FROM " + identity(source), original, operations);
		equal(connection, "SELECT rowid FROM " + identity(original), "SELECT " + quote(documentId) + " FROM " + identity(source), original, operations);

		Table probe = original.clone(); probe.setName("sqlapp_recovery_" + suffix); probe.setDialect(dialect);
		for (var operation : registry.createSql(probe, SqlType.CREATE)) execute(connection, operation, operations);
		execute(connection, new SqlOperation("INSERT OR ABORT INTO " + identity(probe) + "(rowid," + names + ") SELECT "
				+ quote(documentId) + "," + names + " FROM " + identity(source), SqlType.INSERT, probe), operations);
		execute(connection, new SqlOperation("INSERT INTO " + identity(probe) + "(" + quote(probe.getName()) + ",rank) VALUES('integrity-check',1)", SqlType.INSERT, probe), operations);

		String left = "sqlapp_vocab_old_" + suffix, right = "sqlapp_vocab_new_" + suffix;
		for (var entry : List.of(java.util.Map.entry(left, original), java.util.Map.entry(right, probe))) {
			var table = entry.getValue();
			execute(connection, new SqlOperation("CREATE VIRTUAL TABLE temp." + quote(entry.getKey()) + " USING fts5vocab("
					+ literal(table.getSchemaName()) + "," + literal(table.getName()) + ",'instance')", SqlType.CREATE, original), operations);
		}
		equal(connection, "SELECT term,doc,col,offset FROM temp." + quote(left), "SELECT term,doc,col,offset FROM temp." + quote(right), original, operations);
		// Native blobs are compared without decoding or copying shadow rows. This
		// checks token counts even with detail=none/column or empty documents.
		equal(connection, "SELECT id,sz FROM " + shadow(original, "_docsize"), "SELECT id,sz FROM " + shadow(probe, "_docsize"), original, operations);
		for (var name : List.of(left, right)) execute(connection, new SqlOperation("DROP TABLE temp." + quote(name), SqlType.DROP, original), operations);
		for (var operation : registry.createSql(probe, SqlType.DROP)) execute(connection, operation, operations);
		return new VerifiedContent(captured, capturedId);
	}

	private static void equal(Connection connection, String left, String right, Table original, List<SqlOperation> operations) throws SQLException {
		zero(connection, "SELECT count(*) FROM (" + left + " EXCEPT " + right + ")", original, operations);
		zero(connection, "SELECT count(*) FROM (" + right + " EXCEPT " + left + ")", original, operations);
	}

	private static void zero(Connection connection, String sql, Table original, List<SqlOperation> operations) throws SQLException {
		try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
			if (!result.next() || result.getLong(1) != 0) throw new SQLException("SQLite content recovery source does not match original document IDs, indexed terms or token counts");
		}
		operations.add(new SqlOperation(sql, SqlType.SELECT, original));
	}

	private static void execute(Connection connection, SqlOperation operation, List<SqlOperation> operations) throws SQLException {
		try (var statement = connection.createStatement()) { statement.execute(operation.getSqlText()); }
		operations.add(operation);
	}

	private static boolean hasRowid(Table table) { return !(table instanceof View) && !"true".equals(table.getSpecifics().get("without_rowid")); }
	private static String identity(Table table) { return quote(table.getSchemaName()) + "." + quote(table.getName()); }
	private static String shadow(Table table, String suffix) { return quote(table.getSchemaName()) + "." + quote(table.getName() + suffix); }
	private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
	private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
	private SqliteContentRecovery() { }
}
