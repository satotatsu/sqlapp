/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.math.BigInteger;

/** Validation shared by explicit sequence and identity restart requests. */
final class SpannerCounterRestart {
	private SpannerCounterRestart() {
	}

	static long value(final String value, final String object) {
		try {
			final var counter = new BigInteger(value == null ? "" : value.trim());
			if (counter.signum() <= 0 || counter.bitLength() > 63)
				throw new NumberFormatException();
			return counter.longValueExact();
		} catch (ArithmeticException | NumberFormatException exception) {
			throw new IllegalArgumentException("Spanner counter restart requires an explicit positive INT64 for " + object,
					exception);
		}
	}
}
