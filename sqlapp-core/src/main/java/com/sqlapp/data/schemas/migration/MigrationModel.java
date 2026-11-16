/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A SELECT-defined transformation node. The SQL is a normal sqlapp
 * comment-template; other models and sources are referenced with
 * <code>/*$ref('name')*&#47;placeholder</code> and
 * <code>/*$source('name')*&#47;placeholder</code>.
 *
 * @see MigrationModelReferences
 */
public record MigrationModel(String name, String schemaName, String sql, Materialization materialization,
		Map<String, Object> parameters) {

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
	}

	public MigrationModel(final String name, final String sql, final Materialization materialization) {
		this(name, null, sql, materialization, Map.of());
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
		TABLE
	}
}
