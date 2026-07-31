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

class PostgresCopyFromBuilderTest {

	@Test
	void testCopyFreezeForLocalTable() {
		assertEquals(
				"COPY events FROM STDIN WITH (FREEZE true)",
				new PostgresCopyFromBuilder(
						DialectHolder.postgreSQL180, "events")
						.freeze(true)
						.build());
	}

	@Test
	void testRejectCopyFreezeForForeignTableOnPostgres18() {
		PostgresCopyFromBuilder builder = new PostgresCopyFromBuilder(
				DialectHolder.postgreSQL180, "foreign_events")
				.foreignTable(true)
				.freeze(true);

		assertThrows(IllegalArgumentException.class, builder::build);
	}

	@Test
	void testKeepPre18ForeignTableFreezeCompatibility() {
		assertEquals(
				"COPY foreign_events FROM STDIN WITH (FREEZE true)",
				new PostgresCopyFromBuilder(
						DialectHolder.postgreSQL170, "foreign_events")
						.foreignTable(true)
						.freeze(true)
						.build());
	}

	@Test
	void testCopyFromStdinWithPostgres17ErrorHandling() {
		assertEquals(
				"COPY public.orders (id, payload) FROM STDIN WITH (FORMAT csv, HEADER true, DELIMITER ',', NULL '', ENCODING 'UTF8', ON_ERROR ignore, LOG_VERBOSITY verbose)",
				new PostgresCopyFromBuilder(DialectHolder.postgreSQL170,
						"public", "orders")
						.column("id")
						.column("payload")
						.format("csv")
						.header(true)
						.delimiter(",")
						.nullString("")
						.encoding("UTF8")
						.onError("ignore")
						.logVerbosity("verbose")
						.build());
	}

	@Test
	void testExistingCopyOptionsRemainAvailableBeforePostgres17() {
		assertEquals(
				"COPY orders FROM STDIN WITH (FORMAT csv)",
				new PostgresCopyFromBuilder(DialectHolder.postgreSQL160, "orders")
						.format("csv")
						.build());
	}

	@Test
	void testRejectPostgres17OptionsOnOlderVersion() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL160,
						"orders").onError("ignore").build());
	}

	@Test
	void testRejectVerboseLoggingWithoutIgnoredRows() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL170,
						"orders").logVerbosity("verbose").build());
	}

	@Test
	void testRejectUnknownErrorOptions() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL170,
						"orders").onError("continue"));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL170,
						"orders").logVerbosity("debug"));
	}

	@Test
	void testForceNullOptionsForAllCsvColumns() {
		assertEquals(
				"COPY orders FROM STDIN WITH (FORMAT csv, FORCE_NULL *, FORCE_NOT_NULL *)",
				new PostgresCopyFromBuilder(DialectHolder.postgreSQL170, "orders")
						.format("csv")
						.forceNullAll(true)
						.forceNotNullAll(true)
						.build());
	}

	@Test
	void testRejectAllColumnForceOptionsOutsidePostgres17Csv() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL160,
						"orders").format("csv").forceNullAll(true).build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL170,
						"orders").format("text").forceNullAll(true).build());
	}

	@Test
	void testPostgres18RejectLimitAndSilentLogging() {
		assertEquals(
				"COPY orders FROM STDIN WITH (FORMAT csv, ON_ERROR ignore, REJECT_LIMIT 100, LOG_VERBOSITY silent)",
				new PostgresCopyFromBuilder(DialectHolder.postgreSQL180, "orders")
						.format("csv")
						.onError("ignore")
						.rejectLimit(100)
						.logVerbosity("silent")
						.build());
	}

	@Test
	void testRejectInvalidPostgres18ErrorLimitOptions() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL180,
						"orders").rejectLimit(0));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL180,
						"orders").rejectLimit(10).build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL170,
						"orders").onError("ignore").rejectLimit(10).build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresCopyFromBuilder(DialectHolder.postgreSQL170,
						"orders").onError("ignore")
						.logVerbosity("silent").build());
	}
}
