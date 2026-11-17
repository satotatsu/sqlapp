/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.model;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelColumn;
import com.sqlapp.data.schemas.migration.MigrationModelSource;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.bulk.AbstractMigrationModelExecutor;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.SqlParser;

/**
 * Builds models inside DuckDB. Tables are replaced with
 * {@code CREATE OR REPLACE TABLE ... AS}, views with
 * {@code CREATE OR REPLACE VIEW ... AS}; file sources are read in place by the
 * DuckDB file readers.
 * <p>
 * A table with declared columns is created with those types, NOT NULL
 * constraints, the primary key and a length check for character columns, and is
 * then filled with {@code INSERT ... SELECT}. DuckDB ignores declared string
 * lengths, so the length check is what rejects values that would not fit the
 * target. Values are cast to the declared types; a value that cannot be cast or
 * violates a constraint fails the model.
 * </p>
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
		if (model.hasDeclaredColumns()) {
			materializeDeclared(connection, model, relation, selectSql);
			return;
		}
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

	private void materializeDeclared(final Connection connection, final MigrationModel model, final String relation,
			final String selectSql) throws SQLException {
		checkResultColumns(model, resultColumns(connection, model, selectSql));
		final String existing = existingType(connection, model);
		if (existing != null) {
			execute(connection, ("VIEW".equalsIgnoreCase(existing) ? "DROP VIEW " : "DROP TABLE ") + relation,
					Map.of());
		}
		execute(connection, createTable(model, relation), Map.of());
		final String columns = model.columns().stream().map(column -> identifier(column.name()))
				.collect(Collectors.joining(", "));
		execute(connection, "INSERT INTO " + relation + " (" + columns + ")\nSELECT " + columns + " FROM (\n"
				+ selectSql + "\n) AS model_result", model.parameters());
	}

	private String createTable(final MigrationModel model, final String relation) {
		final StringBuilder sql = new StringBuilder("CREATE TABLE ").append(relation).append(" (");
		for (int i = 0; i < model.columns().size(); i++) {
			final MigrationModelColumn column = model.columns().get(i);
			sql.append(i == 0 ? "\n  " : ",\n  ").append(identifier(column.name())).append(' ')
					.append(type(model, column));
			if (column.notNull()) {
				sql.append(" NOT NULL");
			}
			if (column.length() != null && column.dataType().isCharacter()) {
				sql.append(" CHECK (length(").append(identifier(column.name())).append(") <= ").append(column.length())
						.append(')');
			}
		}
		if (!model.primaryKey().isEmpty()) {
			sql.append(",\n  PRIMARY KEY (")
					.append(model.primaryKey().stream().map(this::identifier).collect(Collectors.joining(", ")))
					.append(')');
		}
		return sql.append("\n)").toString();
	}

	private String type(final MigrationModel model, final MigrationModelColumn column) {
		final DbDataType<?> type = dialect.getDbDataTypes().getDbType(column.dataType(), column.length());
		if (type == null) {
			throw new IllegalArgumentException("DuckDB has no type for " + column.typeText() + " of column "
					+ column.name() + " in model " + model.name());
		}
		final String definition = type
				.getColumCreateDefinition(column.dataType().isCharacter() ? null : column.length(), column.scale());
		if (definition.contains("$")) {
			throw new IllegalArgumentException("Specify the precision of " + column.typeText() + " for column "
					+ column.name() + " in model " + model.name());
		}
		return definition;
	}

	/** Column labels of the model query, read without fetching rows. */
	private List<String> resultColumns(final Connection connection, final MigrationModel model, final String selectSql)
			throws SQLException {
		final List<String> labels = new ArrayList<>();
		final ParametersContext context = new ParametersContext();
		context.putAll(model.parameters());
		new JdbcQueryHandler(
				SqlParser.getInstance().parse(dialect, "SELECT * FROM (\n" + selectSql + "\n) AS model_result LIMIT 0"),
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet resultSet) {
						// No rows are fetched.
					}
				}) {
			@Override
			protected void handleResultSet(final ExResultSet resultSet) throws SQLException {
				final ResultSetMetaData metadata = resultSet.getNativeObject().getMetaData();
				for (int i = 1; i <= metadata.getColumnCount(); i++) {
					labels.add(metadata.getColumnLabel(i));
				}
			}
		}.execute(connection, context);
		return labels;
	}

	private static void checkResultColumns(final MigrationModel model, final List<String> actual) {
		final List<String> missing = new ArrayList<>();
		for (final MigrationModelColumn column : model.columns()) {
			if (actual.stream().noneMatch(label -> label.equalsIgnoreCase(column.name()))) {
				missing.add(column.name());
			}
		}
		final List<String> undeclared = actual.stream()
				.filter(label -> model.columns().stream().noneMatch(column -> column.name().equalsIgnoreCase(label)))
				.toList();
		final List<String> duplicates = actual.stream()
				.filter(label -> actual.stream().filter(other -> other.equalsIgnoreCase(label)).count() > 1).distinct()
				.toList();
		if (!missing.isEmpty() || !undeclared.isEmpty() || !duplicates.isEmpty()) {
			throw new IllegalArgumentException("Query columns of model " + model.name()
					+ " do not match the declared columns." + (missing.isEmpty() ? "" : " Missing: " + missing + ".")
					+ (undeclared.isEmpty() ? "" : " Not declared: " + undeclared + ".")
					+ (duplicates.isEmpty() ? "" : " Duplicated: " + duplicates + "."));
		}
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
