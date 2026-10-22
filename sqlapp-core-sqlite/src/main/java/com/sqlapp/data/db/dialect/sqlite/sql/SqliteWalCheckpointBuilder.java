/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.Schema;

/**
 * Generates a single-database WAL checkpoint; execution and status handling
 * belong to the caller.
 */
public class SqliteWalCheckpointBuilder {
	public enum Mode {
		PASSIVE, FULL, RESTART, TRUNCATE
	}

	private final Sqlite dialect;
	private String schema = "main";
	private Mode mode = Mode.PASSIVE;

	public SqliteWalCheckpointBuilder(Dialect dialect) {
		Objects.requireNonNull(dialect, "dialect");
		if (!(dialect instanceof Sqlite sqlite))
			throw new IllegalArgumentException("SqliteWalCheckpointBuilder requires a SQLite dialect");
		this.dialect = sqlite;
	}

	public SqliteWalCheckpointBuilder schema(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("WAL checkpoint requires a nonempty schema name without NUL characters");
		schema = value;
		return this;
	}

	public SqliteWalCheckpointBuilder schema(Schema value) {
		return schema(Objects.requireNonNull(value, "schema").getName());
	}

	public SqliteWalCheckpointBuilder mode(Mode value) {
		mode = Objects.requireNonNull(value, "mode");
		return this;
	}

	public String build() {
		if (!dialect.supportsWalCheckpointModes())
			throw new IllegalArgumentException(
					"WAL checkpoint modes require SQLite 3.7.6 or later; resolve a known revision");
		if (mode == Mode.TRUNCATE && !dialect.supportsWalCheckpointTruncate())
			throw new IllegalArgumentException("WAL checkpoint TRUNCATE requires SQLite 3.8.8 or later");
		var builder = new SqliteSqlBuilder(dialect);
		builder._add("PRAGMA ").name(schema)._add(".wal_checkpoint(")._add(mode.name())._add(")");
		return builder.toString();
	}
}
