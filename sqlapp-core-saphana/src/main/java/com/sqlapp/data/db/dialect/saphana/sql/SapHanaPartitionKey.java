/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.Locale;
import java.util.regex.Pattern;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Only documented, single-column partition functions are executable
 * expressions.
 */
record SapHanaPartitionKey(String function, Column column, Column valueColumn) {
	private static final Pattern FUNCTION = Pattern
			.compile("(?is)^(YEAR|MONTH|HOUR)\\s*\\(\\s*(\"(?:[^\"]|\"\")+\"|[\\p{L}_][\\p{L}\\p{N}_$#]*)\\s*\\)$");

	static SapHanaPartitionKey resolve(Table table, String name) {
		if (name == null)
			throw new IllegalArgumentException("SAP HANA partition key is required");
		var match = FUNCTION.matcher(name.trim());
		if (match.matches()) {
			String identifier = unquote(match.group(2));
			var column = table.getColumns().get(identifier);
			var literal = table.getColumns().get(name);
			if (literal != null) {
				throw new IllegalArgumentException(
						"SAP HANA function-like partition column names require explicit DDL: " + name);
			}
			if (column == null || column.getDataType() == null || !column.getDataType().isDateTime()
					|| column.getDataType() == DataType.TIME || column.getDataType() == DataType.TIME_WITH_TIMEZONE)
				throw new IllegalArgumentException(
						"SAP HANA date partition function requires an existing date/time column: " + identifier);
			if (column.getName().codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)))
				throw new IllegalArgumentException(
						"SAP HANA partition function arguments containing whitespace require explicit DDL: "
								+ identifier);
			String function = match.group(1).toUpperCase(Locale.ROOT);
			var valueColumn = new Column().setDataType("HOUR".equals(function) ? DataType.VARCHAR : DataType.INT);
			return new SapHanaPartitionKey(function, column, valueColumn);
		}
		var column = table.getColumns().get(unquote(name));
		if (column == null)
			throw new IllegalArgumentException("Unsupported SAP HANA partition expression or missing column: " + name);
		if (FUNCTION.matcher(column.getName()).matches())
			throw new IllegalArgumentException(
					"SAP HANA treats function-like partition column names as expressions; explicit DDL is required: "
							+ name);
		return new SapHanaPartitionKey(null, column, column);
	}

	void append(SapHanaSqlBuilder builder) {
		if (function != null)
			builder._add(function)._add("(");
		builder.name(column.getName());
		if (function != null)
			builder._add(")");
	}

	void appendValue(String value, SapHanaSqlBuilder builder) {
		if (function != null
				&& value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[ T][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?)?")) {
			var date = value.length() == 10 ? java.time.LocalDate.parse(value).atStartOfDay()
					: java.time.LocalDateTime.parse(value.replace(' ', 'T'));
			boolean midnight = date.toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
			boolean aligned = switch (function) {
			case "YEAR" -> date.getMonthValue() == 1 && date.getDayOfMonth() == 1 && midnight;
			case "MONTH" -> date.getDayOfMonth() == 1 && midnight;
			default -> date.getMinute() == 0 && date.getSecond() == 0 && date.getNano() == 0;
			};
			if (!aligned)
				throw new IllegalArgumentException(
						"SAP HANA " + function + " partition boundary is not aligned: " + value);
			value = switch (function) {
			case "YEAR" -> Integer.toString(date.getYear());
			case "MONTH" -> date.format(java.time.format.DateTimeFormatter.ofPattern("uuuuMM", Locale.ROOT));
			default -> date.format(java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHH", Locale.ROOT));
			};
		}
		var type = valueColumn.getDataType();
		if (type != null && (type.isCharacter() || type.isNationalCharacter() || type.isDateTime())
				&& !value.startsWith("'") && !value.matches("(?is)^(DATE|TIME|TIMESTAMP)\\s+'.*'$"))
			builder.sqlChar(value);
		else
			builder._add(value);
	}

	private static String unquote(String value) {
		return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
				? value.substring(1, value.length() - 1).replace("\"\"", "\"")
				: value;
	}
}
