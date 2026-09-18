/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle23ai;
import com.sqlapp.data.db.dialect.oracle.Oracle26ai;

final class OracleTestEnvironment {

	private static final String DEFAULT_IMAGE = "gvenzl/oracle-free:23-slim-faststart";

	private OracleTestEnvironment() {
	}

	static String image() {
		return System.getProperty("sqlapp.test.oracle.image", DEFAULT_IMAGE);
	}

	static void assertExpectedDialect(final Dialect dialect) {
		String expected = System.getProperty("sqlapp.test.oracle.expectedDialect", "23ai");
		if ("26ai".equalsIgnoreCase(expected)) {
			assertInstanceOf(Oracle26ai.class, dialect);
		} else {
			assertInstanceOf(Oracle23ai.class, dialect);
		}
	}
}
