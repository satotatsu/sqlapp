/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqliteCheckConstraintReaderTest {
	@Test
	void extractsNestedAndMultipleCheckExpressions() {
		var expressions = SqliteCheckConstraintReader.extractExpressions(
				"value TEXT CHECK (length(value) > 1) "
				+ "CHECK (value <> ')' AND instr(value, '(') >= 0)");

		assertEquals(2, expressions.size());
		assertEquals("length(value) > 1", expressions.get(0));
		assertEquals("value <> ')' AND instr(value, '(') >= 0",
				expressions.get(1));
	}
}
