/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;

class PhoenixFunctionSqlTest {
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void generatesNativeAggregateAndCurrentValueExpressions(boolean modern) throws Exception {
		final var dialect = modern ? DialectHolder.defaultDialect5_3_1 : DialectHolder.defaultDialect;
		assertEquals("SELECT COUNT(*)", dialect.createSqlBuilder().select().count()._add("(*)").toString());
		assertEquals("CURRENT_DATE()", dialect.getCurrentDateFunction());
		assertEquals("CURRENT_DATE()", dialect.getCurrentDateTimeFunction());
		assertEquals("CURRENT_TIME()", dialect.getCurrentTimeFunction());
		assertEquals("CAST(CURRENT_DATE() AS TIMESTAMP)", dialect.getCurrentTimestampFunction());
		for (DataType type : new DataType[] { DataType.DATE, DataType.TIME, DataType.TIMESTAMP }) {
			final String expression = dialect.getDbDataTypes().getDbType(type).getDefaultValueLiteral();
			final String expected = type == DataType.DATE ? "CURRENT_DATE()"
					: type == DataType.TIME ? "CURRENT_TIME()" : "CAST(CURRENT_DATE() AS TIMESTAMP)";
			assertEquals(expected, expression);
			final Column column = new Column("created").setDataType(type).setDefaultValue(expression);
			final Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
			assertTrue(dialect.createSqlBuilder().definition(restored).toString().contains("DEFAULT " + expected));
		}
	}
}
