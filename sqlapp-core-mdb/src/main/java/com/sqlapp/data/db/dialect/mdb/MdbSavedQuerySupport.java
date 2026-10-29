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

	private MdbSavedQuerySupport() {
	}

	static void create(final Database database, final String name, final String definition) throws IOException {
		if (findQueryObject(database, name) != null) {
			throw new IllegalArgumentException("Access saved query already exists: " + name);
		}
		final var parsed = MdbSelectQueryParser.definition(definition);
		final String sql = parsed.sql();
		final UnionParts union = splitUnion(sql);
		validateSources(database, union == null ? sql : union.first);
		if (union != null)
			validateSources(database, union.second);
		final int objectId = nextObjectId(database);
		final int flags = union == null ? 0 : UNION_OBJECT_FLAG;
		addCatalogRow(database, objectId, name, flags);
		try {
			if (union == null) {
				addSelectRows(database, objectId, sql);
			} else {
				addUnionRows(database, objectId, union);
			}
			final var parameters = new ArrayList<Map<String, Object>>();
			for (final var parameter : parsed.parameters()) {
				final var row = queryRow(objectId, (byte) 2, null, parameter.type(), parameter.name(), null);
				row.put("LvExtra", parameter.length());
				parameters.add(row);
			}
			addRowOrder(parameters);
			if (!parameters.isEmpty())
				database.getSystemTable("MSysQueries").addRowsFromMaps(parameters);
		} catch (final IOException | RuntimeException e) {
			drop(database, name);
			throw e;
		}
	}

	static boolean drop(final Database database, final String name) throws IOException {
		final Row object = findQueryObject(database, name);
		if (object == null) {
			return false;
		}
		final int objectId = object.getInt("Id");
		deleteRows(database.getSystemTable("MSysQueries"), "ObjectId", objectId);
		deleteRows(database.getSystemTable("MSysObjects"), "Id", objectId);
		return true;
	}

	private static void addSelectRows(final Database database, final int objectId, final String sql)
			throws IOException {
		final var select = MdbSelectQueryParser.select(sql);
		final List<Map<String, Object>> rows = new ArrayList<>();
		rows.add(queryRow(objectId, TYPE_ATTRIBUTE, null, (short) 1, null, null));
		short selectFlags = 0;
		if (select.distinct()) {
			selectFlags |= 0x02;
		}
		final String columns = select.columns();
		if ("*".equals(columns)) {
			selectFlags |= 0x01;
		}
		rows.add(queryRow(objectId, FLAG_ATTRIBUTE, null, selectFlags, null, null));
		for (final var table : select.tables())
			rows.add(queryRow(objectId, TABLE_ATTRIBUTE, null, null, table.name(), table.alias()));
		for (final var join : select.joins()) {
			rows.add(queryRow(objectId, (byte) 7, join.condition(), join.type(), join.left(), join.right()));
		}
		if (!"*".equals(columns)) {
			for (final String column : splitComma(columns)) {
				rows.add(queryRow(objectId, COLUMN_ATTRIBUTE, column.trim(), (short) 0, null, null));
			}
		}
		if (select.where() != null) {
			rows.add(queryRow(objectId, WHERE_ATTRIBUTE, select.where(), null, null, null));
		}
		if (select.group() != null)
			for (final String group : splitComma(select.group()))
				rows.add(queryRow(objectId, (byte) 9, group, null, null, null));
		if (select.having() != null)
			rows.add(queryRow(objectId, (byte) 10, select.having(), null, null, null));
		if (select.order() != null) {
			for (String order : splitComma(select.order())) {
				order = order.trim();
				final boolean descending = order.toUpperCase(Locale.ROOT).endsWith(" DESC");
				if (descending) {
					order = order.substring(0, order.length() - 5).trim();
				}
				rows.add(queryRow(objectId, ORDER_ATTRIBUTE, order, null, descending ? "D" : null, null));
			}
		}
		addRowOrder(rows);
		database.getSystemTable("MSysQueries").addRowsFromMaps(rows);
	}

	private static void addUnionRows(final Database database, final int objectId, final UnionParts union)
			throws IOException {
		final List<Map<String, Object>> rows = new ArrayList<>();
		rows.add(queryRow(objectId, TYPE_ATTRIBUTE, null, (short) 9, null, null));
		rows.add(queryRow(objectId, FLAG_ATTRIBUTE, null, (short) (union.all ? 1 : 3), null, null));
		rows.add(queryRow(objectId, TABLE_ATTRIBUTE, union.first, null, null, UNION_PART1));
		rows.add(queryRow(objectId, TABLE_ATTRIBUTE, union.second, null, null, UNION_PART2));
		addRowOrder(rows);
		database.getSystemTable("MSysQueries").addRowsFromMaps(rows);
	}

	private static void addRowOrder(final List<Map<String, Object>> rows) {
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).put("Order", new byte[] { 0, 0, (byte) (i >>> 8), (byte) i });
		}
	}

	private static Map<String, Object> queryRow(final int objectId, final byte attribute, final String expression,
			final Short flag, final String name1, final String name2) {
		final Map<String, Object> row = new LinkedHashMap<>();
		row.put("ObjectId", objectId);
		row.put("Attribute", attribute);
		row.put("Expression", expression);
		row.put("Flag", flag);
		row.put("Name1", name1);
		row.put("Name2", name2);
		return row;
	}

	private static void addCatalogRow(final Database database, final int objectId, final String name, final int flags)
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

	private static int queryParentId(final Database database) throws IOException {
		for (final Row row : database.getSystemTable("MSysObjects")) {
			if ("Tables".equalsIgnoreCase(row.getString("Name")) && Short.valueOf((short) 3).equals(row.get("Type"))) {
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

	private static Row findQueryObject(final Database database, final String name) throws IOException {
		for (final Row row : database.getSystemTable("MSysObjects")) {
			if (QUERY_OBJECT_TYPE == row.getShort("Type") && name.equalsIgnoreCase(row.getString("Name"))) {
				return row;
			}
		}
		return null;
	}

	private static void deleteRows(final Table table, final String column, final Object value) throws IOException {
		final Cursor cursor = table.getDefaultCursor();
		cursor.beforeFirst();
		while (cursor.findNextRow(table.getColumn(column), value)) {
			cursor.deleteCurrentRow();
		}
	}

	private static UnionParts splitUnion(final String sql) {
		final int position = MdbSelectQueryParser.keyword(sql, "UNION");
		if (position < 0)
			return null;
		String second = sql.substring(position + 5).strip();
		final boolean all = second.matches("(?is)^ALL\\s+.*");
		if (all)
			second = second.replaceFirst("(?is)^ALL\\s+", "");
		return new UnionParts(sql.substring(0, position).strip(), second, all);
	}

	private static List<String> splitComma(final String value) {
		return MdbSelectQueryParser.splitComma(value);
	}

	private static void validateSources(final Database database, final String sql) throws IOException {
		for (final var table : MdbSelectQueryParser.select(sql).tables()) {
			if (database.getTable(table.name()) == null)
				throw new IllegalArgumentException("Saved query source table not found: " + table.name());
		}
	}

	private record UnionParts(String first, String second, boolean all) {
	}
}
