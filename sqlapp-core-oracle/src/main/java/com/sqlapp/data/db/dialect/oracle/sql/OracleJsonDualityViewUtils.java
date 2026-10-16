/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.schemas.View;
import java.util.List;
import java.util.Map;
import com.sqlapp.util.JsonUtils;

/**
 * Oracle JSON relational duality view properties.
 */
public final class OracleJsonDualityViewUtils {

	public static final String JSON_RELATIONAL_DUALITY_VIEW = "JSON_RELATIONAL_DUALITY_VIEW";
	public static final String JSON_COLUMN_NAME = "JSON_COLUMN_NAME";
	public static final String ROOT_TABLE_OWNER = "ROOT_TABLE_OWNER";
	public static final String ROOT_TABLE_NAME = "ROOT_TABLE_NAME";
	public static final String ALLOW_INSERT = "ALLOW_INSERT";
	public static final String ALLOW_UPDATE = "ALLOW_UPDATE";
	public static final String ALLOW_DELETE = "ALLOW_DELETE";
	public static final String LOGICAL_REPLICATION = "LOGICAL_REPLICATION";
	public static final String TABLE_DETAILS = "DUALITY_TABLE_DETAILS";
	public static final String COLUMN_DETAILS = "DUALITY_COLUMN_DETAILS";

	/**
	 * Catalog rows keyed by catalog column names; returned data is detached from
	 * the view.
	 */
	public static List<Map<String, Object>> getTableDetails(View view) {
		return details(view, TABLE_DETAILS);
	}

	public static List<Map<String, Object>> getColumnDetails(View view) {
		return details(view, COLUMN_DETAILS);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> details(View view, String key) {
		String value = view.getSpecifics().get(key);
		if (value == null)
			return List.of();
		List<?> rows = JsonUtils.fromJsonString(value, List.class);
		if (rows == null || rows.stream().anyMatch(row -> !(row instanceof Map))) {
			throw new IllegalArgumentException("Invalid duality detail metadata: " + key);
		}
		return (List<Map<String, Object>>) (List<?>) List.copyOf(rows);
	}

	private OracleJsonDualityViewUtils() {
	}

	public static boolean isJsonRelationalDualityView(final View view) {
		return Boolean.TRUE.equals(view.getSpecifics().get(JSON_RELATIONAL_DUALITY_VIEW, Boolean.class));
	}

	public static View setJsonRelationalDualityView(final View view, final boolean value) {
		view.getSpecifics().put(JSON_RELATIONAL_DUALITY_VIEW, value);
		return view;
	}
}
