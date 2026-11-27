/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.Schema;

/**
 * Generates schema-scoped PRAGMA optimize without executing or changing
 * connection settings.
 */
public class SqliteOptimizeBuilder {
	private final Sqlite dialect;
	private String schema = "main";
	private boolean includeAllTables;

	public SqliteOptimizeBuilder(Dialect dialect) {
		Objects.requireNonNull(dialect, "dialect");
		if (!(dialect instanceof Sqlite sqlite))
			throw new IllegalArgumentException("SqliteOptimizeBuilder requires a SQLite dialect");
		this.dialect = sqlite;
	}

	public SqliteOptimizeBuilder schema(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"SQLite optimize requires a nonempty schema name without NUL characters");
		schema = value;
		return this;
	}

	public SqliteOptimizeBuilder schema(Schema value) {
		return schema(Objects.requireNonNull(value, "schema").getName());
	}

	/**
	 * Considers tables unused by this connection while retaining a temporary
	 * analysis limit.
	 */
	public SqliteOptimizeBuilder includeAllTables(boolean value) {
		includeAllTables = value;
		return this;
	}

	public String build() {
		if (!dialect.supportsOptimize())
			throw new IllegalArgumentException("SQLite optimize requires SQLite 3.18 or later");
		if (includeAllTables && !dialect.supportsOptimizeAllTables())
			throw new IllegalArgumentException("SQLite optimize includeAllTables requires SQLite 3.46 or later");
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("PRAGMA ").name(schema)._add(".optimize");
		if (includeAllTables)
			builder._add("(0x10012)");
		return builder.toString();
	}
}
