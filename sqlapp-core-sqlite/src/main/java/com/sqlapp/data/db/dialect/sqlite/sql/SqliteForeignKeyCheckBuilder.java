/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/** Generates foreign-key diagnostics for one SQLite database or child table. */
public class SqliteForeignKeyCheckBuilder {
	private final Sqlite dialect;
	private String schema = "main";
	private String table;

	public SqliteForeignKeyCheckBuilder(Dialect dialect) {
		Objects.requireNonNull(dialect, "dialect");
		if (!(dialect instanceof Sqlite sqlite))
			throw new IllegalArgumentException("SqliteForeignKeyCheckBuilder requires a SQLite dialect");
		this.dialect = sqlite;
	}

	public SqliteForeignKeyCheckBuilder schema(String value) {
		requireName(value, "schema");
		schema = value;
		return this;
	}

	public SqliteForeignKeyCheckBuilder schema(Schema value) {
		return schema(Objects.requireNonNull(value, "schema").getName());
	}

	/** Selects a child table within the currently selected schema. */
	public SqliteForeignKeyCheckBuilder table(String value) {
		requireName(value, "table");
		table = value;
		return this;
	}

	/**
	 * Selects the model's complete schema/table identity; an absent schema is
	 * ambiguous.
	 */
	public SqliteForeignKeyCheckBuilder table(Table value) {
		Objects.requireNonNull(value, "table");
		requireName(value.getName(), "table");
		requireName(value.getSchemaName(), "table schema");
		schema = value.getSchemaName();
		table = value.getName();
		return this;
	}

	public SqliteForeignKeyCheckBuilder allTables() {
		table = null;
		return this;
	}

	public String build() {
		if (!dialect.supportsForeignKeyCheck())
			throw new IllegalArgumentException(
					"SQLite foreign_key_check requires SQLite 3.7.16 or later; resolve a dialect with a known revision");
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("PRAGMA ").name(schema)._add(".foreign_key_check");
		if (table != null)
			builder._add("(").name(table)._add(")");
		return builder.toString();
	}

	private void requireName(String value, String kind) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Foreign key check requires a nonempty " + kind + " name");
	}
}
