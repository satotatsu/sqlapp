/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.VectorDistanceType;

class SapHanaVectorIndexReaderTest {

	@Test
	void testSimilarityFunctionMapping() {
		assertEquals(VectorDistanceType.Cosine,
				SapHanaVectorIndexReader.toVectorDistanceType(
						"COSINE_SIMILARITY"));
		assertEquals(VectorDistanceType.Euclidean,
				SapHanaVectorIndexReader.toVectorDistanceType(
						"L2DISTANCE"));
		assertNull(SapHanaVectorIndexReader.toVectorDistanceType(
				"FUTURE_DISTANCE"));
	}
}
