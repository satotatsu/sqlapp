/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder.VectorChunkNormalization;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder.VectorChunkSplit;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder.VectorChunkUnit;
import com.sqlapp.data.schemas.VectorDistanceType;

class OracleVectorSearchSqlBuilderTest {

	@Test
	void testVectorDistanceAndOperators() {
		assertEquals("VECTOR_DISTANCE(embedding, :queryVector, COSINE)",
				builder().vectorDistance("embedding", ":queryVector",
						VectorDistanceType.Cosine).toString());
		assertEquals("embedding <-> :queryVector",
				builder().vectorDistanceOperator("embedding", ":queryVector",
						VectorDistanceType.Euclidean).toString());
		assertEquals("embedding <=> :queryVector",
				builder().vectorDistanceOperator("embedding", ":queryVector",
						VectorDistanceType.Cosine).toString());
		assertEquals("embedding <#> :queryVector",
				builder().vectorDistanceOperator("embedding", ":queryVector",
						VectorDistanceType.DotProduct).toString());
		assertThrows(IllegalArgumentException.class,
				() -> builder().vectorDistanceOperator("embedding", ":queryVector",
						VectorDistanceType.Manhattan));
	}

	@Test
	void testVectorConversions() {
		assertEquals("TO_VECTOR(:queryVector, 768, FLOAT32)",
				builder().toVector(":queryVector", 768, DataType.REAL).toString());
		assertEquals("TO_VECTOR(:queryVector, *, INT8)",
				builder().toVector(":queryVector", null, DataType.TINYINT).toString());
		assertEquals("FROM_VECTOR(embedding)",
				builder().fromVector("embedding").toString());
		assertThrows(IllegalArgumentException.class,
				() -> builder().toVector(":queryVector", 7, DataType.BINARY));
	}

	@Test
	void testVectorEmbedding() {
		assertEquals("VECTOR_EMBEDDING(DOC_MODEL USING :text AS DATA)",
				builder().vectorEmbedding("DOC_MODEL", ":text", "DATA")
						.toString());
		assertEquals("VECTOR_EMBEDDING(DOC_MODEL USING content)",
				builder().vectorEmbedding("DOC_MODEL", "content").toString());
		assertEquals("VECTOR_EMBEDDING(FEATURE_MODEL USING *)",
				builder().vectorEmbeddingUsingAll("FEATURE_MODEL").toString());
		assertThrows(IllegalArgumentException.class,
				() -> builder().vectorEmbedding("", "content"));
	}

	@Test
	void testVectorChunks() {
		assertEquals("VECTOR_CHUNKS(content)",
				builder().vectorChunks("content").toString());
		assertEquals("VECTOR_CHUNKS(content BY WORDS MAX 200 OVERLAP 10 "
				+ "SPLIT BY RECURSIVELY LANGUAGE american NORMALIZE ALL "
				+ "EXTENDED)",
				builder().vectorChunks("content", VectorChunkUnit.Words, 200,
						10, VectorChunkSplit.Recursively, "american",
						VectorChunkNormalization.All, true).toString());
	}

	@Test
	void testRejectInvalidVectorChunks() {
		assertThrows(IllegalArgumentException.class,
				() -> builder().vectorChunks("content",
						VectorChunkUnit.Words, 9, null, null, null, null,
						false));
		assertThrows(IllegalArgumentException.class,
				() -> builder().vectorChunks("content",
						VectorChunkUnit.Words, 100, 30, null, null, null,
						false));
		assertThrows(IllegalArgumentException.class,
				() -> builder().vectorChunks("content",
						VectorChunkUnit.Characters, 100, null,
						VectorChunkSplit.Sentence, null, null, false));
	}

	@Test
	void testApproximateFetch() {
		assertEquals(
				"FETCH APPROXIMATE FIRST 10 ROWS ONLY WITH TARGET ACCURACY 95",
				builder().fetchApproximateFirst(10, 95).toString());
		assertEquals(
				"FETCH APPROXIMATE FIRST 10 ROWS ONLY WITH TARGET ACCURACY PARAMETERS (EFSEARCH 500)",
				builder().fetchApproximateFirst(10, null, 500, null).toString());
		assertEquals(
				"FETCH APPROXIMATE FIRST 10 ROWS ONLY WITH TARGET ACCURACY PARAMETERS (EFSEARCH 500, NEIGHBOR PARTITION PROBES 20)",
				builder().fetchApproximateFirst(10, null, 500, 20).toString());
		assertThrows(IllegalArgumentException.class,
				() -> builder().fetchApproximateFirst(10, 95, 500, null));
	}

	@Test
	void testRejectBeforeOracle23ai() {
		final var oracle21 = DialectResolver.getInstance()
				.getDialect("Oracle", 21, 0, 0);
		assertThrows(IllegalArgumentException.class,
				() -> new OracleSqlBuilder(oracle21).vectorDistance(
						"embedding", ":queryVector", VectorDistanceType.Cosine));
		assertThrows(IllegalArgumentException.class,
				() -> new OracleSqlBuilder(oracle21)
						.vectorEmbedding("DOC_MODEL", ":text"));
		assertThrows(IllegalArgumentException.class,
				() -> new OracleSqlBuilder(oracle21).vectorChunks(":text"));
	}

	private OracleSqlBuilder builder() {
		final var oracle23 = DialectResolver.getInstance()
				.getDialect("Oracle", 23, 0, 0);
		return new OracleSqlBuilder(oracle23);
	}
}
