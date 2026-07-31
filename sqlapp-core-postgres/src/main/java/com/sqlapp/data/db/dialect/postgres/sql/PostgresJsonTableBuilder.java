/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * Builder for PostgreSQL 17 {@code JSON_TABLE}.
 * SQL expressions and SQL type definitions are accepted as SQL fragments;
 * identifiers and JSON paths are escaped by this builder.
 */
public class PostgresJsonTableBuilder {
	private final Dialect dialect;
	private String contextItem;
	private String path;
	private String pathName;
	private boolean errorOnError;
	private final List<JsonTableColumn> columns = new ArrayList<>();

	public PostgresJsonTableBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public PostgresJsonTableBuilder contextItem(String value) {
		this.contextItem = value;
		return this;
	}

	public PostgresJsonTableBuilder path(String value) {
		this.path = value;
		return this;
	}

	public PostgresJsonTableBuilder pathName(String value) {
		this.pathName = value;
		return this;
	}

	public PostgresJsonTableBuilder errorOnError(boolean value) {
		this.errorOnError = value;
		return this;
	}

	public PostgresJsonTableBuilder column(String name, String sqlType, String columnPath) {
		columns.add(new ValueColumn(name, sqlType, columnPath, false));
		return this;
	}

	public PostgresJsonTableBuilder existsColumn(String name, String sqlType, String columnPath) {
		columns.add(new ValueColumn(name, sqlType, columnPath, true));
		return this;
	}

	public PostgresJsonTableBuilder ordinalityColumn(String name) {
		columns.add(new OrdinalityColumn(name));
		return this;
	}

	public PostgresJsonTableBuilder nested(String nestedPath,
			Consumer<PostgresJsonTableBuilder> columnConsumer) {
		PostgresJsonTableBuilder nested = new PostgresJsonTableBuilder(dialect);
		columnConsumer.accept(nested);
		if (nested.columns.isEmpty()) {
			throw new IllegalArgumentException("Nested JSON_TABLE columns must not be empty.");
		}
		columns.add(new NestedColumn(nestedPath, nested.columns));
		return this;
	}

	public String build() {
		Dialect postgres17 = DialectHolder.postgreSQL170;
		if (dialect.compareTo(postgres17) < 0) {
			throw new IllegalArgumentException("JSON_TABLE requires PostgreSQL 17 or later.");
		}
		require(contextItem, "contextItem");
		require(path, "path");
		if (columns.isEmpty()) {
			throw new IllegalArgumentException("JSON_TABLE columns must not be empty.");
		}
		StringBuilder builder = new StringBuilder("JSON_TABLE(");
		builder.append(contextItem).append(", ").append(sqlString(path));
		if (!CommonUtils.isEmpty(pathName)) {
			builder.append(" AS ").append(dialect.quote(pathName));
		}
		builder.append(" COLUMNS (");
		appendColumns(builder, columns);
		builder.append(")");
		if (errorOnError) {
			builder.append(" ERROR ON ERROR");
		}
		return builder.append(")").toString();
	}

	private void appendColumns(StringBuilder builder, List<JsonTableColumn> values) {
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			values.get(i).append(builder);
		}
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private void require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
	}

	private interface JsonTableColumn {
		void append(StringBuilder builder);
	}

	private class OrdinalityColumn implements JsonTableColumn {
		private final String name;
		OrdinalityColumn(String name) {
			require(name, "column name");
			this.name = name;
		}
		@Override
		public void append(StringBuilder builder) {
			builder.append(dialect.quote(name)).append(" FOR ORDINALITY");
		}
	}

	private class ValueColumn implements JsonTableColumn {
		private final String name;
		private final String sqlType;
		private final String path;
		private final boolean exists;
		ValueColumn(String name, String sqlType, String path, boolean exists) {
			require(name, "column name");
			require(sqlType, "sqlType");
			this.name = name;
			this.sqlType = sqlType;
			this.path = path;
			this.exists = exists;
		}
		@Override
		public void append(StringBuilder builder) {
			builder.append(dialect.quote(name)).append(" ").append(sqlType);
			if (exists) {
				builder.append(" EXISTS");
			}
			if (!CommonUtils.isEmpty(path)) {
				builder.append(" PATH ").append(sqlString(path));
			}
		}
	}

	private class NestedColumn implements JsonTableColumn {
		private final String path;
		private final List<JsonTableColumn> columns;
		NestedColumn(String path, List<JsonTableColumn> columns) {
			require(path, "nested path");
			this.path = path;
			this.columns = List.copyOf(columns);
		}
		@Override
		public void append(StringBuilder builder) {
			builder.append("NESTED PATH ").append(sqlString(path)).append(" COLUMNS (");
			appendColumns(builder, columns);
			builder.append(")");
		}
	}
}
