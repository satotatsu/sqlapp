/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbJdbcTypeHandlers.IntervalHandler;
import com.sqlapp.data.interval.Interval;

class DuckDbJdbcTypeHandlersTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "1 day|0 months 1 days 0 microseconds",
			"1 year 2 months 3 days 04:05:06.789|14 months 3 days 14706789000 microseconds",
			"-1 year -2 months -3 days -04:05:06.789|-14 months -3 days -14706789000 microseconds",
			"25:00:00|0 months 1 days 3600000000 microseconds", "00:00:00.000001|0 months 0 days 1 microseconds",
			"00:00:00|0 months 0 days 0 microseconds", "-2 years|-24 months 0 days 0 microseconds" })
	void parsesAndFormatsDuckDbIntervals(final String duckDb, final String formatted) {
		final Interval interval = assertInstanceOf(Interval.class, IntervalHandler.parse(duckDb));
		assertEquals(formatted, IntervalHandler.format(interval));
	}

	@Test
	void keepsMixedSignIntervalsAsText() {
		assertEquals("-1 month 2 days -00:00:01.5", IntervalHandler.parse("-1 month 2 days -00:00:01.5"));
	}

	@Test
	void rejectsUnknownIntervalText() {
		assertThrows(IllegalArgumentException.class, () -> IntervalHandler.parse("1 fortnight"));
	}
}
