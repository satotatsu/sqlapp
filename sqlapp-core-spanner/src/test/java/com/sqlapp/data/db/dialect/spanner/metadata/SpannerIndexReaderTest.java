/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.VectorDistanceType;

class SpannerIndexReaderTest {

	@Test
	void testProductIndexTypeMapping() {
		assertEquals(IndexType.BTree,
				SpannerIndexReader.toIndexType("INDEX"));
		assertEquals(IndexType.FullText,
				SpannerIndexReader.toIndexType("SEARCH"));
		assertEquals(IndexType.Vector,
				SpannerIndexReader.toIndexType("VECTOR"));
	}

	@Test
	void testVectorDistanceMapping() {
		assertEquals(VectorDistanceType.Cosine,
				SpannerIndexReader.toVectorDistanceType("COSINE"));
		assertEquals(VectorDistanceType.DotProduct,
				SpannerIndexReader.toVectorDistanceType("DOT_PRODUCT"));
		assertEquals(VectorDistanceType.Euclidean,
				SpannerIndexReader.toVectorDistanceType("EUCLIDEAN"));
		assertNull(SpannerIndexReader.toVectorDistanceType(null));
	}
}
