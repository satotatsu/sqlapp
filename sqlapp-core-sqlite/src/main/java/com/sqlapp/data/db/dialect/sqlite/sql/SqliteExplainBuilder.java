/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;

/**
 * Generates SQLite diagnostic SQL; execution and result interpretation belong
 * to the caller.
 */
public class SqliteExplainBuilder {
	private static final Set<String> STATEMENTS = Set.of("SELECT", "WITH", "INSERT", "UPDATE", "DELETE", "REPLACE");
	private final Dialect dialect;
	private String statement;
	private boolean queryPlan = true;

	public SqliteExplainBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		if (!(dialect instanceof Sqlite))
			throw new IllegalArgumentException("SqliteExplainBuilder requires a SQLite dialect");
	}

	public SqliteExplainBuilder statement(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("EXPLAIN statement must not be empty");
		this.statement = value;
		return this;
	}

	/** Selects the default, high-level query plan. */
	public SqliteExplainBuilder queryPlan() {
		queryPlan = true;
		return this;
	}

	/** Selects SQLite virtual-machine instructions using plain EXPLAIN. */
	public SqliteExplainBuilder bytecode() {
		queryPlan = false;
		return this;
	}

	public String build() {
		if (statement == null)
			throw new IllegalArgumentException("EXPLAIN statement is required");
		var statements = dialect.createSqlSplitter().parse(statement).stream()
				.filter(part -> !part.getTextType().isComment()).toList();
		if (statements.size() != 1)
			throw new IllegalArgumentException("SQLite EXPLAIN requires exactly one SQL statement");
		String sql = statements.get(0).getText().strip();
		int end = 0;
		while (end < sql.length() && Character.isLetter(sql.charAt(end)))
			end++;
		String command = sql.substring(0, end).toUpperCase(java.util.Locale.ROOT);
		if (!STATEMENTS.contains(command))
			throw new IllegalArgumentException(
					"SQLite EXPLAIN supports SELECT, WITH, INSERT, UPDATE, DELETE or REPLACE; PRAGMA, DDL and transaction commands are not supported");
		return (queryPlan ? "EXPLAIN QUERY PLAN " : "EXPLAIN ") + sql;
	}
}
