/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import java.util.Map;
import java.util.Set;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

/**
 * Recorded text-table settings and explicit target file bindings. No file I/O.
 */
public final class HsqlTextTableSources {
	public static final String SPECIFICS_KEY = "hsql.textSource.v1";
	public static final String TABLE_TYPE_KEY = "hsql.tableType";

	private HsqlTextTableSources() {
	}

	public record Source(String definition, boolean descending) {
		public Source {
			if (definition == null || definition.isBlank() || definition.indexOf('\0') >= 0) {
				throw new IllegalArgumentException("HSQLDB text source requires a file and optional source properties");
			}
		}
	}

	public record Recorded(int format, String schema, String table, String definition, Boolean descending) {
	}

	public static String id(Table table) {
		return new JsonConverter().toJsonString(java.util.Arrays.asList(table.getSchemaName(), table.getName()));
	}

	public static void record(Table table, String definition, Boolean descending) {
		table.getSpecifics().put(TABLE_TYPE_KEY, "TEXT");
		table.getSpecifics().put(SPECIFICS_KEY, new JsonConverter()
				.toJsonString(new Recorded(1, table.getSchemaName(), table.getName(), definition, descending)));
	}

	public static boolean isText(Table table) {
		return "TEXT".equals(table.getSpecifics().get(TABLE_TYPE_KEY));
	}

	public static Recorded get(Table table) {
		String json = table.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return null;
		Recorded source = new JsonConverter().getObjectMapper().readerFor(Recorded.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
		if (source == null || source.format() != 1 || !java.util.Objects.equals(source.schema(), table.getSchemaName())
				|| !java.util.Objects.equals(source.table(), table.getName())) {
			throw new IllegalArgumentException("Invalid or stale HSQLDB text source: " + id(table));
		}
		return source;
	}

	public static Source resolve(Table table, Map<String, Source> bindings) {
		Recorded recorded = get(table);
		Source binding = bindings.get(id(table));
		if (binding == null
				&& (recorded == null || recorded.definition() != null && !recorded.definition().isBlank())) {
			throw new IllegalArgumentException("Supply an explicit target text source for HSQLDB table " + id(table)
					+ " using HsqlCreateTableFactory.createSql(table, bindings), or the Schema/Catalog overload");
		}
		return binding;
	}

	public static void validateBindings(Map<String, Source> bindings, Set<String> tableIds) {
		if (bindings == null)
			throw new IllegalArgumentException("HSQLDB text source bindings must not be null");
		bindings.forEach((id, source) -> {
			if (!tableIds.contains(id) || source == null) {
				throw new IllegalArgumentException("Unknown HSQLDB text table or missing binding: " + id);
			}
		});
	}
}
