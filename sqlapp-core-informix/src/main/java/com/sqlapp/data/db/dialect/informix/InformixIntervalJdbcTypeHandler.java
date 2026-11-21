/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix;

import java.sql.*;
import java.time.Duration;
import java.util.regex.Pattern;
import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes;

/**
 * Native Informix qualifiers, including HOUR/FRACTION-only intervals, never
 * guessed from payload width.
 */
public final class InformixIntervalJdbcTypeHandler implements IntervalTypeHandler, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	private static final Pattern TYPE = Pattern
			.compile("INTERVAL (YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|FRACTION)(?:\\(([0-9]+)\\))? TO "
					+ "(YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|FRACTION)(?:\\(([0-9]+)\\))?");
	private final DataType group;

	public InformixIntervalJdbcTypeHandler(DataType group) {
		this.group = group;
	}

	private record Layout(String declaration, StandardIntervalCodec codec, int leading, int fraction,
			boolean fractionOnly) {
		IntervalValue read(String text) {
			if (fractionOnly) {
				if (text.startsWith("."))
					text = "0" + text;
				else if (text.startsWith("-."))
					text = "-0" + text.substring(1);
				else if (text.startsWith("+."))
					text = "+0" + text.substring(1);
			}
			return codec.parse(text);
		}

		StandardIntervalCodec.Result convert(Object value) {
			if (value instanceof String text && !text.startsWith("P") && !text.startsWith("p"))
				value = read(text.strip());
			else
				value = new IntervalValueConverter().convertObject(value);
			return codec.convert(value);
		}

		String text(Object value) {
			String text = convert(value).text();
			if (fractionOnly) {
				String sign = text.startsWith("-") ? "-" : "+";
				int point = text.indexOf('.');
				return sign + "." + (point < 0 ? "0" : text.substring(point + 1));
			}
			return text;
		}
	}

	private Layout layout(Column column) {
		if (column.getArrayDimension() > 0)
			return layout(elementColumn(column));
		String declaration = column.getDataTypeName();
		if (declaration == null || !declaration.strip().toUpperCase(java.util.Locale.ROOT).startsWith("INTERVAL ")) {
			int leading = column.getLength() == null ? group == DataType.INTERVAL_YEAR_TO_MONTH ? 4 : 2
					: Math.toIntExact(column.getLength());
			int fraction = column.getScale() == null ? 5 : column.getScale();
			if (leading < 1 || leading > 9 || fraction < 0 || fraction > 5)
				throw new IllegalArgumentException("Informix interval precision must be leading 1..9, fraction 0..5");
			declaration = group == DataType.INTERVAL_YEAR_TO_MONTH ? "INTERVAL YEAR(" + leading + ") TO MONTH"
					: "INTERVAL DAY(" + leading + ") TO " + (fraction == 0 ? "SECOND" : "FRACTION(" + fraction + ")");
		}
		declaration = InformixTemporalTypes.normalize(declaration);
		var match = TYPE.matcher(declaration);
		if (!match.matches())
			throw new IllegalArgumentException("Invalid Informix interval declaration");
		String first = match.group(1), last = match.group(3);
		boolean fractionOnly = first.equals("FRACTION");
		int leading = fractionOnly ? 0
				: match.group(2) == null ? first.equals("YEAR") ? 4 : 2 : Integer.parseInt(match.group(2));
		int fraction = last.equals("FRACTION") ? match.group(4) == null ? 3 : Integer.parseInt(match.group(4)) : 0;
		String start = fractionOnly ? "SECOND" : first, end = last.equals("FRACTION") ? "SECOND" : last;
		var qualifier = DataType.valueOf("INTERVAL_" + start + (start.equals(end) ? "" : "_TO_" + end));
		return new Layout(declaration, new StandardIntervalCodec(qualifier, leading, fraction), leading, fraction,
				fractionOnly);
	}

	private Column elementColumn(Column column) {
		if (column.getArrayDimension() != 1)
			throw new IllegalArgumentException("Informix interval LIST supports one dimension");
		String declaration = column.getDataTypeName();
		if (declaration == null)
			throw new IllegalArgumentException("Informix interval LIST requires its native element qualifier");
		var match = Pattern.compile("LIST\\s*\\(\\s*(INTERVAL .*)\\s*NOT NULL\\s*\\)", Pattern.CASE_INSENSITIVE)
				.matcher(declaration.strip());
		if (!match.matches())
			throw new IllegalArgumentException(
					"Only ordered Informix LIST(INTERVAL ... NOT NULL) maps to interval arrays: " + declaration);
		return column.clone().setArrayDimension(0).setDataTypeName(match.group(1).strip());
	}

	private Object arrayValue(Object value, Column column, java.util.List<String> warnings) {
		var element = elementColumn(column);
		Object[] result = (Object[]) IntervalArrays.map(value, 1, leaf -> {
			var converted = prepareInterval(leaf, element);
			for (String warning : converted.warnings())
				if (!warnings.contains(warning))
					warnings.add(warning);
			return converted.value();
		});
		if (result != null && java.util.Arrays.asList(result).contains(null))
			throw new IllegalArgumentException("Informix interval LIST elements must be non-null");
		return result;
	}

	@Override
	public void validateIntervalColumn(Column column) {
		layout(column);
	}

	@Override
	public String readExpression(String quotedColumn, Column column) {
		return column.getArrayDimension() == 0 ? "CAST(" + quotedColumn + " AS VARCHAR(128))"
				: "CAST(" + quotedColumn + " AS LIST(LVARCHAR(128) NOT NULL))";
	}

	@Override
	public IntervalConversion prepareInterval(Object value, Column column) {
		if (column.getArrayDimension() > 0) {
			var warnings = new java.util.ArrayList<String>();
			return new IntervalConversion(arrayValue(value, column, warnings), warnings);
		}
		var result = layout(column).convert(value);
		return new IntervalConversion(result.value(), result.warnings().stream().map(Enum::name).toList());
	}

	@Override
	public IntervalPrecision intervalPrecision(Column column) {
		var layout = layout(column);
		return new IntervalPrecision(layout.leading(), layout.fraction());
	}

	@Override
	public Column adaptIntervalColumn(Column column, IntervalPrecision source) {
		int precision = source.leading() < 1 ? 9 : Math.min(source.leading(), 9);
		int fraction = Math.min(source.fractional(), 5);
		column.setLength(precision).setScale(fraction);
		String declaration = group == DataType.INTERVAL_YEAR_TO_MONTH ? "INTERVAL YEAR(" + precision + ") TO MONTH"
				: "INTERVAL DAY(" + precision + ") TO " + (fraction == 0 ? "SECOND" : "FRACTION(" + fraction + ")");
		if (column.getArrayDimension() > 0)
			declaration = "LIST(" + declaration + " NOT NULL)";
		column.setDataTypeName(declaration);
		column.getSpecifics().put(
				com.sqlapp.data.db.dialect.informix.metadata.InformixColumnReader.INFORMIX_NATIVE_TYPE, declaration);
		return column;
	}

	@Override
	public Object getObject(ResultSet rows, int index) throws SQLException {
		var column = new Column().setDataType(group);
		String declaration = rows.getMetaData().getColumnTypeName(index);
		if (declaration != null && declaration.toUpperCase(java.util.Locale.ROOT).startsWith("INTERVAL "))
			column.setDataTypeName(declaration);
		return getObject(rows, index, column);
	}

	@Override
	public Object getObject(ResultSet rows, int index, Column column) throws SQLException {
		if (column.getArrayDimension() > 0) {
			var array = rows.getArray(index);
			if (array == null)
				return null;
			try {
				return prepareInterval(array.getArray(), column).value();
			} finally {
				array.free();
			}
		}
		int jdbcType = rows.getMetaData().getColumnType(index);
		if (group == DataType.INTERVAL_YEAR_TO_MONTH && layout(column).leading() > 8 && jdbcType != Types.VARCHAR
				&& jdbcType != Types.CHAR && jdbcType != Types.LONGVARCHAR)
			throw new SQLException(
					"Informix YEAR(9) interval reads require the exact VARCHAR projection; native JDBC interval conversion can overflow");
		String text = rows.getString(index);
		return text == null ? null : layout(column).read(text.strip());
	}

	@Override
	public Object getObject(ResultSet rows, String label) throws SQLException {
		return getObject(rows, rows.findColumn(label));
	}

	@Override
	public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		setObject(statement, index, value, new Column().setDataType(group));
	}

	@Override
	public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		var layout = layout(column);
		if (column.getArrayDimension() > 0) {
			if (value == null)
				statement.setNull(index, Types.OTHER);
			else
				statement.setObject(index, IntervalArrays.map(prepareInterval(value, column).value(), 1, layout::text));
			return;
		}
		if (value == null)
			statement.setNull(index, Types.OTHER);
		else
			statement.setString(index, layout.text(value));
	}

	public String literal(Object value, Column column) {
		var layout = layout(column);
		if (column.getArrayDimension() > 0) {
			if (value == null)
				return "NULL";
			Object[] values = (Object[]) prepareInterval(value, column).value();
			return "LIST{" + java.util.Arrays.stream(values).map(leaf -> literal(leaf, elementColumn(column)))
					.collect(java.util.stream.Collectors.joining(",")) + "}";
		}
		return value == null ? "NULL"
				: "INTERVAL(" + layout.text(value) + ")" + layout.declaration().substring("INTERVAL".length());
	}
}
