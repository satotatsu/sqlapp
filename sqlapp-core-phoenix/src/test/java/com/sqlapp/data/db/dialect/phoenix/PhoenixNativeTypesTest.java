/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class PhoenixNativeTypesTest {
	@ParameterizedTest
	@CsvSource({ "VARCHAR ARRAY[10],VARCHAR ARRAY[10]", "INTEGER[20],INTEGER ARRAY[20]",
			"UNSIGNED_TIMESTAMP ARRAY [ 3 ],UNSIGNED_TIMESTAMP ARRAY[3]",
			"DECIMAL(10;2) ARRAY[4],DECIMAL(10;2) ARRAY[4]" })
	void preservesDeclaredArrayCapacityThroughXml(String input, String expected) throws Exception {
		for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect,
				DialectHolder.defaultDialect5_3_1 }) {
			final var column = new Column("values");
			dialect.setDbType(java.sql.Types.ARRAY, input.replace(';', ','), null, null, column);
			assertEquals(1, column.getArrayDimension());
			assertTrue(column.getArrayDimensionUpperBound() > 0);
			final Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
			assertEquals(expected.replace(';', ','), dialect.createSqlBuilder().definition(restored).toString());
		}
	}

	@Test
	void rejectsInvalidArrayCapacitiesBeforeChangingTheColumn() {
		for (String input : new String[] { "INTEGER[0]", "INTEGER[-1]", "INTEGER[2147483648]", "INTEGER[bad]" }) {
			final var column = new Column("values").setDataType(DataType.VARCHAR);
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.defaultDialect.setDbType(input, null, null, column), input);
			assertEquals(DataType.VARCHAR, column.getDataType());
		}
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlBuilder().definition(
				new Column("bad").setDataType(DataType.INT).setArrayDimension(1).setArrayDimensionUpperBound(-1)));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlBuilder().definition(
				new Column("bad").setDataType(DataType.INT).setArrayDimension(1).setArrayDimensionLowerBound(2)));
	}

	@Test
	void rejectsUnrepresentableGeneratedColumnSemantics() {
		final var dialect = DialectHolder.defaultDialect;
		assertThrows(UnsupportedOperationException.class, () -> dialect.createSqlBuilder()
				.definition(new Column("id").setDataType(DataType.BIGINT).setIdentity(true)));
		assertThrows(UnsupportedOperationException.class, () -> dialect.createSqlBuilder()
				.definition(new Column("calc").setDataType(DataType.INT).setFormula("1 + 1")));
	}

	@ParameterizedTest
	@CsvSource({ "UNSIGNED_FLOAT,REAL", "UNSIGNED_DOUBLE,DOUBLE", "UNSIGNED_DATE,DATE", "UNSIGNED_TIME,TIME",
			"UNSIGNED_TIMESTAMP,TIMESTAMP" })
	void preservesNativeEncodingsThroughXmlAndDdl(String name, DataType expected) throws Exception {
		for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect,
				DialectHolder.defaultDialect5_3_1 }) {
			final var column = new Column("value");
			assertTrue(dialect.setDbType(name.toLowerCase(java.util.Locale.ROOT), null, null, column));
			assertEquals(expected, column.getDataType());
			assertEquals(name, column.getDataTypeName());
			final Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
			assertEquals(name, dialect.createSqlBuilder().definition(restored).toString());
		}
	}

	@ParameterizedTest
	@CsvSource({ "INTEGER ARRAY,INTEGER ARRAY", "VARCHAR[],VARCHAR ARRAY", "UNSIGNED_FLOAT ARRAY,UNSIGNED_FLOAT ARRAY",
			"DECIMAL(10;2) ARRAY,DECIMAL(10;2) ARRAY" })
	void preservesArrayElementTypes(String input, String expected) throws Exception {
		input = input.replace(';', ',');
		expected = expected.replace(';', ',');
		final var dialect = DialectHolder.defaultDialect;
		final var column = new Column("values");
		dialect.setDbType(java.sql.Types.ARRAY, input, null, null, column);
		assertEquals(1, column.getArrayDimension());
		final Column restored = SchemaUtils.readXml(new java.io.StringReader(column.asXml()));
		assertEquals(expected, dialect.createSqlBuilder().definition(restored).toString());
	}

	@Test
	void rejectsUnsupportedArrayShapes() {
		final var dialect = DialectHolder.defaultDialect;
		assertThrows(IllegalArgumentException.class,
				() -> dialect.setDbType("INTEGER[][]", null, null, new Column("bad")));
		assertThrows(IllegalArgumentException.class,
				() -> dialect.setDbType("VARBINARY ARRAY", null, null, new Column("bad")));
		assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder()
				.definition(new Column("bad").setDataType(DataType.INT).setArrayDimension(2)));
		assertEquals("FLOAT",
				dialect.createSqlBuilder().definition(new Column("normal").setDataType(DataType.REAL)).toString());
	}
}
