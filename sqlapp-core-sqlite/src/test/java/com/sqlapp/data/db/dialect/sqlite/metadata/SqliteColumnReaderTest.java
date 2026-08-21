/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqliteColumnReaderTest {
	@Test
	void splitsDefinitionsWithoutSplittingExpressionsOrStrings() {
		var definitions = SqliteColumnReader.splitColumnDefinitions("""
				CREATE TABLE sample (
				 id INTEGER PRIMARY KEY,
				 "display name" TEXT AS (printf('%s,%s', first_name, last_name)) VIRTUAL,
				 total NUMERIC GENERATED ALWAYS AS ((price + tax) * quantity) STORED
				) STRICT
				""");

		assertEquals(3, definitions.size());
		assertEquals("printf('%s,%s', first_name, last_name)",
				SqliteColumnReader.extractFormula(definitions.get(1)));
		assertEquals("(price + tax) * quantity",
				SqliteColumnReader.extractFormula(definitions.get(2)));
	}
}
