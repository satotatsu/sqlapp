/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.rowiterator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;

/** Per-iterator Schema snapshot, captured before any combined source is consumed. */
final class JsonRowSchema {
	private final Table table;
	private final boolean inferColumns;
	private final Set<Column> fixedTypeColumns = Collections.newSetFromMap(new IdentityHashMap<>());

	JsonRowSchema(final Table table) {
		this.table = table;
		inferColumns = table.getColumns().isEmpty();
		for (final Column column : table.getColumns()) {
			if (column.getDataType() != null) fixedTypeColumns.add(column);
		}
	}

	static ObjectReader reader(final JsonConverter converter, final Class<?> type) {
		return converter.getObjectMapper().readerFor(type).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
	}

	Column resolve(final String name, final Function<String, Column> schemaLookup) {
		if (!inferColumns) return schemaLookup.apply(name);
		// Source keys are identities when inferring a Schema; aliases must not merge them.
		for (final Column column : table.getColumns()) {
			if (column.getName().equals(name)) return column;
		}
		if (table.getColumns().get(name) != null) {
			throw new IllegalArgumentException("Ambiguous JSON column name: " + name);
		}
		final Column column = new Column(name);
		table.getColumns().add(column);
		return column;
	}

	Object prepare(final Column column, final Object value) {
		// Row's general Number converter uses longValue for BigInteger; retain its full value here.
		final Object exact = value instanceof BigInteger integer ? new BigDecimal(integer) : value;
		if (fixedTypeColumns.contains(column)) return exact;
		if (exact instanceof Boolean) {
			if (column.getDataType() == null) column.setDataType(DataType.BOOLEAN);
		} else if (exact instanceof Integer || exact instanceof Long) {
			if (column.getDataType() == null) column.setDataType(DataType.BIGINT);
			if (column.getDataType() == DataType.DECIMAL) growDecimal(column, new BigDecimal(exact.toString()));
		} else if (exact instanceof Number) {
			if (column.getDataType() == null || column.getDataType() == DataType.INT
					|| column.getDataType() == DataType.BIGINT) {
				column.setDataType(DataType.DECIMAL).setLength(38).setScale(17);
			}
			if (column.getDataType() == DataType.DECIMAL) growDecimal(column, new BigDecimal(exact.toString()));
		} else {
			final long length = DialectUtils.getDefaultTypeLength(exact.toString());
			if (column.getDataType() == null) column.setDataType(DataType.VARCHAR).setLength(length);
			if (column.getLength() != null) column.setLength(Math.max(length, column.getLength()));
		}
		return exact;
	}

	private void growDecimal(final Column column, final BigDecimal value) {
		final int scale = Math.max(column.getScale(), Math.max(0, value.scale()));
		final long integers = Math.max(column.getLength() - column.getScale(),
				Math.max(0L, (long) value.precision() - value.scale()));
		column.setScale(scale).setLength(integers + scale);
	}
}
