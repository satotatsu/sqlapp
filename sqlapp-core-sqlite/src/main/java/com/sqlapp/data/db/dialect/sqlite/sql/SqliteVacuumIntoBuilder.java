/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.Schema;

/** Generates VACUUM INTO SQL; filesystem access and execution belong to the caller. */
public class SqliteVacuumIntoBuilder {
	private final Sqlite dialect;
	private String schema = "main";
	private String destination;

	public SqliteVacuumIntoBuilder(Dialect dialect) {
		Objects.requireNonNull(dialect, "dialect");
		if (!(dialect instanceof Sqlite sqlite))
			throw new IllegalArgumentException("SqliteVacuumIntoBuilder requires a SQLite dialect");
		this.dialect = sqlite;
	}

	public SqliteVacuumIntoBuilder schema(String value) {
		requireText(value, "schema");
		schema = value;
		return this;
	}

	public SqliteVacuumIntoBuilder schema(Schema value) {
		return schema(Objects.requireNonNull(value, "schema").getName());
	}

	/** A literal SQLite filename, never a raw SQL expression. */
	public SqliteVacuumIntoBuilder destination(String value) {
		requireText(value, "destination");
		destination = value;
		return this;
	}

	public String build() {
		if (!dialect.supportsVacuumInto())
			throw new IllegalArgumentException("SQLite VACUUM INTO requires SQLite 3.27 or later");
		if (destination == null)
			throw new IllegalArgumentException("SQLite VACUUM INTO destination is required");
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("VACUUM ").name(schema)._add(" INTO '")._add(destination.replace("'", "''"))._add("'");
		return builder.toString();
	}

	private void requireText(String value, String kind) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("SQLite VACUUM INTO requires a nonempty " + kind + " without NUL characters");
	}
}
