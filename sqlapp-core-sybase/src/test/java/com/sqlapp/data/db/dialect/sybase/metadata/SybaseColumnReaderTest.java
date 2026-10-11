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

class SybaseColumnReaderTest {
	@ParameterizedTest
	@CsvSource({
			"12,5,columns.sql",
			"14,0,columns.sql",
			"15,0,columns15.sql",
			"16,0,columns15.sql"
	})
	void selectsVersionCompatibleCatalogQuery(final int major,
			final int minor, final String expected) {
		var version = new ProductVersionInfo()
				.setMajorVersion(major)
				.setMinorVersion(minor);
		assertEquals(expected,
				SybaseColumnReader.getColumnSqlResource(version));
	}
}
