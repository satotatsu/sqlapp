/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

class SapHanaTypeIdentifierTest {
	@Test
	void quotesReservedWordsLeadingDigitsAndEmbeddedQuotes() {
		for (Dialect dialect : new Dialect[] { DialectHolder.defaultDialect, DialectHolder.cloudDialect }) {
			for (String keyword : new String[] { "condition", "CURRENT_SCHEMA", "limit", "select" }) {
				assertEquals("\"" + keyword + "\"", dialect.quote(keyword));
			}
			assertEquals("ORDINARY_NAME", dialect.quote("ORDINARY_NAME"));
			assertEquals("\"123NAME\"", dialect.quote("123NAME"));
			assertEquals("\"A\"\"B\"", dialect.quote("A\"B"));
			assertEquals("\"already quoted\"", dialect.quote("\"already quoted\""));
		}
	}

	@Test
	void distinguishesUnsignedTinyintFromSignedByte() {
		for (Dialect dialect : new Dialect[] { DialectHolder.defaultDialect, DialectHolder.cloudDialect }) {
			Column column = new Column("VALUE");
			assertTrue(dialect.setDbType("TINYINT", null, null, column));
			assertEquals(DataType.UTINYINT, column.getDataType());
			assertEquals("SMALLINT", dialect.createSqlBuilder()
					.addTypeDefinition(new Column("V").setDataType(DataType.TINYINT)).toString());
			assertEquals("TINYINT", dialect.createSqlBuilder().addTypeDefinition(column).toString());
		}
	}

	@Test
	void validatesDecimalBoundariesBeforeSqlGeneration() {
		for (Dialect dialect : new Dialect[] { DialectHolder.defaultDialect, DialectHolder.cloudDialect }) {
			Column column = new Column("AMOUNT").setDataType(DataType.DECIMAL).setLength(38L).setScale(38);
			assertEquals("DECIMAL(38,38)", dialect.createSqlBuilder().addTypeDefinition(column).toString());
			assertEquals("DECIMAL", dialect.createSqlBuilder()
					.addTypeDefinition(new Column("N").setDataType(DataType.DECIMAL)).toString());
			assertEquals("DECIMAL(8,2)",
					dialect.createSqlBuilder()
							.addTypeDefinition(new Column("N").setDataType(DataType.NUMERIC).setLength(8L).setScale(2))
							.toString());
			for (long precision : new long[] { 0, 39 }) {
				column.setLength(precision);
				assertThrows(IllegalArgumentException.class,
						() -> dialect.createSqlBuilder().addTypeDefinition(column));
			}
			column.setLength(4L);
			for (int scale : new int[] { -1, 5 }) {
				column.setScale(scale);
				assertThrows(IllegalArgumentException.class,
						() -> dialect.createSqlBuilder().addTypeDefinition(column));
			}
		}
	}
}
