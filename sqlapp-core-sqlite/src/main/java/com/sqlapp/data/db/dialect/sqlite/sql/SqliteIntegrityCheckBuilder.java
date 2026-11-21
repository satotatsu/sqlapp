/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.Schema;

/**
 * Generates schema-wide SQLite integrity diagnostics without executing them.
 */
public class SqliteIntegrityCheckBuilder {
	private final Dialect dialect;
	private String schema = "main";
	private boolean quick;
	private Integer maxErrors;
	private String table;

	public SqliteIntegrityCheckBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		if (!(dialect instanceof Sqlite))
			throw new IllegalArgumentException("SqliteIntegrityCheckBuilder requires a SQLite dialect");
	}

	public SqliteIntegrityCheckBuilder schema(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Integrity check requires a nonempty schema name");
		schema = value;
		return this;
	}

	public SqliteIntegrityCheckBuilder schema(Schema value) {
		return schema(Objects.requireNonNull(value, "schema").getName());
	}

	/** Selects the default full integrity check. */
	public SqliteIntegrityCheckBuilder integrity() {
		quick = false;
		return this;
	}

	/**
	 * Skips UNIQUE and index/table consistency checks performed by the full check.
	 */
	public SqliteIntegrityCheckBuilder quick() {
		quick = true;
		return this;
	}

	/** Limits reported errors; omission retains SQLite's own default. */
	public SqliteIntegrityCheckBuilder maxErrors(int value) {
		if (value < 1)
			throw new IllegalArgumentException("Integrity check maxErrors must be positive");
		maxErrors = value;
		return this;
	}

	/**
	 * Checks only this table and its indexes (SQLite 3.33+). SQLite accepts either
	 * a table or an error limit, not both.
	 */
	public SqliteIntegrityCheckBuilder table(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Integrity check requires a nonempty table name without NUL characters");
		table = value;
		return this;
	}

	public String build() {
		if (table != null && !((Sqlite) dialect).supportsIntegrityCheckTable())
			throw new IllegalArgumentException("SQLite table integrity checks require SQLite 3.33 or later");
		if (table != null && maxErrors != null)
			throw new IllegalArgumentException("SQLite integrity checks accept a table or maxErrors, not both");
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("PRAGMA ").name(schema)._add(quick ? ".quick_check" : ".integrity_check");
		if (maxErrors != null)
			builder._add("(" + maxErrors + ")");
		if (table != null)
			builder._add("('")._add(table.replace("'", "''"))._add("')");
		return builder.toString();
	}
}
