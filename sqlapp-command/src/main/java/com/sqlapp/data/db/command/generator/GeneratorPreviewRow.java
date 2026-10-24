package com.sqlapp.data.db.command.generator;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A generated sample before JDBC conversion or database-generated values.
 * Indexes are zero-based.
 */
public record GeneratorPreviewRow(String catalogName, String schemaName, String tableName, File configFile,
		long startRowIndex, long iterateIndex, Map<String, Object> values) {
	public GeneratorPreviewRow {
		values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	/** Runtime Java types; null values have no runtime type. */
	public Map<String, String> valueTypes() {
		final Map<String, String> types = new LinkedHashMap<>();
		values.forEach((key, value) -> types.put(key, value == null ? null : value.getClass().getName()));
		return Collections.unmodifiableMap(types);
	}
}
