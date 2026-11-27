/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one model query against small inline inputs instead of its real
 * upstream relations. Nothing is materialized.
 *
 * @param id           test identifier
 * @param model        model under test
 * @param given        fixture for every model or source the model references,
 *                     keyed by that name
 * @param expectedRows expected result rows as canonical strings in query
 *                     column order
 * @param ordered      whether row order is significant
 */
public record MigrationModelUnitTest(String id, String model, Map<String, Fixture> given,
		List<List<String>> expectedRows, boolean ordered) {

	public MigrationModelUnitTest {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("Unit test id must not be empty");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("Unit test model is required: " + id);
		}
		given = given == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(given));
		final List<List<String>> rows = new ArrayList<>();
		if (expectedRows != null) {
			for (final List<String> row : expectedRows) {
				rows.add(Collections.unmodifiableList(new ArrayList<>(row)));
			}
		}
		expectedRows = List.copyOf(rows);
	}

	/**
	 * Inline input relation: either literal rows or a query. Literal values keep
	 * only the types the engine infers from numbers, booleans and text; use
	 * {@code sql} with explicit casts when the exact type matters.
	 */
	public record Fixture(List<String> columns, List<List<Object>> rows, String sql) {

		public Fixture {
			columns = columns == null ? List.of() : List.copyOf(columns);
			final List<List<Object>> copy = new ArrayList<>();
			if (rows != null) {
				for (final List<Object> row : rows) {
					copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
				}
			}
			rows = Collections.unmodifiableList(copy);
			sql = sql == null || sql.isBlank() ? null : sql;
			if (sql != null) {
				if (!columns.isEmpty() || !rows.isEmpty()) {
					throw new IllegalArgumentException("Specify either sql or columns/rows for a fixture");
				}
			} else {
				if (columns.isEmpty()) {
					throw new IllegalArgumentException("Fixture columns are required when sql is not specified");
				}
				for (final String column : columns) {
					MigrationModel.requireName("Fixture column", column);
				}
				for (final List<Object> row : rows) {
					if (row.size() != columns.size()) {
						throw new IllegalArgumentException("Fixture row has " + row.size() + " values but "
								+ columns.size() + " columns are declared: " + row);
					}
				}
			}
		}

		public static Fixture rows(final List<String> columns, final List<List<Object>> rows) {
			return new Fixture(columns, rows, null);
		}

		public static Fixture sql(final String sql) {
			return new Fixture(null, null, sql);
		}
	}
}
