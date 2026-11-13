/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbDdlParser.ColumnDdl;

/**
 * Inputs are DDL strings as stored by DuckDB in duckdb_tables().sql and
 * duckdb_indexes().sql.
 */
class DuckDbDdlParserTest {

	@Test
	void readsGeneratedColumnsAndCollationsRespectingQuotes() {
		final String sql = "CREATE TABLE \"we(ird\".\"T\"\"x(\"(a INTEGER CHECK((a > 0)), "
				+ "\"b,c\" INTEGER GENERATED ALWAYS AS((a + 1)), \"Name\" VARCHAR DEFAULT('x,(y)'), "
				+ "d STRUCT(\"GENERATED ALWAYS AS\" INTEGER), e INTEGER GENERATED ALWAYS AS(length('a)b')), "
				+ "f VARCHAR COLLATE nocase, g VARCHAR DEFAULT('COLLATE x'), UNIQUE(a));";
		final Map<String, ColumnDdl> columns = DuckDbDdlParser.columns(sql);
		assertEquals(List.of("a", "b,c", "Name", "d", "e", "f", "g"), List.copyOf(columns.keySet()));
		assertEquals("(a + 1)", columns.get("b,c").generationExpression());
		assertEquals("length('a)b')", columns.get("e").generationExpression());
		assertNull(columns.get("d").generationExpression());
		assertNull(columns.get("Name").generationExpression());
		assertEquals("nocase", columns.get("f").collation());
		assertNull(columns.get("g").collation());
		assertEquals(Map.of("b,c", "(a + 1)", "e", "length('a)b')"), DuckDbDdlParser.generatedColumns(sql));
	}

	@Test
	void readsIndexKeys() {
		final List<String> keys = DuckDbDdlParser
				.indexKeys("CREATE INDEX \"i on(\" ON \"we(ird\".\"T\"\"x(\"((a + 1), \"b,c\", \"Name\", lower(x));");
		assertEquals(List.of("(a + 1)", "\"b,c\"", "\"Name\"", "lower(x)"), keys);
		assertNull(DuckDbDdlParser.identifierOrNull(keys.get(0)));
		assertEquals("b,c", DuckDbDdlParser.identifierOrNull(keys.get(1)));
		assertEquals("Name", DuckDbDdlParser.identifierOrNull(keys.get(2)));
		assertNull(DuckDbDdlParser.identifierOrNull(keys.get(3)));
		assertEquals(List.of("code", "pid"),
				DuckDbDdlParser.indexKeys("CREATE UNIQUE INDEX ix1 ON s1.child(code, pid);"));
	}

	@Test
	void readsEnumValues() {
		assertEquals(List.of("a", "b,c", "d'e"), DuckDbDdlParser.enumValues("ENUM('a', 'b,c', 'd''e')"));
		assertNull(DuckDbDdlParser.enumValues("VARCHAR"));
		assertNull(DuckDbDdlParser.enumValues("STRUCT(a ENUM('x'))"));
	}
}
