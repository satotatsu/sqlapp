/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.schemas.ProductVersionInfo;

class SybaseTableSpaceFileReaderTest {
	@ParameterizedTest
	@CsvSource({
			"12,5,tableSpaceFiles.sql",
			"14,0,tableSpaceFiles.sql",
			"15,0,tableSpaceFiles15.sql",
			"16,0,tableSpaceFiles15.sql"
	})
	void selectsVersionCompatibleCatalogQuery(final int major,
			final int minor, final String expected) {
		var version = new ProductVersionInfo()
				.setMajorVersion(major)
				.setMinorVersion(minor);
		assertEquals(expected,
				SybaseTableSpaceFileReader.getTableSpaceFileSqlResource(version));
	}
}
