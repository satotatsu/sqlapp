/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.converter;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.StandardIntervalCodec;
import com.sqlapp.data.interval.IntervalValue;

/** Destination text converter; column limits are enforced by JDBC/literal preparation. */
public final class StandardIntervalConverter extends AbstractConverter<IntervalValue> {
	private static final long serialVersionUID = 1L;
	private final DataType type;

	public StandardIntervalConverter(DataType type) { this.type = type; }

	@Override
	public IntervalValue convertObject(Object value) {
		if (value == null || "".equals(value)) return getDefaultValue();
		if (isSupplier(value)) return convertObject(getSupplierValue(value));
		if (value instanceof String text && !text.startsWith("P") && !text.startsWith("p"))
			return new StandardIntervalCodec(type, 18, 9).parse(text);
		return new IntervalValueConverter().convertObject(value);
	}

	@Override
	public String format(IntervalValue value) {
		return value == null ? null : new StandardIntervalCodec(type, 18, 9).convert(value).text();
	}

	@Override
	public IntervalValue copy(Object value) { return convertObject(value); }
}
