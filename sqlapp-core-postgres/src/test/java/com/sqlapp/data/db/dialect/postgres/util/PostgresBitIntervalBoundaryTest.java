package com.sqlapp.data.db.dialect.postgres.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class PostgresBitIntervalBoundaryTest {
	@Test
	void preservesUnboundedVarbitAliasesAndFixedBitLengths() {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
			for (String declaration : new String[] { "varbit", "bit varying", "bit varying(7)", "bit(7)" }) {
				Column column = new Column("value");
				assertTrue(dialect.setDbType(declaration, null, null, column), declaration);
				boolean bounded = declaration.contains("(");
				assertEquals(declaration.startsWith("bit(") ? DataType.BINARY : DataType.VARBINARY, column.getDataType());
				if (bounded) assertEquals(7L, column.getLength()); else assertNull(column.getLength());
				String expected = declaration.startsWith("bit(") ? "BIT(7)" : bounded ? "VARBIT(7)" : "VARBIT";
				assertEquals(expected, new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
			}
		}
	}

	@Test
	void preservesIntervalFieldsAndFractionalPrecisionWithoutLeadingPrecisionDefaults() {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
			for (DataType type : new DataType[] { DataType.INTERVAL, DataType.INTERVAL_YEAR, DataType.INTERVAL_MONTH,
					DataType.INTERVAL_DAY, DataType.INTERVAL_HOUR, DataType.INTERVAL_MINUTE, DataType.INTERVAL_SECOND,
					DataType.INTERVAL_YEAR_TO_MONTH, DataType.INTERVAL_DAY_TO_HOUR, DataType.INTERVAL_DAY_TO_MINUTE,
					DataType.INTERVAL_DAY_TO_SECOND, DataType.INTERVAL_HOUR_TO_MINUTE, DataType.INTERVAL_HOUR_TO_SECOND,
					DataType.INTERVAL_MINUTE_TO_SECOND }) {
				Column column = new Column("value");
				assertTrue(dialect.setDbType(type.getTypeName(), null, null, column), type.name());
				assertEquals(type, column.getDataType()); assertNull(column.getLength(), type.name());
				assertEquals(type.getTypeName(), new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
				if (type == DataType.INTERVAL || type.getTypeName().endsWith("SECOND")) {
					for (int precision : new int[] { 0, 3, 6 }) {
						Column parsed = new Column("value");
						assertTrue(dialect.setDbType(type.getTypeName() + "(" + precision + ")[][]", null, null, parsed));
						assertEquals((long) precision, parsed.getLength());
						assertNull(parsed.getScale());
						assertEquals(2, parsed.getArrayDimension());
						column.setLength(precision).setArrayDimension(2);
						assertEquals(type.getTypeName() + "(" + precision + ")[][]", new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
					}
					assertThrows(IllegalArgumentException.class, () -> new PostgresSqlBuilder(dialect).typeDefinition(column.setLength(7)));
				}
			}
		}
	}
}
