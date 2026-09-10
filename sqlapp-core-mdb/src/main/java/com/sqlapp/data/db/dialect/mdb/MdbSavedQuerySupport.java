/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.spannm.jackcess.Cursor;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.Row;
import io.github.spannm.jackcess.Table;

/** Writes the documented Jackcess query row model back to Access catalogs. */
final class MdbSavedQuerySupport {
	private static final short QUERY_OBJECT_TYPE = 5;
	private static final byte TYPE_ATTRIBUTE = 1;
	private static final byte FLAG_ATTRIBUTE = 3;
	private static final byte TABLE_ATTRIBUTE = 5;
	private static final byte COLUMN_ATTRIBUTE = 6;
	private static final byte WHERE_ATTRIBUTE = 8;
	private static final byte ORDER_ATTRIBUTE = 11;
	private static final int UNION_OBJECT_FLAG = 128;
	private static final String UNION_PART1 = "X7YZ_____1";
	private static final String UNION_PART2 = "X7YZ_____2";
	private static final Pattern SELECT = Pattern.compile(
			"(?is)^SELECT\\s+(DISTINCT\\s+)?(.+?)\\s+FROM\\s+"
					+ "(\\[[^]]+]|[A-Za-z0-9_$]+)"
					+ "(?:\\s+WHERE\\s+(.+?))?"
					+ "(?:\\s+ORDER\\s+BY\\s+(.+))?$"
			);

	private MdbSavedQuerySupport() {
	}

	static void create(final Database database, final String name,
			final String definition) throws IOException {
		if (findQueryObject(database, name) != null) {
			throw new IllegalArgumentException(
					"Access saved query already exists: " + name);
		}
		final String sql = stripTerminator(definition);
		final UnionParts union = splitUnion(sql);
		final int objectId = nextObjectId(database);
		final int flags = union == null ? 0 : UNION_OBJECT_FLAG;
		addCatalogRow(database, objectId, name, flags);
		try {
			if (union == null) {
				addSelectRows(database, objectId, sql);
			} else {
				addUnionRows(database, objectId, union);
			}
		} catch (final IOException | RuntimeException e) {
			drop(database, name);
			throw e;
		}
	}

	static boolean drop(final Database database, final String name)
			throws IOException {
		final Row object = findQueryObject(database, name);
		if (object == null) {
			return false;
		}
		final int objectId = object.getInt("Id");
		deleteRows(database.getSystemTable("MSysQueries"), "ObjectId",
				objectId);
		deleteRows(database.getSystemTable("MSysObjects"), "Id", objectId);
		return true;
	}

	private static void addSelectRows(final Database database,
			final int objectId, final String sql) throws IOException {
		final Matcher matcher = SELECT.matcher(sql);
		if (!matcher.matches()) {
			throw new UnsupportedOperationException(
					"Direct saved SELECT currently supports SELECT columns FROM "
							+ "one-table [WHERE ...] [ORDER BY ...]: " + sql);
		}
		final List<Map<String, Object>> rows = new ArrayList<>();
		rows.add(queryRow(objectId, TYPE_ATTRIBUTE, null, (short) 1, null,
				null));
		short selectFlags = 0;
		if (matcher.group(1) != null) {
			selectFlags |= 0x02;
		}
		final String columns = matcher.group(2).trim();
		if ("*".equals(columns)) {
			selectFlags |= 0x01;
		}
		rows.add(queryRow(objectId, FLAG_ATTRIBUTE, null, selectFlags, null,
				null));
		rows.add(queryRow(objectId, TABLE_ATTRIBUTE, null, null,
				unquote(matcher.group(3)), null));
		if (!"*".equals(columns)) {
			for (final String column : splitComma(columns)) {
				rows.add(queryRow(objectId, COLUMN_ATTRIBUTE, column.trim(),
						(short) 0, null, null));
			}
		}
		if (matcher.group(4) != null) {
			rows.add(queryRow(objectId, WHERE_ATTRIBUTE,
					matcher.group(4).trim(), null, null, null));
		}
		if (matcher.group(5) != null) {
			for (String order : splitComma(matcher.group(5))) {
				order = order.trim();
				final boolean descending = order.toUpperCase(Locale.ROOT)
						.endsWith(" DESC");
				if (descending) {
					order = order.substring(0, order.length() - 5).trim();
				}
				rows.add(queryRow(objectId, ORDER_ATTRIBUTE, order, null,
						descending ? "D" : null, null));
			}
		}
		addRowOrder(rows);
		database.getSystemTable("MSysQueries").addRowsFromMaps(rows);
	}

	private static void addUnionRows(final Database database,
			final int objectId, final UnionParts union) throws IOException {
		final List<Map<String, Object>> rows = new ArrayList<>();
		rows.add(queryRow(objectId, TYPE_ATTRIBUTE, null, (short) 9, null,
				null));
		rows.add(queryRow(objectId, FLAG_ATTRIBUTE, null,
				(short) (union.all ? 1 : 3), null, null));
		rows.add(queryRow(objectId, TABLE_ATTRIBUTE, union.first, null, null,
				UNION_PART1));
		rows.add(queryRow(objectId, TABLE_ATTRIBUTE, union.second, null, null,
				UNION_PART2));
		addRowOrder(rows);
		database.getSystemTable("MSysQueries").addRowsFromMaps(rows);
	}

	private static void addRowOrder(final List<Map<String, Object>> rows) {
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).put("Order", new byte[] { 0, 0,
					(byte) (i >>> 8), (byte) i });
		}
	}

	private static Map<String, Object> queryRow(final int objectId,
			final byte attribute, final String expression, final Short flag,
			final String name1, final String name2) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("ObjectId", objectId);
		row.put("Attribute", attribute);
		row.put("Expression", expression);
		row.put("Flag", flag);
		row.put("Name1", name1);
		row.put("Name2", name2);
		return row;
	}

	private static void addCatalogRow(final Database database,
			final int objectId, final String name, final int flags)
			throws IOException {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("Id", objectId);
		row.put("ParentId", queryParentId(database));
		row.put("Name", name);
		row.put("Type", QUERY_OBJECT_TYPE);
		row.put("DateCreate", LocalDateTime.now());
		row.put("DateUpdate", LocalDateTime.now());
		row.put("Owner", new byte[] { (byte) 0xA6, 0x33 });
		row.put("Flags", flags);
		database.getSystemTable("MSysObjects").addRowFromMap(row);
	}

	private static int queryParentId(final Database database)
			throws IOException {
		for (final Row row : database.getSystemTable("MSysObjects")) {
			if ("Tables".equalsIgnoreCase(row.getString("Name"))
					&& Short.valueOf((short) 3).equals(row.get("Type"))) {
				return row.getInt("Id");
			}
		}
		throw new IOException("Access Tables catalog object was not found");
	}

	private static int nextObjectId(final Database database) throws IOException {
		int candidate = Integer.MIN_VALUE + 1;
		final java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (final Row row : database.getSystemTable("MSysObjects")) {
			ids.add(row.getInt("Id"));
		}
		while (ids.contains(candidate)) {
			candidate++;
		}
		return candidate;
	}

	private static Row findQueryObject(final Database database,
			final String name) throws IOException {
		for (final Row row : database.getSystemTable("MSysObjects")) {
			if (QUERY_OBJECT_TYPE == row.getShort("Type")
					&& name.equalsIgnoreCase(row.getString("Name"))) {
				return row;
			}
		}
		return null;
	}

	private static void deleteRows(final Table table, final String column,
			final Object value) throws IOException {
		final Cursor cursor = table.getDefaultCursor();
		cursor.beforeFirst();
		while (cursor.findNextRow(table.getColumn(column), value)) {
			cursor.deleteCurrentRow();
		}
	}

	private static String stripTerminator(final String sql) {
		String value = sql.strip();
		if (value.endsWith(";")) {
			value = value.substring(0, value.length() - 1).stripTrailing();
		}
		return value;
	}

	private static UnionParts splitUnion(final String sql) {
		final Matcher matcher = Pattern.compile("(?is)^(.+)\\s+UNION(\\s+ALL)?\\s+(.+)$")
				.matcher(sql);
		return matcher.matches()
				? new UnionParts(matcher.group(1).trim(), matcher.group(3).trim(),
						matcher.group(2) != null)
				: null;
	}

	private static List<String> splitComma(final String value) {
		final List<String> result = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < value.length(); i++) {
			final char ch = value.charAt(i);
			if (ch == '(') {
				depth++;
			} else if (ch == ')') {
				depth--;
			} else if (ch == ',' && depth == 0) {
				result.add(value.substring(start, i));
				start = i + 1;
			}
		}
		result.add(value.substring(start));
		return result;
	}

	private static String unquote(final String identifier) {
		return identifier.startsWith("[") && identifier.endsWith("]")
				? identifier.substring(1, identifier.length() - 1)
				: identifier;
	}

	private record UnionParts(String first, String second, boolean all) {
	}
}
