/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import com.sqlapp.data.converter.StandardIntervalConverter;

/** SQL-standard qualifier with independent leading and fractional precision. */
public final class StandardIntervalType extends AbstractPrecisionScaleType<StandardIntervalType> {
	private static final long serialVersionUID = 1L;

	public StandardIntervalType(DataType type, int maximumPrecision, int maximumScale) {
		this(type, maximumPrecision, maximumScale, 32);
	}

	public StandardIntervalType(DataType type, int maximumPrecision, int maximumScale, int maximumArrayDimensions) {
		var codec = new StandardIntervalCodec(type, 2, Math.min(6, maximumScale));
		setDataType(type);
		setTypeName(type.getTypeName());
		setDefaultPrecision(2).setMaxPrecision(maximumPrecision);
		boolean seconds = type.name().endsWith("SECOND");
		setFixedPrecision(true).setFixedScale(seconds);
		setDefaultScale(seconds ? Math.min(6, maximumScale) : 0).setMaxScale(seconds ? maximumScale : 0);
		String[] units = type.name().substring("INTERVAL_".length()).split("_TO_");
		String format = "INTERVAL " + units[0] + "(" + PRECISION_REPLACE
				+ (units[0].equals("SECOND") ? "," + SCALE_REPLACE : "") + ")";
		if (units.length == 2) format += " TO " + units[1] + (seconds ? "(" + SCALE_REPLACE + ")" : "");
		setCreateFormat(format);
		setColumnTypeMatcher(codec.columnTypeMatcher());
		var converter = new StandardIntervalConverter(type);
		setConverter(converter).setFormatter(converter);
		setJdbcTypeHandler(new StandardIntervalJdbcTypeHandler(type, 1, maximumPrecision, maximumScale, maximumArrayDimensions));
		setLiteral("INTERVAL '", "' " + String.join(" TO ", units));
	}
}
