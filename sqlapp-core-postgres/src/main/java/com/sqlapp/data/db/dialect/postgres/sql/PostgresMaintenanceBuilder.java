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
 * PostgreSQL {@code VACUUM} and {@code ANALYZE} SQL builder.
 */
public class PostgresMaintenanceBuilder {
	private final Dialect dialect;

	public PostgresMaintenanceBuilder(Dialect dialect) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
	}

	public MaintenanceExpression vacuum(String tableName) {
		return vacuum(null, tableName);
	}

	public MaintenanceExpression vacuum(String schemaName, String tableName) {
		return new MaintenanceExpression("VACUUM", schemaName, tableName);
	}

	public MaintenanceExpression analyze(String tableName) {
		return analyze(null, tableName);
	}

	public MaintenanceExpression analyze(String schemaName, String tableName) {
		return new MaintenanceExpression("ANALYZE", schemaName, tableName);
	}

	public final class MaintenanceExpression {
		private final String command;
		private final String schemaName;
		private final String tableName;
		private final List<String> columns = new ArrayList<>();
		private boolean only;

		private MaintenanceExpression(String command, String schemaName,
				String tableName) {
			this.command = command;
			this.schemaName = schemaName;
			this.tableName = require(tableName, "tableName");
		}

		/**
		 * Excludes inheritance children and partitions from the operation.
		 */
		public MaintenanceExpression only() {
			return only(true);
		}

		public MaintenanceExpression only(boolean value) {
			this.only = value;
			return this;
		}

		public MaintenanceExpression column(String value) {
			columns.add(require(value, "column"));
			return this;
		}

		public String build() {
			if (only && dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
				throw new IllegalArgumentException(
						"VACUUM/ANALYZE ONLY requires PostgreSQL 18 or later.");
			}
			StringBuilder builder = new StringBuilder(command).append(" ");
			if (only) {
				builder.append("ONLY ");
			}
			appendQualifiedName(builder, schemaName, tableName);
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
			return builder.toString();
		}
	}

	private void appendQualifiedName(StringBuilder builder, String schemaName,
			String tableName) {
		if (!CommonUtils.isEmpty(schemaName)) {
			builder.append(dialect.quote(schemaName)).append(".");
		}
		builder.append(dialect.quote(tableName));
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}
}
