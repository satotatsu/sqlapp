/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql;

import java.util.Map;
import java.util.Locale;

/** Interpretation of physical signed TINYINT(1) columns in the MySQL family. */
public enum MySqlTinyInt1Mapping {
	/** Legacy Boolean interpretation. */
	BOOLEAN,
	/** Preserve the numeric value as TINYINT. */
	NUMERIC;

	public static final String OPTION_NAME = "mysql.tinyInt1Mapping";

	/** Validates MySQL-family options; null means no override was supplied. */
	public static MySqlTinyInt1Mapping fromOptions(final Map<String, String> options) {
		MySqlTinyInt1Mapping result = null;
		if (options == null) return null;
		for (var entry : options.entrySet()) {
			if (!OPTION_NAME.equalsIgnoreCase(entry.getKey()))
				throw new IllegalArgumentException("Unsupported MySQL-family dialect option: " + entry.getKey()
						+ ". Supported option: " + OPTION_NAME);
			if (result != null) throw new IllegalArgumentException("Duplicate dialect option: " + OPTION_NAME);
			try {
				result = valueOf(entry.getValue().trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException | NullPointerException e) {
				throw new IllegalArgumentException(OPTION_NAME + " must be BOOLEAN or NUMERIC: " + entry.getValue(), e);
			}
		}
		return result;
	}
}
