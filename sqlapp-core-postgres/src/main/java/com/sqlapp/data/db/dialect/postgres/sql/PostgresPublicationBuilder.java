/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL logical-replication publication SQL builder.
 */
public class PostgresPublicationBuilder {
	private static final Set<String> PUBLISH_OPERATIONS =
			Set.of("insert", "update", "delete", "truncate");
	private static final Set<String> GENERATED_COLUMN_MODES =
			Set.of("none", "stored");

	private final Dialect dialect;
	private final String publicationName;
	private final List<PublishedTable> tables = new ArrayList<>();
	private final Set<String> publishOperations = new LinkedHashSet<>();
	private Boolean publishViaPartitionRoot;
	private String generatedColumnMode;
	private boolean allTables;

	public PostgresPublicationBuilder(Dialect dialect, String publicationName) {
		this.dialect = Objects.requireNonNull(dialect, "dialect");
		this.publicationName = require(publicationName, "publicationName");
	}

	public PostgresPublicationBuilder allTables() {
		this.allTables = true;
		return this;
	}

	public PostgresPublicationBuilder table(String tableName,
			String... columnNames) {
		return table(null, tableName, false, null, columnNames);
	}

	public PostgresPublicationBuilder table(String schemaName, String tableName,
			boolean only, String rowFilter, String... columnNames) {
		PublishedTable table = new PublishedTable(schemaName,
				require(tableName, "tableName"), only, rowFilter);
		if (columnNames != null) {
			for (String columnName : columnNames) {
				table.columns.add(require(columnName, "columnName"));
			}
		}
		tables.add(table);
		return this;
	}

	public PostgresPublicationBuilder publish(String... operations) {
		if (operations == null || operations.length == 0) {
			throw new IllegalArgumentException(
					"At least one publish operation is required.");
		}
		for (String operation : operations) {
			String normalized = require(operation, "publish operation")
					.toLowerCase(Locale.ROOT);
			if (!PUBLISH_OPERATIONS.contains(normalized)) {
				throw new IllegalArgumentException(
						"Unsupported publish operation: " + operation);
			}
			publishOperations.add(normalized);
		}
		return this;
	}

	public PostgresPublicationBuilder publishGeneratedColumns(String value) {
		checkPostgres18();
		String normalized = require(value, "publishGeneratedColumns")
				.toLowerCase(Locale.ROOT);
		if (!GENERATED_COLUMN_MODES.contains(normalized)) {
			throw new IllegalArgumentException(
					"publishGeneratedColumns must be none or stored.");
		}
		this.generatedColumnMode = normalized;
		return this;
	}

	public PostgresPublicationBuilder publishViaPartitionRoot(boolean value) {
		this.publishViaPartitionRoot = value;
		return this;
	}

	public String create() {
		if (allTables && !tables.isEmpty()) {
			throw new IllegalArgumentException(
					"FOR ALL TABLES cannot be combined with individual tables.");
		}
		StringBuilder builder = new StringBuilder("CREATE PUBLICATION ")
				.append(dialect.quote(publicationName));
		if (allTables) {
			builder.append(" FOR ALL TABLES");
		} else if (!tables.isEmpty()) {
			builder.append(" FOR TABLE ");
			appendTables(builder);
		}
		appendParameters(builder, " WITH (");
		return builder.toString();
	}

	public String alterParameters() {
		if (!tables.isEmpty() || allTables) {
			throw new IllegalArgumentException(
					"ALTER parameter SQL cannot contain publication tables.");
		}
		if (!hasParameters()) {
			throw new IllegalArgumentException(
					"At least one publication parameter is required.");
		}
		StringBuilder builder = new StringBuilder("ALTER PUBLICATION ")
				.append(dialect.quote(publicationName));
		appendParameters(builder, " SET (");
		return builder.toString();
	}

	private void appendTables(StringBuilder builder) {
		for (int i = 0; i < tables.size(); i++) {
			if (i > 0) {
				builder.append(", ");
			}
			PublishedTable table = tables.get(i);
			if (table.only) {
				builder.append("ONLY ");
			}
			appendQualifiedName(builder, table.schemaName, table.tableName);
			if (!table.columns.isEmpty()) {
				builder.append(" (");
				for (int j = 0; j < table.columns.size(); j++) {
					if (j > 0) {
						builder.append(", ");
					}
					builder.append(dialect.quote(table.columns.get(j)));
				}
				builder.append(")");
			}
			if (!CommonUtils.isEmpty(table.rowFilter)) {
				builder.append(" WHERE (").append(table.rowFilter).append(")");
			}
		}
	}

	private void appendParameters(StringBuilder builder, String prefix) {
		if (!hasParameters()) {
			return;
		}
		builder.append(prefix);
		List<String> parameters = new ArrayList<>();
		if (!publishOperations.isEmpty()) {
			parameters.add("publish = "
					+ sqlString(String.join(", ", publishOperations)));
		}
		if (generatedColumnMode != null) {
			parameters.add("publish_generated_columns = "
					+ generatedColumnMode);
		}
		if (publishViaPartitionRoot != null) {
			parameters.add("publish_via_partition_root = "
					+ publishViaPartitionRoot);
		}
		builder.append(String.join(", ", parameters)).append(")");
	}

	private boolean hasParameters() {
		return !publishOperations.isEmpty() || generatedColumnMode != null
				|| publishViaPartitionRoot != null;
	}

	private void appendQualifiedName(StringBuilder builder, String schemaName,
			String tableName) {
		if (!CommonUtils.isEmpty(schemaName)) {
			builder.append(dialect.quote(schemaName)).append(".");
		}
		builder.append(dialect.quote(tableName));
	}

	private void checkPostgres18() {
		if (dialect.compareTo(DialectHolder.postgreSQL180) < 0) {
			throw new IllegalArgumentException(
					"publish_generated_columns requires PostgreSQL 18 or later.");
		}
	}

	private String sqlString(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private String require(String value, String name) {
		if (CommonUtils.isEmpty(value)) {
			throw new IllegalArgumentException(name + " must not be empty.");
		}
		return value;
	}

	private static final class PublishedTable {
		private final String schemaName;
		private final String tableName;
		private final boolean only;
		private final String rowFilter;
		private final List<String> columns = new ArrayList<>();

		private PublishedTable(String schemaName, String tableName, boolean only,
				String rowFilter) {
			this.schemaName = schemaName;
			this.tableName = tableName;
			this.only = only;
			this.rowFilter = rowFilter;
		}
	}
}
