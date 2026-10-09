package com.sqlapp.data.db.dialect.postgres.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class PostgresNumericBoundaryTest {
	@Test
	void resolvesNegativeScaleTypeNamesFrom15() {
		for (int major : new int[] { 15, 18 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
			for (String name : new String[] { "numeric", "decimal" }) {
				Column column = new Column("value");
				assertTrue(dialect.setDbType(name + "(2,-3)[]", null, null, column));
				assertNotEquals(DataType.OTHER, column.getDataType());
				assertEquals(2L, column.getLength());
				assertEquals(-3, column.getScale());
				assertEquals(1, column.getArrayDimension());
			}
		}
	}
	private PostgresSqlBuilder builder(int major) {
		return new PostgresSqlBuilder(DialectResolver.getInstance().getDialect("postgres", major, 0, null));
	}

	@Test
	void preservesUnconstrainedTypesAndArrays() {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			for (DataType type : new DataType[] { DataType.NUMERIC, DataType.DECIMAL, DataType.VARCHAR }) {
				assertEquals(type.name(), builder(major).typeDefinition(type, null, null, null).toString());
				for (int dimension : new int[] { 0, 1, 2 }) {
					Column column = new Column("value").setDataType(type).setArrayDimension(dimension);
					assertEquals(type.name() + "[]".repeat(dimension), builder(major).typeDefinition(column).toString());
				}
			}
		}
	}

	@Test
	void preservesExtendedScaleAndRejectsUnsupportedOrInvalidDeclarations() {
		for (int major : new int[] { 15, 18 }) {
			assertEquals("NUMERIC(2,-3)[]", builder(major).typeDefinition(new Column("value")
					.setDataType(DataType.NUMERIC).setLength(2L).setScale(-3).setArrayDimension(1)).toString());
			assertEquals("NUMERIC(3,5)", builder(major).typeDefinition(new Column("value")
					.setDataType(DataType.NUMERIC).setLength(3L).setScale(5)).toString());
		}
		for (int scale : new int[] { -3, 5 }) {
			assertThrows(IllegalArgumentException.class, () -> builder(11).typeDefinition(new Column("value")
					.setDataType(DataType.NUMERIC).setLength(2L).setScale(scale)));
		}
		for (long precision : new long[] { 0, 1001 }) {
			assertThrows(IllegalArgumentException.class, () -> builder(15).typeDefinition(new Column("value")
					.setDataType(DataType.NUMERIC).setLength(precision)));
		}
		for (int scale : new int[] { -1001, 1001 }) {
			assertThrows(IllegalArgumentException.class, () -> builder(15).typeDefinition(new Column("value")
					.setDataType(DataType.NUMERIC).setLength(2L).setScale(scale)));
		}
		assertThrows(IllegalArgumentException.class, () -> builder(15).typeDefinition(new Column("value")
				.setDataType(DataType.NUMERIC).setScale(3)));
	}
}
