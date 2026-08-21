/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;

class VirticaTableReaderTest {

	@Test
	void textIndexReaderIsAvailableAcrossVersionBoundaries() {
		assertIndexReader(7, 1, 0);
		assertIndexReader(7, 2, 0);
		assertIndexReader(8, 0, 0);
		assertIndexReader(9, 0, 0);
		assertIndexReader(11, 1, 0);
		assertIndexReader(11, 1, 1);
		assertIndexReader(25, 1, 0);
	}

	private void assertIndexReader(int major, int minor, int revision) {
		var dialect = DialectResolver.getInstance().getDialect(
				"Vertica", major, minor, revision);
		var tableReader = new VirticaTableReader(dialect);
		assertInstanceOf(VirticaIndexReader.class, tableReader.newIndexReader());
	}
}
