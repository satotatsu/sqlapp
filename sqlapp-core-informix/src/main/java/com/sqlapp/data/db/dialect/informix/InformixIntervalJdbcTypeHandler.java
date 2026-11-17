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

/** Native Informix qualifiers, including HOUR/FRACTION-only intervals, never guessed from payload width. */
public final class InformixIntervalJdbcTypeHandler implements IntervalTypeHandler, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	private static final Pattern TYPE = Pattern.compile(
			"INTERVAL (YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|FRACTION)(?:\\(([0-9]+)\\))? TO "
			+ "(YEAR|MONTH|DAY|HOUR|MINUTE|SECOND|FRACTION)(?:\\(([0-9]+)\\))?");
	private final DataType group;
	public InformixIntervalJdbcTypeHandler(DataType group) { this.group = group; }

	private record Layout(String declaration, StandardIntervalCodec codec, int leading, int fraction, boolean fractionOnly) {
		IntervalValue read(String text) {
			if (fractionOnly) {
				if (text.startsWith(".")) text = "0" + text;
				else if (text.startsWith("-.")) text = "-0" + text.substring(1);
				else if (text.startsWith("+.")) text = "+0" + text.substring(1);
			}
			return codec.parse(text);
		}
		StandardIntervalCodec.Result convert(Object value) {
			if (value instanceof String text && !text.startsWith("P") && !text.startsWith("p")) value = read(text);
			else value = new IntervalValueConverter().convertObject(value);
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
		if (column.getArrayDimension() > 0) throw new IllegalArgumentException("Informix interval collections require a named collection model");
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
		if (!match.matches()) throw new IllegalArgumentException("Invalid Informix interval declaration");
		String first = match.group(1), last = match.group(3);
		boolean fractionOnly = first.equals("FRACTION");
		int leading = fractionOnly ? 0 : match.group(2) == null ? first.equals("YEAR") ? 4 : 2 : Integer.parseInt(match.group(2));
		int fraction = last.equals("FRACTION") ? match.group(4) == null ? 3 : Integer.parseInt(match.group(4)) : 0;
		String start = fractionOnly ? "SECOND" : first, end = last.equals("FRACTION") ? "SECOND" : last;
		var qualifier = DataType.valueOf("INTERVAL_" + start + (start.equals(end) ? "" : "_TO_" + end));
		return new Layout(declaration, new StandardIntervalCodec(qualifier, leading, fraction), leading, fraction, fractionOnly);
	}
	@Override public void validateIntervalColumn(Column column) { layout(column); }
	@Override public IntervalConversion prepareInterval(Object value, Column column) {
		var result = layout(column).convert(value);
		return new IntervalConversion(result.value(), result.warnings().stream().map(Enum::name).toList());
	}
	@Override public IntervalPrecision intervalPrecision(Column column) {
		var layout = layout(column);
		return new IntervalPrecision(layout.leading(), layout.fraction());
	}
	@Override public Column adaptIntervalColumn(Column column, IntervalPrecision source) {
		int precision = source.leading() < 1 ? 9 : Math.min(source.leading(), 9);
		int fraction = Math.min(source.fractional(), 5);
		column.setLength(precision).setScale(fraction);
		String declaration = group == DataType.INTERVAL_YEAR_TO_MONTH ? "INTERVAL YEAR(" + precision + ") TO MONTH"
				: "INTERVAL DAY(" + precision + ") TO " + (fraction == 0 ? "SECOND" : "FRACTION(" + fraction + ")");
		column.setDataTypeName(declaration);
		column.getSpecifics().put(com.sqlapp.data.db.dialect.informix.metadata.InformixColumnReader.INFORMIX_NATIVE_TYPE, declaration);
		return column;
	}
	@Override public Object getObject(ResultSet rows, int index) throws SQLException {
		var column = new Column().setDataType(group);
		String declaration = rows.getMetaData().getColumnTypeName(index);
		if (declaration != null && declaration.toUpperCase(java.util.Locale.ROOT).startsWith("INTERVAL ")) column.setDataTypeName(declaration);
		return getObject(rows, index, column);
	}
	@Override public Object getObject(ResultSet rows, int index, Column column) throws SQLException {
		String text = rows.getString(index);
		return text == null ? null : layout(column).read(text.strip());
	}
	@Override public Object getObject(ResultSet rows, String label) throws SQLException { return getObject(rows, rows.findColumn(label)); }
	@Override public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		setObject(statement, index, value, new Column().setDataType(group));
	}
	@Override public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		var layout = layout(column);
		if (value == null) statement.setNull(index, Types.OTHER);
		else statement.setString(index, layout.text(value));
	}
	public String literal(Object value, Column column) {
		var layout = layout(column);
		return value == null ? "NULL" : "INTERVAL(" + layout.text(value) + ")" + layout.declaration().substring("INTERVAL".length());
	}
}
