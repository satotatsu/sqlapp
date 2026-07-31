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

class PostgresArrayBuilderTest {
	private final PostgresArrayBuilder builder =
			new PostgresArrayBuilder(DialectHolder.postgreSQL180);

	@Test
	void testArrayReverse() {
		assertEquals("array_reverse(values_array)",
				builder.arrayReverse("values_array"));
	}

	@Test
	void testArraySortVariants() {
		assertEquals("array_sort(values_array)",
				builder.arraySort("values_array"));
		assertEquals("array_sort(values_array, true)",
				builder.arraySort("values_array", true));
		assertEquals("array_sort(values_array, true, false)",
				builder.arraySort("values_array", true, false));
	}

	@Test
	void testRejectBeforePostgres18() {
		PostgresArrayBuilder postgres17 =
				new PostgresArrayBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.arrayReverse("values_array"));
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.arraySort("values_array"));
	}

	@Test
	void testRejectEmptyExpression() {
		assertThrows(IllegalArgumentException.class,
				() -> builder.arraySort(""));
		assertThrows(IllegalArgumentException.class,
				() -> builder.arrayReverse(null));
	}
}
