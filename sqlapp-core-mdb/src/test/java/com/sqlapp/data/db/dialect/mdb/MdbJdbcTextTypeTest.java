/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Types;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class MdbJdbcTextTypeTest {
	@Test
	void ucanaccessVarcharMetadataRetainsTextLengthAndJdbcHandler() {
		for (String name : new String[] { "VARCHAR", "CHARACTER VARYING" }) {
			var column = new Column("顧客 名");
			assertTrue(DialectHolder.defaultDialect.setDbType(Types.VARCHAR, name, 40L, 0, column));
			assertEquals(DataType.VARCHAR, column.getDataType());
			assertEquals(40L, column.getLength());
			assertNotNull(DialectHolder.defaultDialect.getDbDataType(column));
		}
	}
}
