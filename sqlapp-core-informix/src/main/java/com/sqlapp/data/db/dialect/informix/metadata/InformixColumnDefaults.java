/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

/**
 * Restores SQL defaults without JDBC's lossy string trimming or keyword
 * quoting.
 */
final class InformixColumnDefaults {
	private InformixColumnDefaults() {
	}

	static String expression(Column column, String kind, String raw) {
		if (kind == null)
			return null;
		return switch (kind) {
		case "N" -> "NULL";
		case "T" -> "TODAY";
		case "U" -> "USER";
		case "S" -> "DBSERVERNAME";
		case "C" -> {
			String type = column.getDataTypeName();
			if (type == null || !type.startsWith("DATETIME "))
				throw new IllegalStateException(
						"CURRENT default requires an Informix DATETIME column: " + column.getName());
			yield "CURRENT"
					+ com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes.normalize(type).substring(8);
		}
		case "L" -> literal(column, raw);
		default -> throw new UnsupportedOperationException(
				"Unknown Informix default kind " + kind + " for " + column.getName());
		};
	}

	private static String literal(Column column, String raw) {
		final DataType type = column.getDataType();
		if (type != null && (type.isCharacter() || type == DataType.BOOLEAN)) {
			String value;
			if (raw == null && type.isCharacter())
				value = ""; // L + NULL catalog value denotes an empty string.
			else {
				int end = raw == null ? -1 : raw.indexOf('\0');
				if (end < 0)
					throw new IllegalStateException("Missing Informix string-default terminator: " + column.getName());
				value = raw.substring(0, end);
			}
			if (type == DataType.BOOLEAN && !java.util.Set.of("t", "f").contains(value))
				throw new IllegalStateException("Invalid Informix BOOLEAN default: " + column.getName());
			return quote(value);
		}
		if (raw == null || raw.indexOf(' ') < 0)
			throw new IllegalStateException("Missing Informix encoded literal default: " + column.getName());
		final String ascii = raw.substring(raw.indexOf(' ') + 1);
		final int terminator = ascii.indexOf('\0');
		final String value = (terminator < 0 ? ascii : ascii.substring(0, terminator)).strip();
		if (value.isEmpty())
			throw new IllegalStateException("Empty Informix literal default: " + column.getName());
		if (type == DataType.DATE)
			return quote(value);
		if (type != null && (type.isDateTime() || type.isInterval())) {
			String declaration = column.getDataTypeName();
			if (declaration == null || !value.matches("[+-]?(?:[0-9][0-9:. -]*|\\.[0-9]+)"))
				throw new IllegalStateException("Cannot reconstruct Informix temporal default: " + column.getName());
			declaration = com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes.normalize(declaration);
			final String keyword = type.isInterval() ? "INTERVAL" : "DATETIME";
			return keyword + "(" + value + ")" + declaration.substring(keyword.length());
		}
		if (type != null && type.isNumeric()
				&& value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[Ee][+-]?[0-9]+)?"))
			return value;
		throw new UnsupportedOperationException(
				"Cannot decode Informix literal default for " + column.getName() + " (" + type + ")");
	}

	private static String quote(String text) {
		return "'" + text.replace("'", "''") + "'";
	}
}
