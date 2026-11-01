/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.db.datatype.DataType;

class PhoenixDataTypeTest {
	private Dialect dialect(boolean modern) {
		return modern ? DialectHolder.defaultDialect5_3_1 : DialectHolder.defaultDialect;
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void retainsThirtyEightDigitDecimalsThroughXmlAndDdl(boolean modern) throws Exception {
		final Dialect dialect = dialect(modern);
		final Column column = new Column("amount");
		dialect.setDbType("DECIMAL", 38L, 38, column);
		assertEquals(DataType.DECIMAL, column.getDataType());
		assertEquals(38L, column.getLength());
		assertEquals(38, column.getScale());
		final Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
		assertEquals("DECIMAL(38,38)", dialect.createSqlBuilder().definition(restored).toString());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void registersCharUsingUtf8(boolean modern) {
		assertEquals("UTF-8", dialect(modern).getDbDataTypes().getDbType(DataType.CHAR).getCharset());
	}
}
