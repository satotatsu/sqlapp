/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresModernFunctionBuilderTest {
	private final PostgresModernFunctionBuilder builder =
			new PostgresModernFunctionBuilder(DialectHolder.postgreSQL180);

	@Test
	void testCasefoldAndChecksums() {
		assertEquals("casefold(display_name)", builder.casefold("display_name"));
		assertEquals("crc32(payload)", builder.crc32("payload"));
		assertEquals("crc32c(payload)", builder.crc32c("payload"));
	}

	@Test
	void testByteaFunctionsAndIntegerCasts() {
		assertEquals("reverse(payload)", builder.reverseBytes("payload"));
		assertEquals("CAST(event_id AS bytea)",
				builder.integerToBytea("event_id"));
		assertEquals("CAST(payload AS bigint)",
				builder.byteaToInteger("payload", "BIGINT"));
		assertThrows(IllegalArgumentException.class,
				() -> builder.byteaToInteger("payload", "numeric"));
	}

	@Test
	void testGammaFunctions() {
		assertEquals("gamma(value)", builder.gamma("value"));
		assertEquals("lgamma(value)", builder.lgamma("value"));
	}

	@Test
	void testStripNullArrayElements() {
		assertEquals("json_strip_nulls(payload, true)",
				builder.jsonStripNulls("payload", true));
		assertEquals("jsonb_strip_nulls(payload, false)",
				builder.jsonbStripNulls("payload", false));
	}

	@Test
	void testRejectBeforePostgres18() {
		PostgresModernFunctionBuilder postgres17 =
				new PostgresModernFunctionBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.casefold("display_name"));
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.crc32("payload"));
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.reverseBytes("payload"));
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.gamma("value"));
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.integerToBytea("event_id"));
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.jsonbStripNulls("payload", true));
	}

	@Test
	void testRejectEmptyExpression() {
		assertThrows(IllegalArgumentException.class,
				() -> builder.casefold(""));
		assertThrows(IllegalArgumentException.class,
				() -> builder.jsonStripNulls(null, true));
		assertThrows(IllegalArgumentException.class,
				() -> builder.byteaToInteger("", "integer"));
	}
}
