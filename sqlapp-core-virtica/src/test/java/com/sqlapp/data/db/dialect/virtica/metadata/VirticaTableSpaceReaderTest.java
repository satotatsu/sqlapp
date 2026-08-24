/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;

class VirticaTableSpaceReaderTest {

	@Test
	void fileReaderIsAvailableAcrossVersionBoundaries() {
		assertFileReader(7, 1, 0);
		assertFileReader(9, 0, 0);
		assertFileReader(11, 1, 1);
		assertFileReader(12, 0, 4);
		assertFileReader(25, 1, 0);
	}

	private void assertFileReader(int major, int minor, int revision) {
		var dialect = DialectResolver.getInstance().getDialect(
				"Vertica", major, minor, revision);
		var reader = new VirticaTableSpaceReader(dialect);
		assertInstanceOf(VirticaTableSpaceFileReader.class,
				reader.newTableSpaceFileReader());
	}
}
