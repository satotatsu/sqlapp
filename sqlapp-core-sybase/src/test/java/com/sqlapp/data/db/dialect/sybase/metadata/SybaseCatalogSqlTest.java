/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SybaseCatalogSqlTest {
	@Test
	void retainsObjectHeadersWithoutSourceAndDisambiguatesCheckObjects() throws Exception {
		for (String name : new String[] { "procedures.sql", "functions.sql", "views.sql", "triggers.sql",
				"checkConstraints.sql" }) {
			assertTrue(sql(name).contains("left outer join syscomments"), name);
		}
		assertTrue(sql("checkConstraints.sql").contains("co.type = 'c'"));
		assertTrue(sql("tables.sql").contains("si.indid = 1 or (si.status2 & 512) = 512"));
	}

	@Test
	void distinguishesDeclarativeConstraintsAndDolClustering() throws Exception {
		final String constraints = sql("aseUniqueConstraints.sql");
		assertTrue(constraints.contains("(si.status2 & 2) = 2"));
		assertFalse(constraints.contains("(si.status & 2) = 2"));
		assertTrue(constraints.contains("index_colorder(so.name, si.indid, n.number, su.uid)"));
		for (String name : new String[] { "aseUniqueConstraints.sql", "aseIndexes.sql" }) {
			final String query = sql(name);
			assertTrue(query.contains("si.indid = 1 or (si.status2 & 512) = 512"), name);
			assertTrue(query.contains("order by su.name, so.name, si.name, n.number"), name);
		}
	}

	private String sql(String name) throws Exception {
		try (var input = SybaseColumnReader.class.getResourceAsStream(name)) {
			assertNotNull(input, name);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
		}
	}

	@Test
	void isolatesModernColumnsAndAvoidsTextJoinMultiplication() throws Exception {
		final String legacy = sql("columns.sql");
		assertFalse(legacy.contains("computedcol"));
		assertFalse(legacy.contains("status2"));
		assertFalse(legacy.contains("syscomments"));
		final String modern = sql("columns15.sql");
		assertTrue(modern.contains("computedcol"));
		assertTrue(modern.contains("status2"));
		assertFalse(modern.contains("syscomments"));
	}

	@Test
	void retrievesSystemSourceInCompleteChunkOrder() throws Exception {
		for (String name : new String[] { "columnDefinitions.sql", "procedures.sql", "functions.sql", "views.sql",
				"triggers.sql", "checkConstraints.sql" }) {
			final String sql = sql(name).replaceAll("\\s+", " ");
			assertTrue(sql.contains("texttype=0"), name);
			assertTrue(sql.contains("source_hidden"), name);
			assertTrue(sql.matches("(?s).*order by.*number.*colid2.*colid.*"), name);
		}
		assertTrue(sql("triggers.sql").contains("tr.deltrig=tbl.id"));
	}
}
