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

class PostgresUuidBuilderTest {
	private final PostgresUuidBuilder builder =
			new PostgresUuidBuilder(DialectHolder.postgreSQL180);

	@Test
	void testUuidGeneration() {
		assertEquals("uuidv4()", builder.uuidV4());
		assertEquals("uuidv7()", builder.uuidV7());
		assertEquals("uuidv7(INTERVAL '-1 hour')",
				builder.uuidV7("INTERVAL '-1 hour'"));
	}

	@Test
	void testUuidExtraction() {
		assertEquals("uuid_extract_version(event_id)",
				builder.extractVersion("event_id"));
		assertEquals("uuid_extract_timestamp(event_id)",
				builder.extractTimestamp("event_id"));
	}

	@Test
	void testRejectBeforePostgres18() {
		PostgresUuidBuilder postgres17 =
				new PostgresUuidBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class, postgres17::uuidV4);
		assertThrows(IllegalArgumentException.class, postgres17::uuidV7);
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.extractVersion("event_id"));
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.extractTimestamp("event_id"));
	}

	@Test
	void testRejectEmptyExpressions() {
		assertThrows(IllegalArgumentException.class,
				() -> builder.uuidV7(""));
		assertThrows(IllegalArgumentException.class,
				() -> builder.extractVersion(null));
	}
}
