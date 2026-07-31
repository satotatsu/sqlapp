/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL {@code COPY ... TO STDOUT} SQL builder.
 */
public class PostgresCopyToBuilder {
	private final Dialect dialect;
	private final String schemaName;
	private final String relationName;
	private final List<String> columns = new ArrayList<>();
	private final List<String> options = new ArrayList<>();
	private boolean materializedView;

	public PostgresCopyToBuilder(Dialect dialect, String relationName) {
		this(dialect, null, relationName);
	}

	public PostgresCopyToBuilder(Dialect dialect, String schemaName,
			String relationName) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		require(relationName, "relationName");
		this.schemaName = schemaName;
		this.relationName = relationName;
	}

	/**
	 * Marks the target as a materialized view. Direct {@code COPY TO} support for
	 * materialized views was added in PostgreSQL 18.
	 */
	public PostgresCopyToBuilder materializedView(boolean value) {
		this.materializedView = value;
		return this;
	}

	public PostgresCopyToBuilder column(String name) {
		require(name, "column");
		columns.add(name);
		return this;
	}

	public PostgresCopyToBuilder format(String value) {
		require(value, "format");
		return option("FORMAT", value);
	}

	public PostgresCopyToBuilder header(boolean value) {
		return option("HEADER", Boolean.toString(value));
	}

	public PostgresCopyToBuilder delimiter(String value) {
		require(value, "delimiter");
		options.add("DELIMITER " + sqlString(value));
		return this;
	}

	public PostgresCopyToBuilder nullString(String value) {
		Objects.requireNonNull(value, "nullString");
		options.add("NULL " + sqlString(value));
		return this;
	}

	public PostgresCopyToBuilder encoding(String value) {
		require(value, "encoding");
		options.add("ENCODING " + sqlString(value));
		return this;
	}

	public PostgresCopyToBuilder forceQuoteAll(boolean value) {
		if (value) {
			options.add("FORCE_QUOTE *");
		}
		return this;
	}

	public String build() {
		if (materializedView
				&& dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"COPY TO from a materialized view requires PostgreSQL 18 or later.");
		}
		StringBuilder builder = new StringBuilder("COPY ");
		if (!CommonUtils.isEmpty(schemaName)) {
			builder.append(dialect.quote(schemaName)).append(".");
		}
		builder.append(dialect.quote(relationName));
		if (!columns.isEmpty()) {
			builder.append(" (");
			for (int i = 0; i < columns.size(); i++) {
				if (i > 0) {
					builder.append(", ");
				}
				builder.append(dialect.quote(columns.get(i)));
			}
			builder.append(")");
		}
		builder.append(" TO STDOUT");
		if (!options.isEmpty()) {
			builder.append(" WITH (").append(String.join(", ", options))
					.append(")");
		}
		return builder.toString();
	}

	private PostgresCopyToBuilder option(String name, String value) {
		require(value, name);
		options.add(name + " " + value);
		return this;
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}
}
