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

class PostgresExplainBuilderTest {
	@Test
	void testExplainAnalyzeWithModernOptions() {
		assertEquals(
				"EXPLAIN (ANALYZE TRUE, BUFFERS TRUE, SERIALIZE BINARY, WAL TRUE, TIMING FALSE, MEMORY TRUE, FORMAT JSON) SELECT * FROM orders",
				new PostgresExplainBuilder(DialectHolder.postgreSQL180)
						.analyze(true)
						.buffers(true)
						.serialize("binary")
						.wal(true)
						.timing(false)
						.memory(true)
						.format("json")
						.statement("SELECT * FROM orders")
						.build());
	}

	@Test
	void testGenericPlanFromPostgres16() {
		assertEquals(
				"EXPLAIN (GENERIC_PLAN TRUE, FORMAT YAML) SELECT * FROM orders WHERE id = $1",
				new PostgresExplainBuilder(DialectHolder.postgreSQL160)
						.genericPlan(true)
						.format("yaml")
						.statement("SELECT * FROM orders WHERE id = $1")
						.build());
	}

	@Test
	void testVersionBoundaries() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL150)
						.genericPlan(true));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL160)
						.serialize("text"));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL160)
						.memory(true));
	}

	@Test
	void testRejectInvalidOptionCombinations() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL180)
						.analyze(true)
						.genericPlan(true)
						.statement("SELECT * FROM orders WHERE id = $1")
						.build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL180)
						.wal(true)
						.statement("SELECT * FROM orders")
						.build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL180)
						.serialize("binary")
						.statement("SELECT * FROM orders")
						.build());
	}

	@Test
	void testValidation() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL180)
						.format("csv"));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresExplainBuilder(DialectHolder.postgreSQL180)
						.build());
	}
}
