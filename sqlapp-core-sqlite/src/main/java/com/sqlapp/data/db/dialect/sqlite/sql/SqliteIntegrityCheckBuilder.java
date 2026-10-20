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

	public String build() {
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("PRAGMA ").name(schema)._add(quick ? ".quick_check" : ".integrity_check");
		if (maxErrors != null)
			builder._add("(" + maxErrors + ")");
		return builder.toString();
	}
}
