/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A SELECT-defined transformation node. The SQL is a normal sqlapp
 * comment-template; other models and sources are referenced with
 * <code>/*$ref('name')*&#47;placeholder</code> and
 * <code>/*$source('name')*&#47;placeholder</code>.
 *
 * <p>
 * A table model can declare its output columns and primary key. The engine then
 * creates the table with these columns and rejects rows that do not fit, and
 * the declared types are what the target uses. An incremental model requires
 * both and merges new rows into its table by the primary key.
 * </p>
 *
 * @see MigrationModelReferences
 */
public record MigrationModel(String name, String schemaName, String sql, Materialization materialization,
		Map<String, Object> parameters, List<MigrationModelColumn> columns, List<String> primaryKey) {

	/**
	 * Template parameter that is true while an incremental model adds rows to its
	 * existing table, and false when the table is built from all rows.
	 */
	public static final String INCREMENTAL_PARAMETER = "isIncremental";

	/** Names are restricted so that they are safe as identifiers and node IDs. */
	public static final Pattern NAME_PATTERN = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$]*");

	public MigrationModel {
		requireName("Model name", name);
		if (schemaName != null && schemaName.isBlank()) {
			schemaName = null;
		}
		if (schemaName != null) {
			requireName("Model schema", schemaName);
		}
		if (sql == null || sql.isBlank()) {
			throw new IllegalArgumentException("Model sql is required: " + name);
		}
		materialization = materialization == null ? Materialization.VIEW : materialization;
		parameters = parameters == null ? Map.of()
				: java.util.Collections.unmodifiableMap(new TreeMap<>(parameters));
		columns = columns == null ? List.of() : List.copyOf(columns);
		primaryKey = primaryKey == null ? List.of() : List.copyOf(primaryKey);
		if (!columns.isEmpty() && materialization == Materialization.VIEW) {
			throw new IllegalArgumentException("Declared columns require materialization TABLE: " + name);
		}
		if (materialization == Materialization.INCREMENTAL && (columns.isEmpty() || primaryKey.isEmpty())) {
			throw new IllegalArgumentException(
					"An incremental model needs declared columns and a primary key to merge rows: " + name);
		}
		if (parameters.keySet().stream().anyMatch(INCREMENTAL_PARAMETER::equalsIgnoreCase)) {
			throw new IllegalArgumentException(
					"Parameter " + INCREMENTAL_PARAMETER + " is set by the runner and cannot be defined: " + name);
		}
		if (!primaryKey.isEmpty() && columns.isEmpty()) {
			throw new IllegalArgumentException("A primary key requires declared columns: " + name);
		}
		final Set<String> names = new HashSet<>();
		for (final MigrationModelColumn column : columns) {
			if (!names.add(column.name().toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("Duplicate column " + column.name() + " in model " + name);
			}
		}
		final Set<String> keys = new HashSet<>();
		final List<MigrationModelColumn> keyed = new ArrayList<>(columns);
		for (final String key : primaryKey) {
			if (!keys.add(key.toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("Duplicate primary key column " + key + " in model " + name);
			}
			final int index = indexOf(columns, key);
			if (index < 0) {
				throw new IllegalArgumentException(
						"Primary key column " + key + " is not a declared column of model " + name);
			}
			// Primary key columns are NOT NULL in every database.
			keyed.set(index, keyed.get(index).withNotNull());
		}
		columns = List.copyOf(keyed);
	}

	public MigrationModel(final String name, final String schemaName, final String sql,
			final Materialization materialization, final Map<String, Object> parameters) {
		this(name, schemaName, sql, materialization, parameters, List.of(), List.of());
	}

	public MigrationModel(final String name, final String sql, final Materialization materialization) {
		this(name, null, sql, materialization, Map.of());
	}

	/** Whether the output columns are declared. */
	public boolean hasDeclaredColumns() {
		return !columns.isEmpty();
	}

	private static int indexOf(final List<MigrationModelColumn> columns, final String name) {
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).name().equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	static void requireName(final String label, final String value) {
		if (value == null || !NAME_PATTERN.matcher(value).matches()) {
			throw new IllegalArgumentException(label
					+ " must start with a letter or underscore and contain only letters, digits, _ or $: " + value);
		}
	}

	public enum Materialization {
		/** Stores only the query; rows are computed when the relation is read. */
		VIEW,
		/** Replaces the relation with the complete query result on every run. */
		TABLE,
		/**
		 * Merges the query result into the existing table by primary key. The query
		 * reads its own table through a reference to itself to select the rows to
		 * add; {@link MigrationModel#INCREMENTAL_PARAMETER} tells it whether the
		 * table is being added to. Rows are never deleted.
		 */
		INCREMENTAL
	}

	/** Whether the model reads its own table through a self reference. */
	public boolean isIncremental() {
		return materialization == Materialization.INCREMENTAL;
	}
}
