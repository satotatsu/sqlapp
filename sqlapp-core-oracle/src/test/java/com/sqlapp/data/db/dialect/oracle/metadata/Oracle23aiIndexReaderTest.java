/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.VectorDistanceType;

class Oracle23aiIndexReaderTest {

	@Test
	void testApplyVectorIndexParameters() {
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING");

		Oracle23aiIndexReader.applyVectorIndexParameters(index, """
				{
				  "type": "HNSW",
				  "num_neighbors": 32,
				  "efConstruction": 300,
				  "distance": "COSINE",
				  "accuracy": 95,
				  "vector_type": "FLOAT32",
				  "vector_dimension": 384,
				  "degree_of_parallelism": 4,
				  "indexed_col": "EMBED_VECTOR"
				}
				""");

		assertEquals(VectorDistanceType.Cosine, index.getVectorDistanceType());
		assertEquals("HNSW", index.getSpecifics().get(Oracle23aiIndexReader.ORGANIZATION));
		assertEquals("32", index.getSpecifics().get(Oracle23aiIndexReader.NEIGHBORS));
		assertEquals("300", index.getSpecifics().get(Oracle23aiIndexReader.EFCONSTRUCTION));
		assertEquals("95", index.getSpecifics().get(Oracle23aiIndexReader.TARGET_ACCURACY));
		assertEquals("4", index.getSpecifics().get(Oracle23aiIndexReader.PARALLEL));
		assertEquals("FLOAT32", index.getSpecifics().get("VECTOR_TYPE"));
		assertEquals("384", index.getSpecifics().get("VECTOR_DIMENSION"));
		assertEquals("EMBED_VECTOR", index.getSpecifics().get("INDEXED_COLUMN"));
	}

	@Test
	void testOnlyUnavailableOrUnauthorizedErrorsCanBeSkipped() {
		assertTrue(Oracle23aiIndexReader.isVectorIndexDetailUnavailable(
				new RuntimeException(new SQLException("table or view does not exist", "42000", 942))));
		assertTrue(Oracle23aiIndexReader.isVectorIndexDetailUnavailable(
				new RuntimeException(new SQLException("insufficient privileges", "42000", 1031))));
		assertFalse(Oracle23aiIndexReader.isVectorIndexDetailUnavailable(
				new RuntimeException(new SQLException("invalid identifier", "42000", 904))));
	}
}
