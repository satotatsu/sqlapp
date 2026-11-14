/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.util.Locale;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaProperties;

/**
 * Dialect-independent target column type: a sqlapp {@link DataType} name with
 * optional length and scale, such as {@code VARCHAR(10)}, {@code DECIMAL(12,2)}
 * or {@code TIMESTAMP}. The target dialect's SQL builder chooses the product
 * type.
 *
 * @param dataType sqlapp data type
 * @param length   optional length or precision
 * @param scale    optional scale
 */
public record BulkMigrationColumnType(DataType dataType, Long length, Integer scale) {
	private static final Pattern PATTERN = Pattern
			.compile("\\s*([A-Za-z][A-Za-z0-9_ ]*?)\\s*(?:\\(\\s*(\\d{1,18})\\s*(?:,\\s*(\\d{1,9})\\s*)?\\))?\\s*");

	/**
	 * Parses a type text and rejects unknown names or malformed lengths with the
	 * accepted form in the message.
	 */
	public static BulkMigrationColumnType parse(final String text) {
		final var matcher = text == null ? null : PATTERN.matcher(text);
		if (matcher == null || !matcher.matches()) {
			throw new IllegalArgumentException(invalid(text));
		}
		final String name = matcher.group(1).trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
		final DataType dataType;
		try {
			dataType = DataType.valueOf(name);
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(invalid(text) + " Unknown DataType: " + name, e);
		}
		if (dataType == DataType.OTHER) {
			throw new IllegalArgumentException(invalid(text) + " OTHER has no portable definition.");
		}
		final Long length = matcher.group(2) == null ? null : Long.valueOf(matcher.group(2));
		final Integer scale = matcher.group(3) == null ? null : Integer.valueOf(matcher.group(3));
		return new BulkMigrationColumnType(dataType, length, scale);
	}

	/**
	 * Replaces the column's type. The source product type name is cleared so the
	 * target dialect derives its own type from the DataType.
	 */
	public Column applyTo(final Column column) {
		column.setDataType(dataType);
		SchemaProperties.DATA_TYPE_NAME.setValue(column, null);
		column.setLength(length);
		column.setScale(scale);
		return column;
	}

	/** Canonical text, used in fingerprints and generated jobs. */
	@Override
	public String toString() {
		if (length == null) {
			return dataType.name();
		}
		return dataType.name() + "(" + length + (scale == null ? "" : "," + scale) + ")";
	}

	private static String invalid(final String text) {
		return "Invalid column type '" + text
				+ "'. Use a sqlapp DataType with optional length and scale, such as VARCHAR(10) or DECIMAL(12,2).";
	}
}
