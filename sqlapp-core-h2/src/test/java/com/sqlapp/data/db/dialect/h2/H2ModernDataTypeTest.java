/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class H2ModernDataTypeTest {

	@Test
	void testModernDataTypesAndCurrentFunctions() {
		final H2 dialect = (H2) DialectHolder.defaultDialect;
		assertType(dialect, "JSON", DataType.JSON);
		assertType(dialect, "ENUM", DataType.ENUM);
		assertType(dialect, "DECFLOAT", DataType.DECIMALFLOAT);
		assertType(dialect, "TIME WITH TIME ZONE",
				DataType.TIME_WITH_TIMEZONE);
		assertType(dialect, "TIMESTAMP WITH TIME ZONE",
				DataType.TIMESTAMP_WITH_TIMEZONE);
		assertEquals("CURRENT_DATE", dialect.getCurrentDateFunction());
		assertEquals("CURRENT_TIME", dialect.getCurrentTimeFunction());
		assertEquals("CURRENT_TIMESTAMP",
				dialect.getCurrentTimestampFunction());
	}

	private void assertType(final H2 dialect, final String typeName,
			final DataType expected) {
		final Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName(typeName);
		assertEquals(expected, column.getDataType());
	}
}
