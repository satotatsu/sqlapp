/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.model;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelColumn;
import com.sqlapp.data.schemas.migration.MigrationModelSource;
import com.sqlapp.jdbc.bulk.AbstractMigrationModelExecutor;

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
 * target. The query result is staged once in a temporary table, so the rows
 * that are checked are the rows that are inserted. A value that would change
 * when cast to its declared type (rounding, truncation, overflow) or cannot be
 * cast fails the model; conversions that change values belong in the model SQL.
 * A value that violates a constraint fails the model too.
 * </p>
 * <p>
 * An incremental model is built the same way the first time. Later runs merge
 * the staged rows into the table with {@code INSERT ... ON CONFLICT DO UPDATE}
 * on the primary key. The lineage fingerprint is kept as the table comment.
 * </p>
 */
public final class DuckDbMigrationModelExecutor extends AbstractMigrationModelExecutor {
	private static final String EXISTING_TYPE_SQL = "SELECT table_type FROM information_schema.tables"
			+ " WHERE table_catalog = current_database() AND lower(table_schema) = lower(coalesce(?, current_schema()))"
			+ " AND lower(table_name) = lower(?)";

	private static final String COMMENT_SQL = "SELECT comment FROM duckdb_tables()"
			+ " WHERE database_name = current_database() AND lower(schema_name) = lower(coalesce(?, current_schema()))"
			+ " AND lower(table_name) = lower(?)";

	/**
	 * Table comment prefix and format version of the recorded lineage fingerprint.
	 */
	private static final String FINGERPRINT_PREFIX = "sqlapp-model:1:";

	/** Holds the model result while it is checked, so the query runs once. */
	private static final String STAGE = "sqlapp_model_stage";

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
			materializeDeclared(connection, model, relation, selectSql, model.parameters());
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

	@Override
	public IncrementalState incrementalState(final Connection connection, final MigrationModel model)
			throws SQLException {
		final String existing = existingType(connection, model);
		if (existing == null) {
			return new IncrementalState(false, null);
		}
		if (!"BASE TABLE".equalsIgnoreCase(existing)) {
			return new IncrementalState(true, null);
		}
		try (var statement = connection.prepareStatement(COMMENT_SQL)) {
			statement.setString(1, model.schemaName());
			statement.setString(2, model.name());
			try (var resultSet = statement.executeQuery()) {
				final String comment = resultSet.next() ? resultSet.getString(1) : null;
				return new IncrementalState(true,
						comment != null && comment.startsWith(FINGERPRINT_PREFIX)
								&& comment.length() > FINGERPRINT_PREFIX.length()
										? comment.substring(FINGERPRINT_PREFIX.length())
										: null);
			}
		}
	}

	/**
	 * Builds the table from all rows, or merges the new rows into it by primary
	 * key: NULL and duplicated keys and values that the declared types would change
	 * are rejected on the same staged rows that are merged. The lineage fingerprint
	 * is written as the table comment in the same transaction.
	 */
	@Override
	public void materializeIncremental(final Connection connection, final MigrationModel model, final String selectSql,
			final boolean incremental, final String fingerprint) throws SQLException {
		final String relation = relation(model);
		if (model.schemaName() != null) {
			execute(connection, "CREATE SCHEMA IF NOT EXISTS " + identifier(model.schemaName()), Map.of());
		}
		final Map<String, Object> parameters = new java.util.HashMap<>(model.parameters());
		parameters.put(MigrationModel.INCREMENTAL_PARAMETER, incremental);
		if (incremental) {
			stage(connection, model, selectSql, parameters);
			try {
				checkKeys(connection, model);
				execute(connection, upsert(model, relation), Map.of());
			} catch (final SQLException | RuntimeException e) {
				dropStageAfter(connection, e);
				throw e;
			}
			dropStage(connection);
		} else {
			materializeDeclared(connection, model, relation, selectSql, parameters);
		}
		execute(connection,
				"COMMENT ON TABLE " + relation + " IS '" + (FINGERPRINT_PREFIX + fingerprint).replace("'", "''") + "'",
				Map.of());
	}

	/** Stages the query result once and checks its columns and conversions. */
	private void stage(final Connection connection, final MigrationModel model, final String selectSql,
			final Map<String, Object> parameters) throws SQLException {
		execute(connection,
				"CREATE OR REPLACE TEMP TABLE " + STAGE + " AS\nSELECT * FROM (\n" + selectSql + "\n) AS model_result",
				parameters);
		try {
			final Map<String, String> resultTypes = resultTypes(connection);
			checkResultColumns(model, new ArrayList<>(resultTypes.keySet()));
			checkLosslessConversion(connection, model, resultTypes);
		} catch (final SQLException | RuntimeException e) {
			dropStageAfter(connection, e);
			throw e;
		}
	}

	/** Rejects NULL and duplicated primary keys among the staged rows. */
	private void checkKeys(final Connection connection, final MigrationModel model) throws SQLException {
		final List<String> keys = model.primaryKey().stream().map(this::identifier).toList();
		final String keyList = String.join(", ", keys);
		final String sql = "SELECT (SELECT count(*) FROM " + STAGE + " WHERE "
				+ keys.stream().map(key -> key + " IS NULL").collect(Collectors.joining(" OR "))
				+ "), (SELECT count(*) FROM (SELECT " + keyList + " FROM " + STAGE + " WHERE "
				+ keys.stream().map(key -> key + " IS NOT NULL").collect(Collectors.joining(" AND ")) + " GROUP BY "
				+ keyList + " HAVING count(*) > 1) AS duplicated)";
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			final long nulls = resultSet.getLong(1);
			final long duplicated = resultSet.getLong(2);
			if (nulls > 0 || duplicated > 0) {
				throw new IllegalArgumentException(
						"New rows of incremental model " + model.name() + " cannot be merged by primary key "
								+ model.primaryKey() + ": " + nulls + " rows with a NULL key, " + duplicated
								+ " duplicated keys. Make the model SQL return one row per key.");
			}
		}
	}

	private String upsert(final MigrationModel model, final String relation) {
		final String columns = model.columns().stream().map(column -> identifier(column.name()))
				.collect(Collectors.joining(", "));
		final List<String> updates = model.columns().stream()
				.filter(column -> model.primaryKey().stream().noneMatch(key -> key.equalsIgnoreCase(column.name())))
				.map(column -> identifier(column.name()) + " = EXCLUDED." + identifier(column.name())).toList();
		return "INSERT INTO " + relation + " (" + columns + ")\nSELECT " + columns + " FROM " + STAGE
				+ "\nON CONFLICT ("
				+ model.primaryKey().stream().map(this::identifier).collect(Collectors.joining(", ")) + ") DO "
				+ (updates.isEmpty() ? "NOTHING" : "UPDATE SET " + String.join(", ", updates));
	}

	private void dropStageAfter(final Connection connection, final Exception failure) {
		// A failed statement can abort the transaction; keep the original error.
		try {
			dropStage(connection);
		} catch (final SQLException dropFailure) {
			failure.addSuppressed(dropFailure);
		}
	}

	private void materializeDeclared(final Connection connection, final MigrationModel model, final String relation,
			final String selectSql, final Map<String, Object> parameters) throws SQLException {
		// The query runs once; the checked rows are the inserted rows.
		stage(connection, model, selectSql, parameters);
		try {
			final String existing = existingType(connection, model);
			if (existing != null) {
				execute(connection, ("VIEW".equalsIgnoreCase(existing) ? "DROP VIEW " : "DROP TABLE ") + relation,
						Map.of());
			}
			execute(connection, createTable(model, relation), Map.of());
			final String columns = model.columns().stream().map(column -> identifier(column.name()))
					.collect(Collectors.joining(", "));
			execute(connection, "INSERT INTO " + relation + " (" + columns + ")\nSELECT " + columns + " FROM " + STAGE,
					Map.of());
		} catch (final SQLException | RuntimeException e) {
			dropStageAfter(connection, e);
			throw e;
		}
		dropStage(connection);
	}

	private void dropStage(final Connection connection) throws SQLException {
		execute(connection, "DROP TABLE IF EXISTS " + STAGE, Map.of());
	}

	/** Column names and DuckDB types of the staged query result, in order. */
	private static Map<String, String> resultTypes(final Connection connection) throws SQLException {
		final Map<String, String> types = new LinkedHashMap<>();
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery("DESCRIBE " + STAGE)) {
			while (resultSet.next()) {
				types.put(resultSet.getString("column_name"), resultSet.getString("column_type"));
			}
		}
		return types;
	}

	/**
	 * Fails when casting a value to its declared type would change it: rounding,
	 * truncation, overflow or a failed conversion. A value is kept when casting it
	 * to the declared type and back gives the same value. Only counts are reported,
	 * never the values.
	 */
	private void checkLosslessConversion(final Connection connection, final MigrationModel model,
			final Map<String, String> resultTypes) throws SQLException {
		final List<MigrationModelColumn> checked = new ArrayList<>();
		final List<String> sourceTypes = new ArrayList<>();
		final StringBuilder sql = new StringBuilder("SELECT ");
		for (final MigrationModelColumn column : model.columns()) {
			final String sourceType = resultTypes.entrySet().stream()
					.filter(entry -> entry.getKey().equalsIgnoreCase(column.name())).map(Map.Entry::getValue)
					.findFirst().orElseThrow();
			final String declaredType = type(model, column);
			if (sourceType.equalsIgnoreCase(declaredType)) {
				continue;
			}
			final String name = identifier(column.name());
			final String cast = "TRY_CAST(" + name + " AS " + declaredType + ")";
			sql.append(checked.isEmpty() ? "" : ", ").append("count(*) FILTER (WHERE ").append(name)
					.append(" IS NOT NULL AND (").append(cast).append(" IS NULL OR TRY_CAST(").append(cast)
					.append(" AS ").append(sourceType).append(") IS DISTINCT FROM ").append(name).append("))");
			checked.add(column);
			sourceTypes.add(sourceType);
		}
		if (checked.isEmpty()) {
			return;
		}
		sql.append(" FROM ").append(STAGE);
		final List<String> failures = new ArrayList<>();
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql.toString())) {
			resultSet.next();
			for (int i = 0; i < checked.size(); i++) {
				final long count = resultSet.getLong(i + 1);
				if (count > 0) {
					failures.add(checked.get(i).name() + " (" + count + " rows, " + sourceTypes.get(i) + " to "
							+ checked.get(i).typeText() + ")");
				}
			}
		}
		if (!failures.isEmpty()) {
			throw new IllegalArgumentException("Model " + model.name()
					+ " has values that change or fail when cast to the declared type: " + String.join(", ", failures)
					+ ". Convert them explicitly in the model SQL, for example with ROUND or CAST.");
		}
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
