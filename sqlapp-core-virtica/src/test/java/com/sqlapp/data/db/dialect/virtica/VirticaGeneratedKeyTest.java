/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;

class VirticaGeneratedKeyTest {
	@Test
	void supportsOnlyExplicitSequencePreallocation() {
		Dialect dialect = DialectHolder.defaultDialect90;
		assertTrue(dialect.supportsIdentity());
		assertFalse(dialect.supportsIdentitySequencePreallocation());
		assertTrue(dialect.supportsSequencePreallocation());
		assertTrue(dialect.requiresExplicitIdentityValuesForGeneratedKeys());
	}
}
