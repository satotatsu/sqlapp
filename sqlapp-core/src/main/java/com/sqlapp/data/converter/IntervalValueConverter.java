/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.converter;

import java.time.Duration;
import java.time.Period;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.data.interval.IntervalValue;

/** Canonical interval conversion without database-specific text guessing. */
public class IntervalValueConverter extends AbstractConverter<IntervalValue> {
	private static final long serialVersionUID = 1L;

	@Override
	public IntervalValue convertObject(final Object value) {
		if (value == null || "".equals(value)) return getDefaultValue();
		if (isSupplier(value)) return convertObject(getSupplierValue(value));
		if (value instanceof IntervalValue interval) return interval;
		if (value instanceof Interval interval) return interval.toIntervalValue();
		if (value instanceof Duration duration) return new IntervalValue(0, 0, duration);
		if (value instanceof Period period) {
			return new IntervalValue(period.toTotalMonths(), period.getDays(), Duration.ZERO);
		}
		if (value instanceof String text) return IntervalValue.parse(text);
		throw new IllegalArgumentException("Unsupported interval value: " + value.getClass().getName());
	}

	@Override
	public String format(final IntervalValue value) {
		return value == null ? null : value.toString();
	}

	@Override
	public IntervalValue copy(final Object value) {
		return convertObject(value);
	}
}
