package com.sqlapp.data.db.dialect.sybase;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SybaseDataTypeMatrixTest {
	@ParameterizedTest
	@CsvSource({ "tinyint, UTINYINT, TINYINT", "smallint, SMALLINT, SMALLINT", "int, INT, INT",
			"bigint, BIGINT, BIGINT", "real, REAL, REAL", "float, FLOAT, FLOAT(48)", "float(24), FLOAT, FLOAT(24)",
			"double precision, DOUBLE, DOUBLE PRECISION", "'numeric(12,3)', NUMERIC, 'NUMERIC(12,3)'",
			"'decimal(12,3)', DECIMAL, 'DECIMAL(12,3)'", "numeric, NUMERIC, 'NUMERIC(18,0)'",
			"decimal, DECIMAL, 'DECIMAL(18,0)'", "char(12), CHAR, CHAR(12)", "varchar(12), VARCHAR, VARCHAR(12)",
			"binary(12), BINARY, BINARY(12)", "varbinary(12), VARBINARY, VARBINARY(12)", "text, LONGVARCHAR, TEXT",
			"image, BLOB, IMAGE", "bit, BOOLEAN, BIT", "unichar(12), NCHAR, UNICHAR(12)",
			"univarchar(12), NVARCHAR, UNIVARCHAR(12)", "unichar ( 12 ), NCHAR, UNICHAR(12)", "unitext, NCLOB, UNITEXT",
			"datetime, DATETIME, DATETIME", "timestamp, ROWVERSION, TIMESTAMP",
			"unsigned smallint, USMALLINT, UNSIGNED SMALLINT", "unsigned int, UINT, UNSIGNED INT",
			"unsigned bigint, UBIGINT, UNSIGNED BIGINT", "usmallint, USMALLINT, UNSIGNED SMALLINT",
			"uint, UINT, UNSIGNED INT", "ubigint, UBIGINT, UNSIGNED BIGINT" })
	void resolvesAndGeneratesNativeTypes(String input, DataType type, String expected) {
		for (int major : new int[] { 15, 16 }) {
			var dialect = DialectResolver.getInstance().getDialect("Adaptive Server Enterprise", major, 0, null);
			assertEquals("sybase", dialect.getSimpleName());
			var column = new Column("VALUE");
			assertTrue(dialect.setDbType(input, null, null, column), input);
			assertEquals(type, column.getDataType(), input);
			assertEquals(expected, dialect.createSqlBuilder().addTypeDefinition(column).toString());
			var table = new Table("TYPE_MATRIX");
			table.getColumns().add(column);
			SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
			assertTrue(factory.createSql(table).getFirst().getSqlText().contains(expected));
		}
	}

	@Test
	void portableTypesUseAseSpellingsAndPreserveLogicalSignedBytes() {
		var dialect = DialectHolder.defaultDialect;
		for (String source : new String[] { null, "DOUBLE", "DOUBLE PRECISION" })
			assertEquals("DOUBLE PRECISION",
					dialect.createSqlBuilder()
							.addTypeDefinition(new Column("VALUE").setDataType(DataType.DOUBLE).setDataTypeName(source))
							.toString());
		assertEquals("SMALLINT", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.TINYINT)).toString());
		assertEquals("VARCHAR(36)", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.UUID)).toString());
		assertEquals("UNITEXT", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.LONGNVARCHAR)).toString());
		for (String name : new String[] { null, "TIMESTAMP", "DATETIME" })
			assertEquals("DATETIME",
					dialect.createSqlBuilder()
							.addTypeDefinition(
									new Column("VALUE").setDataType(DataType.TIMESTAMP).setDataTypeName(name))
							.toString());
	}

	@Test
	void resolvesIdentityDeclarationsAndRetainsUnsignedRange() {
		var dialect = DialectHolder.defaultDialect;
		for (String input : new String[] { "INT IDENTITY", "smallint identity", "numeric(18,0) identity",
				"unsigned int identity" }) {
			var column = new Column("VALUE");
			assertTrue(dialect.setDbType(input, null, null, column), input);
			assertNotEquals(DataType.OTHER, column.getDataType(), input);
			assertTrue(column.isIdentity(), input);
		}
		assertEquals((short) 255,
				dialect.getDbDataTypes().getDbType(DataType.UTINYINT).getConverter().convertObject(255));
	}

	@Test
	void rejectsInvalidPrecisionAndUnsupportedVariant() {
		var dialect = DialectHolder.defaultDialect;
		for (DataType type : new DataType[] { DataType.NUMERIC, DataType.DECIMAL, DataType.FLOAT }) {
			for (int precision : new int[] { 0, 49 }) {
				var value = new Column("VALUE").setDataType(type).setLength(precision);
				assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().addTypeDefinition(value));
			}
		}
		var numeric = new Column("VALUE").setDataType(DataType.NUMERIC).setLength(2).setScale(3);
		assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().addTypeDefinition(numeric));
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlBuilder().addTypeDefinition(new Column("VALUE").setDataType(DataType.ANY_DATA)));
	}
}
