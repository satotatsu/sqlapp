/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Converts a number to an integral type: the fraction is truncated toward zero
 * as before, but a value outside the target range is rejected instead of being
 * wrapped ({@code (int) 3000000000L}) or saturated ({@code (int) 3e9}), which
 * would silently store a different number.
 */
final class IntegralNumbers {

	private IntegralNumbers() {
	}

	/**
	 * @param value    number to convert
	 * @param original value reported in the error message
	 * @param type     target type name for the error message
	 * @param min      smallest allowed value
	 * @param max      largest allowed value
	 * @return the truncated value within {@code [min, max]}
	 * @throws ConverterException when the truncated value is out of range or not
	 *                            a finite number
	 */
	static long toLong(final Number value, final Object original, final String type, final long min,
			final long max) {
		final long result;
		if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
				|| value instanceof java.util.concurrent.atomic.AtomicInteger
				|| value instanceof java.util.concurrent.atomic.AtomicLong) {
			result = value.longValue();
		} else if (value instanceof Double || value instanceof Float) {
			final double number = value.doubleValue();
			if (Double.isNaN(number) || Double.isInfinite(number)) {
				throw outOfRange(original, type);
			}
			// (long) saturates beyond the long range, so large values are checked
			// exactly; the range check below catches (int) wrapping.
			result = Math.abs(number) < 9.0e18 ? (long) number : toLong(new BigDecimal(number), original, type);
		} else if (value instanceof BigInteger integer) {
			if (integer.bitLength() > 63) {
				throw outOfRange(original, type);
			}
			result = integer.longValue();
		} else {
			result = toLong(value instanceof BigDecimal decimal ? decimal : decimal(value, original, type), original,
					type);
		}
		if (result < min || result > max) {
			throw outOfRange(original, type);
		}
		return result;
	}

	/** Parses text the same way as the converters: integer first, then decimal. */
	static long parse(final String value, final String type, final long min, final long max) {
		try {
			return toLong(Long.valueOf(value), value, type, min, max);
		} catch (NumberFormatException e) {
			try {
				return toLong(new BigDecimal(value), value, type, min, max);
			} catch (NumberFormatException decimalFailure) {
				// Keeps the previous behavior for text such as "1e3" or "NaN".
				return toLong(Double.valueOf(value), value, type, min, max);
			}
		}
	}

	private static long toLong(final BigDecimal decimal, final Object original, final String type) {
		// Values inside the long range are the common case; avoid creating objects.
		if (decimal.scale() <= 0 || decimal.precision() - decimal.scale() < 19) {
			final BigDecimal truncated = decimal.scale() <= 0 ? decimal : decimal.setScale(0, RoundingMode.DOWN);
			try {
				return truncated.longValueExact();
			} catch (ArithmeticException e) {
				throw outOfRange(original, type);
			}
		}
		final BigInteger integer = decimal.toBigInteger();
		if (integer.bitLength() > 63) {
			throw outOfRange(original, type);
		}
		return integer.longValue();
	}

	private static BigDecimal decimal(final Number value, final Object original, final String type) {
		try {
			return new BigDecimal(value.toString());
		} catch (NumberFormatException e) {
			throw new ConverterException("Cannot convert to " + type + ": " + original, e);
		}
	}

	private static ConverterException outOfRange(final Object original, final String type) {
		return new ConverterException("Value out of range for " + type + ": " + original
				+ (original == null ? "" : " (" + original.getClass().getName() + ")"));
	}
}
