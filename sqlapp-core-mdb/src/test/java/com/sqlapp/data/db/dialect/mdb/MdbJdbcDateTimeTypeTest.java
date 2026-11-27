/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Types;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class MdbJdbcDateTimeTypeTest {
	@ParameterizedTest
	@ValueSource(strings = { "DATETIME", "TIMESTAMP" })
	void nativeAndJdbcDateTimeNamesRetainTheDateTimeHandler(String name) {
		var dialect = DialectHolder.defaultDialect;
		var column = new Column("誕生日");
		assertTrue(dialect.setDbType(Types.TIMESTAMP, name, null, null, column));
		assertEquals(DataType.DATETIME, column.getDataType());
		assertNotNull(dialect.getDbDataType(column));
		assertNotNull(dialect.getDbDataType(column).getJdbcTypeHandler());
	}
}
