/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

class SapHanaUtilsTest {

	@Test
	void testUniqueIndexType() {
		final Index index = new Index("IDX_TEST");

		SapHanaUtils.setIndexType("BTREE_UNIQUE", index);

		assertTrue(index.isUnique());
		assertEquals(IndexType.BTree, index.getIndexType());
		assertFalse(index.isCompression());
	}

	@Test
	void testCompressedIndexType() {
		final Index index = new Index("IDX_TEST");

		SapHanaUtils.setIndexType("CPBTREE", index);

		assertFalse(index.isUnique());
		assertEquals(IndexType.CPBTree, index.getIndexType());
		assertTrue(index.isCompression());
	}
}
