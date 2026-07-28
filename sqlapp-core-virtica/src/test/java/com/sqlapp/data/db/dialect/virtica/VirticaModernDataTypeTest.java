/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class VirticaModernDataTypeTest {

	@Test
	void testNativeUuidAndProductName() {
		final Virtica dialect = (Virtica) DialectHolder.defaultDialect80;
		final Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("UUID");
		assertEquals(DataType.UUID, column.getDataType());
		assertEquals("Vertica", dialect.getProductName());
	}
}
