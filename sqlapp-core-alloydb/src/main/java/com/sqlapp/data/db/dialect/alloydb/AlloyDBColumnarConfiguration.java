/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;

/** Explicit planning for the instance-wide persistent columnar relation flag. */
public final class AlloyDBColumnarConfiguration {
	public static final String RELATIONS = "google_columnar_engine.relations";
	public static final String COLUMNS = "alloydb.columnar.columns";
	private AlloyDBColumnarConfiguration() { }

	/** Select named columns; identifiers follow the columnar flag grammar, not SQL quoting. */
	public static Table setColumns(Table table, String... columns) {
		Objects.requireNonNull(columns, "columns");
		if (columns.length == 0) throw new IllegalArgumentException("Select columns or use clear(table)");
		var names = List.copyOf(Arrays.asList(columns));
		validateColumns(table, names);
		table.getSpecifics().put(COLUMNS, String.join(",", names));
		return table;
	}

	/** Select all columns, including columns added in the future. */
	public static Table setAllColumns(Table table) {
		table.getSpecifics().put(COLUMNS, "*");
		return table;
	}

	/** Explicitly remove this relation from the persistent flag. */
	public static Table clear(Table table) {
		table.getSpecifics().put(COLUMNS, "-");
		return table;
	}

	/** Populate table specifics from already batch-read Catalog settings; performs no JDBC calls. */
	public static void read(Catalog catalog) {
		var setting = catalog.getSettings().get(RELATIONS);
		if (setting == null || setting.getValue() == null) return;
		var relations = parse(setting.getValue());
		for (var schema : catalog.getSchemas()) {
			for (var table : schema.getTables()) {
				table.getSpecifics().remove(COLUMNS);
				var value = relations.get(List.of(catalog.getName(), schema.getName(), table.getName()));
				if (value != null) table.getSpecifics().put(COLUMNS, value.columns() == null ? "*" : String.join(",", value.columns()));
			}
		}
	}

	/**
	 * Merge explicitly modeled table choices into the captured instance-wide setting.
	 * Unmodeled relations (including other databases) are preserved. No SQL is executed.
	 */
	public static Plan plan(Catalog catalog) {
		var setting = catalog.getSettings().get(RELATIONS);
		if (setting == null || setting.getValue() == null)
			throw new IllegalArgumentException("Columnar planning requires the current instance-wide " + RELATIONS
					+ " in Catalog.settings; read settings or supply an explicitly verified baseline");
		String original = setting.getValue();
		var previous = parse(original);
		var desired = new LinkedHashMap<>(previous);
		for (var schema : catalog.getSchemas()) {
			for (var table : schema.getTables()) {
				String value = table.getSpecifics().get(COLUMNS);
				if (value == null) continue;
				var identity = List.of(identifier(catalog.getName()), identifier(schema.getName()), identifier(table.getName()));
				if ("-".equals(value)) { desired.remove(identity); continue; }
				List<String> columns = "*".equals(value) ? null : columns(value);
				if (columns != null) validateColumns(table, columns);
				desired.put(identity, new Relation(identity, columns));
			}
		}
		String result = String.join(",", desired.values().stream().map(Relation::text).toList());
		if (desired.equals(previous)) return new Plan(original, original, List.of());
		var sql = new PostgresSqlBuilder(new AlloyDB15())._add("ALTER SYSTEM SET " + RELATIONS + " = ").sqlChar(result).toString();
		return new Plan(original, result, List.of(new SqlOperation(sql, SqlType.ALTER, catalog),
				new SqlOperation("SELECT pg_catalog.pg_reload_conf()")));
	}

	/** Reviewable original/desired setting and ordered, explicitly executable SQL. */
	public record Plan(String originalRelations, String relations, List<SqlOperation> sqlOperations) {
		public Plan { sqlOperations = List.copyOf(sqlOperations); }
		/** ALTER SYSTEM must run outside a transaction block. */
		public boolean requiresAutoCommit() { return !sqlOperations.isEmpty(); }
	}

	private record Relation(List<String> identity, List<String> columns) {
		String text() { return String.join(".", identity) + (columns == null ? "" : "(" + String.join(",", columns) + ")"); }
	}

	private static String identifier(String value) {
		if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*"))
			throw new IllegalArgumentException("Columnar relations flag requires an ASCII identifier without SQL quoting: " + value);
		return value;
	}

	private static List<String> columns(String text) {
		var names = Arrays.stream(text.split(",", -1)).map(String::trim).map(AlloyDBColumnarConfiguration::identifier).toList();
		if (names.stream().distinct().count() != names.size()) throw new IllegalArgumentException("Duplicate columnar columns: " + text);
		return names;
	}

	private static void validateColumns(Table table, List<String> names) {
		if (names.stream().distinct().count() != names.size()) throw new IllegalArgumentException("Duplicate columnar columns");
		for (var name : names) {
			identifier(name);
			if (table.getColumns().get(name) == null || !name.equals(table.getColumns().get(name).getName())) throw new IllegalArgumentException("Columnar column is absent from table " + table.getName() + ": " + name);
		}
	}

	private static Map<List<String>, Relation> parse(String text) {
		var result = new LinkedHashMap<List<String>, Relation>();
		int start = 0;
		int depth = 0;
		for (int i = 0; i <= text.length(); i++) {
			char c = i == text.length() ? ',' : text.charAt(i);
			if (c == '(') depth++;
			if (c == ')') depth--;
			if (depth < 0 || depth > 1) throw new IllegalArgumentException("Malformed " + RELATIONS + ": " + text);
			if (c != ',' || depth != 0) continue;
			String item = text.substring(start, i).trim();
			start = i + 1;
			if (item.isEmpty() && text.isBlank()) continue;
			int open = item.indexOf('(');
			String name = open < 0 ? item : item.substring(0, open).trim();
			var identity = Arrays.stream(name.split("\\.", -1)).map(String::trim).map(AlloyDBColumnarConfiguration::identifier).toList();
			if (identity.size() != 3) throw new IllegalArgumentException("Columnar relation requires database.schema.table: " + item);
			List<String> cols = null;
			if (open >= 0) {
				if (!item.endsWith(")")) throw new IllegalArgumentException("Malformed columnar relation: " + item);
				cols = columns(item.substring(open + 1, item.length() - 1));
			}
			if (result.putIfAbsent(identity, new Relation(identity, cols)) != null)
				throw new IllegalArgumentException("Duplicate columnar relation: " + name);
		}
		if (depth != 0) throw new IllegalArgumentException("Malformed " + RELATIONS + ": " + text);
		return result;
	}
}
