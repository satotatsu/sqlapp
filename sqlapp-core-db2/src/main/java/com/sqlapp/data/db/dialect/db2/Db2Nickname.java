/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.Map;
import java.util.TreeMap;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

/** Federation identity and options supplement the shared Table/Column model. */
public final class Db2Nickname {
	public static final String SPECIFICS_KEY = "db2.nickname.v1";

	private Db2Nickname() {
	}

	public record Snapshot(int format, String serverName, String remoteSchema, String remoteTable, String wrapperType,
			String cachingAllowed, Map<String, String> options, Map<String, Map<String, String>> columnOptions) {
		public Snapshot {
			if (format != 1)
				throw new IllegalArgumentException("Unsupported Db2 nickname snapshot format");
			for (String value : new String[] { serverName, remoteSchema, remoteTable, wrapperType, cachingAllowed })
				if (value != null && value.indexOf('\0') >= 0)
					throw new IllegalArgumentException("Db2 nickname metadata must not contain NUL");
			options = immutableOptions(options);
			var columns = new TreeMap<String, Map<String, String>>();
			if (columnOptions != null)
				columnOptions.forEach((column, values) -> {
					com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.identifier(column);
					columns.put(column, immutableOptions(values));
				});
			columnOptions = java.util.Collections.unmodifiableMap(columns);
		}
	}

	private static Map<String, String> immutableOptions(Map<String, String> options) {
		var result = new TreeMap<String, String>();
		if (options != null)
			options.forEach((key, value) -> {
				if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_]*") || value == null || value.indexOf('\0') >= 0)
					throw new IllegalArgumentException("Invalid Db2 nickname option");
				if (result.putIfAbsent(key.toUpperCase(java.util.Locale.ROOT), value) != null)
					throw new IllegalArgumentException("Duplicate Db2 nickname option: " + key);
			});
		return java.util.Collections.unmodifiableMap(result);
	}

	public static boolean isNickname(Table table) {
		return table.getSpecifics().containsKey(SPECIFICS_KEY);
	}

	public static Snapshot get(Table table) {
		String json = table.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			throw new IllegalArgumentException("Table is not a Db2 nickname: " + table.getName());
		Snapshot snapshot = new JsonConverter().getObjectMapper().readerFor(Snapshot.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (snapshot == null)
			throw new IllegalArgumentException("Invalid Db2 nickname snapshot");
		return snapshot;
	}

	public static void set(Table table, Snapshot snapshot) {
		if (snapshot == null)
			throw new IllegalArgumentException("Db2 nickname snapshot is required");
		table.getSpecifics().put(SPECIFICS_KEY, new JsonConverter().toJsonString(snapshot));
	}
}
