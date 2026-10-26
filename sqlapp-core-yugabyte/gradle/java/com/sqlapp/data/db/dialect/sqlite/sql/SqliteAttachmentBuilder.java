/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.Schema;

/**
 * Generates connection-local ATTACH/DETACH SQL without accessing databases or
 * files.
 */
public class SqliteAttachmentBuilder {
	private final Dialect dialect;

	public SqliteAttachmentBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		if (!(dialect instanceof Sqlite))
			throw new IllegalArgumentException("SqliteAttachmentBuilder requires a SQLite dialect");
	}

	/** Filename is a literal SQLite filename, not a SQL expression. */
	public String attach(String filename, String schema) {
		if (filename == null || filename.isBlank() || filename.indexOf('\0') >= 0)
			throw new IllegalArgumentException("SQLite ATTACH requires a nonempty filename without NUL characters");
		requireSchema(schema);
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("ATTACH DATABASE '")._add(filename.replace("'", "''"))._add("' AS ").name(schema);
		return builder.toString();
	}

	public String attach(String filename, Schema schema) {
		return attach(filename, Objects.requireNonNull(schema, "schema").getName());
	}

	public String detach(String schema) {
		requireSchema(schema);
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("DETACH DATABASE ").name(schema);
		return builder.toString();
	}

	public String detach(Schema schema) {
		return detach(Objects.requireNonNull(schema, "schema").getName());
	}

	private void requireSchema(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"SQLite attachment requires a nonempty schema name without NUL characters");
		if (value.equalsIgnoreCase("main") || value.equalsIgnoreCase("temp"))
			throw new IllegalArgumentException("SQLite attachment cannot use the reserved schema name: " + value);
	}
}
