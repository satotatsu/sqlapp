/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.Table;

/** Physical options for ordinary row-organized tables, with no data access. */
final class Db2TableOptionsSql {
	private Db2TableOptionsSql() {
	}

	static void create(Table table, Db2SqlBuilder builder) {
		String type = table.getSpecifics().get("TYPE"), organization = table.getSpecifics().get("TABLEORG");
		String clustered = table.getSpecifics().get("CLUSTERED");
		if (clustered != null && !clustered.isBlank() && !"N".equals(clustered))
			throw unsupported(table);
		if ((type != null && !"T".equals(type)) || (organization != null && !"R".equals(organization)))
			throw unsupported(table);
		if ("R".equals(organization))
			builder._add(" ORGANIZE BY ROW");
		if (table.isCompression()) {
			String compression = table.getCompressionType() == null ? "ROW ADAPTIVE"
					: table.getCompressionType().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
			switch (compression) {
			case "VALUE", "VALUE COMPRESSION" -> builder._add(" COMPRESS NO VALUE COMPRESSION");
			case "ROW STATIC" -> builder._add(" COMPRESS YES STATIC");
			case "ROW ADAPTIVE" -> builder._add(" COMPRESS YES ADAPTIVE");
			case "ROW STATIC VALUE" -> builder._add(" COMPRESS YES STATIC VALUE COMPRESSION");
			case "ROW ADAPTIVE VALUE" -> builder._add(" COMPRESS YES ADAPTIVE VALUE COMPRESSION");
			default -> throw unsupported(table);
			}
		} else if (table.getSpecifics().containsKey("DB2_COMPRESSION") || table.getCompressionType() != null)
			builder._add(" COMPRESS NO");
	}

	static List<String> afterCreate(Table table) {
		List<String> options = new ArrayList<>();
		String pctfree = table.getSpecifics().get("PCTFREE");
		if (pctfree != null && !pctfree.isBlank() && !"-1".equals(pctfree)) {
			int value;
			try {
				value = Integer.parseInt(pctfree);
			} catch (NumberFormatException e) {
				throw unsupported(table);
			}
			if (value < 0 || value > 99)
				throw unsupported(table);
			options.add("PCTFREE " + value);
		}
		String append = table.getSpecifics().get("APPEND_MODE");
		if (append != null && !append.isBlank()) {
			if (!Set.of("Y", "N").contains(append))
				throw unsupported(table);
			options.add("APPEND " + ("Y".equals(append) ? "ON" : "OFF"));
		}
		String lock = table.getSpecifics().get("LOCKSIZE");
		if (lock != null && !lock.isBlank()) {
			if (!Set.of("R", "T").contains(lock))
				throw unsupported(table);
			options.add("LOCKSIZE " + ("R".equals(lock) ? "ROW" : "TABLE"));
		}
		String volatileCardinality = table.getSpecifics().get("VOLATILE");
		if (volatileCardinality != null && !volatileCardinality.isBlank()) {
			if (!"C".equals(volatileCardinality))
				throw unsupported(table);
			options.add("VOLATILE CARDINALITY");
		}
		return options;
	}

	private static IllegalArgumentException unsupported(Table table) {
		return new IllegalArgumentException(
				"Invalid or unsupported Db2 table physical options; supply valid row-table options or a complete table definition: "
						+ table.getName());
	}
}
