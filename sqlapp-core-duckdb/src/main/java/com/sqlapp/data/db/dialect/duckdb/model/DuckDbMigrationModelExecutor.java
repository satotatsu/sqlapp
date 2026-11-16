/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.model;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelSource;
import com.sqlapp.jdbc.bulk.AbstractMigrationModelExecutor;

/**
 * Builds models inside DuckDB. Tables are replaced with
 * {@code CREATE OR REPLACE TABLE ... AS}, views with
 * {@code CREATE OR REPLACE VIEW ... AS}; file sources are read in place by the
 * DuckDB file readers.
 */
public final class DuckDbMigrationModelExecutor extends AbstractMigrationModelExecutor {
	private static final String EXISTING_TYPE_SQL = "SELECT table_type FROM information_schema.tables"
			+ " WHERE table_catalog = current_database() AND lower(table_schema) = lower(coalesce(?, current_schema()))"
			+ " AND lower(table_name) = lower(?)";

	public DuckDbMigrationModelExecutor(final Dialect dialect) {
		super(dialect);
	}

	@Override
	public void materialize(final Connection connection, final MigrationModel model, final String selectSql)
			throws SQLException {
		final String relation = relation(model);
		if (model.schemaName() != null) {
			execute(connection, "CREATE SCHEMA IF NOT EXISTS " + identifier(model.schemaName()), Map.of());
		}
		// CREATE OR REPLACE cannot change an existing relation between table and view.
		final String existing = existingType(connection, model);
		final boolean table = model.materialization() == MigrationModel.Materialization.TABLE;
		if (existing != null) {
			final boolean existingView = "VIEW".equalsIgnoreCase(existing);
			if (table && existingView) {
				execute(connection, "DROP VIEW " + relation, Map.of());
			} else if (!table && !existingView) {
				execute(connection, "DROP TABLE " + relation, Map.of());
			}
		}
		execute(connection, "CREATE OR REPLACE " + (table ? "TABLE " : "VIEW ") + relation + " AS\n" + selectSql,
				model.parameters());
	}

	private static String existingType(final Connection connection, final MigrationModel model) throws SQLException {
		try (var statement = connection.prepareStatement(EXISTING_TYPE_SQL)) {
			statement.setString(1, model.schemaName());
			statement.setString(2, model.name());
			try (var resultSet = statement.executeQuery()) {
				return resultSet.next() ? resultSet.getString(1) : null;
			}
		}
	}

	@Override
	protected String fileRelation(final MigrationModelSource source) {
		final String path = "'" + source.file().replace("'", "''") + "'";
		final String format = source.format() != null ? source.format().toLowerCase(Locale.ROOT)
				: extension(source.file());
		return switch (format) {
		case "parquet" -> "read_parquet(" + path + ")";
		case "csv" -> "read_csv(" + path + ")";
		case "tsv" -> "read_csv(" + path + ", delim = '\\t')";
		case "json", "jsonl", "ndjson" -> "read_json_auto(" + path + ")";
		default -> throw new IllegalArgumentException("Unsupported file format for source " + source.name() + ": "
				+ format + ". Specify format: parquet, csv, tsv or json.");
		};
	}

	private static String extension(final String file) {
		String name = file.toLowerCase(Locale.ROOT);
		for (final String compression : new String[] { ".gz", ".zst" }) {
			if (name.endsWith(compression)) {
				name = name.substring(0, name.length() - compression.length());
			}
		}
		final int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1);
	}
}
