/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import io.github.spannm.jackcess.Column;
import io.github.spannm.jackcess.DataType;
import io.github.spannm.jackcess.Table;
import io.github.spannm.jackcess.impl.ColumnImpl;

/**
 * Verifies a fresh rebuild in the same traversal/insertion order as the writer.
 */
final class MdbDataVerification {
	private MdbDataVerification() {
	}

	static void verify(final Table source, final Table target, final Map<String, String> mapping,
			final List<String> issues) throws IOException {
		if (source == null)
			return;
		final Map<String, Column> originalColumns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (final Column column : source.getColumns())
			originalColumns.put(column.getName(), column);
		final Map<String, String> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		if (mapping != null)
			names.putAll(mapping);
		final Map<Column, Column> copied = new java.util.LinkedHashMap<>();
		for (final Column column : target.getColumns()) {
			final Column original = originalColumns.get(names.getOrDefault(column.getName(), column.getName()));
			if (original == null || column.isCalculated())
				continue;
			copied.put(column, original);
			if (original.isAutoNumber() && column.isAutoNumber() && original.getType() == DataType.LONG
					&& column.getType() == DataType.LONG) {
				// Jackcess 5.2.0 exposes no public seed API. Its LONG generator reads
				// the persisted table counter, including deleted/empty-table history.
				final Object expected = ((ColumnImpl) original).getAutoNumberGenerator().getLast();
				final Object actual = ((ColumnImpl) column).getAutoNumberGenerator().getLast();
				if (!Objects.equals(expected, actual))
					issues.add("AutoNumber state differs: " + target.getName() + "." + column.getName()
							+ "; deleted-value history cannot be preserved by the current writer");
			}
		}
		final var originals = source.iterator();
		final var rebuilt = target.iterator();
		long row = 0;
		while (originals.hasNext() && rebuilt.hasNext()) {
			final var expected = originals.next();
			final var actual = rebuilt.next();
			row++;
			for (final var entry : copied.entrySet()) {
				if (!equivalent(expected.get(entry.getValue().getName()), actual.get(entry.getKey().getName()),
						entry.getKey().getType())) {
					// Do not put user data into exception messages.
					issues.add("Copied value differs: " + target.getName() + "." + entry.getKey().getName()
							+ " at copied row " + row);
					return;
				}
			}
		}
		if (originals.hasNext() || rebuilt.hasNext())
			issues.add("Copied row traversal differs: " + target.getName());
	}

	private static boolean equivalent(final Object expected, final Object actual, final DataType type) {
		if (expected == null || actual == null)
			return expected == actual;
		if (expected instanceof byte[] left && actual instanceof byte[] right)
			return Arrays.equals(left, right);
		if (type == DataType.GUID) {
			try {
				return guid(expected).equals(guid(actual));
			} catch (final IllegalArgumentException e) {
				return false;
			}
		}
		if (actual instanceof Number) {
			if ((expected instanceof Float || expected instanceof Double)
					&& (actual instanceof Float || actual instanceof Double)) {
				return Double.compare(((Number) expected).doubleValue(), ((Number) actual).doubleValue()) == 0;
			}
			try {
				return new BigDecimal(expected.toString()).compareTo(new BigDecimal(actual.toString())) == 0;
			} catch (final NumberFormatException e) {
				return false;
			}
		}
		if (actual instanceof String && (expected instanceof Number || expected instanceof Boolean))
			return expected.toString().equals(actual);
		return Objects.equals(expected, actual);
	}

	private static UUID guid(final Object value) {
		return UUID.fromString(value.toString().replace("{", "").replace("}", ""));
	}
}
