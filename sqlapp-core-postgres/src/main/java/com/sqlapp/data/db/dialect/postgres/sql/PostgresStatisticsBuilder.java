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
 * PostgreSQL 18 optimizer-statistics restore and clear SQL builder.
 * Statistic values are SQL expressions so their required PostgreSQL types can
 * be preserved.
 */
public class PostgresStatisticsBuilder {
	private final Dialect dialect;

	public PostgresStatisticsBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public RestoreExpression restoreRelation(String schemaName,
			String tableName) {
		checkVersion();
		return new RestoreExpression("pg_restore_relation_stats")
				.argument("schemaname", sqlString(require(schemaName, "schemaName")))
				.argument("relname", sqlString(require(tableName, "tableName")));
	}

	public RestoreExpression restoreAttribute(String schemaName,
			String tableName, String columnName, boolean inherited) {
		checkVersion();
		return new RestoreExpression("pg_restore_attribute_stats")
				.argument("schemaname", sqlString(require(schemaName, "schemaName")))
				.argument("relname", sqlString(require(tableName, "tableName")))
				.argument("attname", sqlString(require(columnName, "columnName")))
				.argument("inherited", Boolean.toString(inherited));
	}

	public String clearRelation(String schemaName, String tableName) {
		checkVersion();
		return "SELECT pg_clear_relation_stats("
				+ sqlString(require(schemaName, "schemaName")) + ", "
				+ sqlString(require(tableName, "tableName")) + ")";
	}

	public String clearAttribute(String schemaName, String tableName,
			String columnName, boolean inherited) {
		checkVersion();
		return "SELECT pg_clear_attribute_stats("
				+ sqlString(require(schemaName, "schemaName")) + ", "
				+ sqlString(require(tableName, "tableName")) + ", "
				+ sqlString(require(columnName, "columnName")) + ", "
				+ inherited + ")";
	}

	public final class RestoreExpression {
		private final String function;
		private final List<String> arguments = new ArrayList<>();

		private RestoreExpression(String function) {
			this.function = function;
		}

		public RestoreExpression statistic(String name, String valueExpression) {
			return argument(require(name, "statistic name"),
					require(valueExpression, "statistic value"));
		}

		public RestoreExpression sourceVersion(int value) {
			if (value <= 0) {
				throw new IllegalArgumentException(
						"sourceVersion must be greater than zero.");
			}
			return argument("version", Integer.toString(value));
		}

		public String build() {
			return "SELECT " + function + "("
					+ String.join(", ", arguments) + ")";
		}

		private RestoreExpression argument(String name, String valueExpression) {
			arguments.add(sqlString(name));
			arguments.add(valueExpression);
			return this;
		}
	}

	private void checkVersion() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"Statistics restore and clear functions require PostgreSQL 18 or later.");
		}
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}
