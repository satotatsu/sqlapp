/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;

class SapHanaVectorSqlBuilderTest {

	@Test
	void testVectorFunctions() {
		final SapHanaSqlBuilder builder = (SapHanaSqlBuilder)
				DialectHolder.defaultDialect.createSqlBuilder();
		assertEquals("COSINE_SIMILARITY(EMBEDDING, ?)",
				builder.cosineSimilarity("EMBEDDING", "?").toString());
		builder.clear();
		assertEquals("L2DISTANCE(EMBEDDING, ?)",
				builder.l2Distance("EMBEDDING", "?").toString());
		builder.clear();
		assertEquals("TO_REAL_VECTOR(?)",
				builder.toRealVector("?").toString());
	}
}
