/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import com.sqlapp.data.converter.IntervalValueConverter;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

/** Lossless sqlapp interval text for CSV, TSV and JSONL; no destination normalization. */
final class TransferIntervalCodec {
	private TransferIntervalCodec() {}

	static boolean interval(Column column) {
		return column.getDataType().getMetaTtype() == MetaType.INTERVAL;
	}

	static void validate(Column column) {
		if (!interval(column) || column.getArrayDimension() > 0)
			throw new IllegalArgumentException("File interval values require a scalar interval column: " + column.getName());
	}

	static String encode(Column column, Object value) {
		validate(column);
		if (value == null) return null;
		IntervalValue interval = value instanceof String text ? decode(column, text)
				: new IntervalValueConverter().convertObject(value);
		if (interval == null) throw invalid(column, null);
		return interval.toString();
	}

	static IntervalValue decode(Column column, String text) {
		validate(column);
		if (text == null) return null;
		try {
			return IntervalValue.parse(text);
		} catch (RuntimeException failure) {
			throw invalid(column, failure);
		}
	}

	private static IllegalArgumentException invalid(Column column, Throwable cause) {
		return new IllegalArgumentException("Interval column '" + column.getName()
				+ "' requires sqlapp interval text P[nM][nD][Tn[.fraction]S] (up to 9 fractional digits); "
				+ "database-specific and legacy interval text are not accepted.", cause);
	}
}
