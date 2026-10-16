/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Table;

/** Explicit options for authoring simple index-organized tables. */
final class OracleIotOptions {
	static final String PREFIX = "oracle.table.iot.";
	private OracleIotOptions() { }
	static boolean modeled(String key) { return key.startsWith(PREFIX); }
	static void validate(Table table) {
		for (String key : table.getSpecifics().keySet()) if (modeled(key)
				&& !java.util.Set.of(PREFIX + "pctThreshold", PREFIX + "including", PREFIX + "mapping", PREFIX + "compressionPrefixLength", PREFIX + "overflow", PREFIX + "overflowTableSpace").contains(key))
			throw new IllegalArgumentException("Unknown IOT option: " + key);
		if (!"INDEX".equals(table.getSpecifics().get("oracle.table.organization")) && table.getSpecifics().keySet().stream().anyMatch(OracleIotOptions::modeled))
			throw new IllegalArgumentException("IOT options require ORGANIZATION INDEX");
		String overflow = table.getSpecifics().get(PREFIX + "overflow"), mapping = table.getSpecifics().get(PREFIX + "mapping");
		for (String flag : java.util.Arrays.asList(overflow, mapping)) if (flag != null && !"true".equals(flag) && !"false".equals(flag)) throw new IllegalArgumentException("IOT flags must be true or false");
		if (!"true".equals(overflow) && (table.getSpecifics().containsKey(PREFIX + "including") || table.getSpecifics().containsKey(PREFIX + "overflowTableSpace")))
			throw new IllegalArgumentException("INCLUDING and overflowTableSpace require overflow=true");
		String including = table.getSpecifics().get(PREFIX + "including");
		var primary = table.getConstraints().getPrimaryKeyConstraint();
		if (including != null && (table.getColumns().get(including) == null || primary == null || primary.getColumns().stream().anyMatch(column -> including.equals(column.getName()))))
			throw new IllegalArgumentException("IOT INCLUDING must name an existing non-primary-key column");
		number(table, "pctThreshold", 1, 50);
		number(table, "compressionPrefixLength", 1, primary == null ? 0 : primary.getColumns().size() - 1);
	}
	private static void number(Table table, String option, int min, int max) {
		String value = table.getSpecifics().get(PREFIX + option); if (value == null) return;
		try { int number = Integer.parseInt(value); if (number >= min && number <= max) return; }
		catch (NumberFormatException ignored) { /* Report the same actionable validation error. */ }
		throw new IllegalArgumentException("IOT " + option + " must be an integer in " + min + ".." + max);
	}
	static void add(Table table, OracleSqlBuilder builder) {
		String threshold = table.getSpecifics().get(PREFIX + "pctThreshold"), compression = table.getSpecifics().get(PREFIX + "compressionPrefixLength");
		if (threshold != null) builder.lineBreak()._add("PCTTHRESHOLD ")._add(threshold);
		if ("true".equals(table.getSpecifics().get(PREFIX + "mapping"))) builder.lineBreak()._add("MAPPING TABLE");
		if (compression != null) builder.lineBreak()._add("COMPRESS ")._add(compression);
	}
	static void addOverflow(Table table, OracleSqlBuilder builder) {
		String including = table.getSpecifics().get(PREFIX + "including");
		if (including != null) builder.lineBreak()._add("INCLUDING ").name(including);
		if ("true".equals(table.getSpecifics().get(PREFIX + "overflow"))) {
			builder.lineBreak()._add("OVERFLOW");
			String space = table.getSpecifics().get(PREFIX + "overflowTableSpace");
			if (space != null) builder.space()._add("TABLESPACE ").name(space);
		}
	}
}
