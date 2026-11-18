/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.converter;

import java.util.Objects;

import com.sqlapp.data.interval.IntervalValue;

/**
 * Keeps canonical values supplied by modern JDBC adapters intact, while retaining
 * the existing typed parser for old values. No database semantics are applied here.
 */
public final class IntervalColumnConverter extends AbstractConverter<Object> {
	private static final long serialVersionUID = 1L;
	private final Converter<?> legacy;

	public IntervalColumnConverter(final Converter<?> legacy) {
		this.legacy = Objects.requireNonNull(legacy, "legacy");
	}

	@Override
	public Object convertObject(final Object value) {
		if (isSupplier(value)) return convertObject(getSupplierValue(value));
		if (value instanceof IntervalValue) return value;
		if (value instanceof String text && (text.startsWith("P") || text.startsWith("p"))) {
			return IntervalValue.parse(text);
		}
		return legacy.convertObject(value);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public String format(final Object value) {
		if (value instanceof IntervalValue interval) return interval.toString();
		return ((Converter) legacy).format(value);
	}

	@Override
	public Object copy(final Object value) {
		if (isSupplier(value)) return copy(getSupplierValue(value));
		if (value instanceof String text && (text.startsWith("P") || text.startsWith("p"))) {
			return IntervalValue.parse(text);
		}
		return value instanceof IntervalValue ? value : legacy.copy(value);
	}
}
