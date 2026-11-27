package com.sqlapp.data.db.dialect.firebird;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class FirebirdDataTypeMatrixTest {
	@ParameterizedTest
	@CsvSource({ "smallint, SMALLINT, SMALLINT", "integer, INT, INTEGER", "int, INT, INTEGER", "bigint, BIGINT, BIGINT",
			"float, REAL, FLOAT", "real, REAL, FLOAT", "double precision, DOUBLE, DOUBLE PRECISION",
			"varchar(12), VARCHAR, VARCHAR(12)", "character varying(12), VARCHAR, VARCHAR(12)",
			"char(7), CHAR, CHAR(7)", "'numeric(12,3)', NUMERIC, 'NUMERIC(12,3)'",
			"'decimal(12,3)', DECIMAL, 'DECIMAL(12,3)'", "numeric, NUMERIC, 'NUMERIC(9,0)'",
			"decimal, DECIMAL, 'DECIMAL(9,0)'", "date, DATE, DATE", "time, TIME, TIME",
			"timestamp, TIMESTAMP, TIMESTAMP", "blob, BLOB, BLOB SUB_TYPE BINARY",
			"blob sub_type binary, BLOB, BLOB SUB_TYPE BINARY", "blob sub_type 0, BLOB, BLOB SUB_TYPE BINARY",
			"blob sub_type text, CLOB, BLOB SUB_TYPE TEXT", "blob sub_type 1, CLOB, BLOB SUB_TYPE TEXT" })
	void resolvesAndGeneratesNativeTypes(String name, DataType type, String expected) {
		for (int[] version : new int[][] { { 1, 5 }, { 2, 0 }, { 2, 5 }, { 3, 0 }, { 4, 0 }, { 5, 0 } }) {
			var dialect = DialectResolver.getInstance().getDialect("Firebird", version[0], version[1], null);
			assertEquals("firebird", dialect.getSimpleName());
			var column = new Column("VALUE");
			assertTrue(dialect.setDbType(name, null, null, column), name);
			assertEquals(type, column.getDataType(), dialect.getClass().getSimpleName() + ": " + name);
			assertEquals(expected,
					dialect.createSqlBuilder().addTypeDefinition(column).toString().toUpperCase(java.util.Locale.ROOT));
			var table = new Table("TYPE_MATRIX");
			table.getColumns().add(column);
			SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
			assertTrue(factory.createSql(table).getFirst().getSqlText().toUpperCase(java.util.Locale.ROOT)
					.contains(expected));
		}
	}

	@ParameterizedTest
	@CsvSource({ "3, 7, REAL, FLOAT", "3, 8, DOUBLE, DOUBLE PRECISION", "4, 24, REAL, FLOAT",
			"4, 25, DOUBLE, DOUBLE PRECISION", "5, 53, DOUBLE, DOUBLE PRECISION" })
	void respectsFloatPrecisionVersionBoundary(int major, int precision, DataType type, String expected) {
		var dialect = DialectResolver.getInstance().getDialect("Firebird", major, 0, null);
		var column = new Column("VALUE");
		assertTrue(dialect.setDbType("FLOAT(" + precision + ")", null, null, column));
		assertEquals(type, column.getDataType());
		assertEquals(expected, dialect.createSqlBuilder().addTypeDefinition(column).toString());
	}

	@ParameterizedTest
	@CsvSource({ "int128, DECIMAL, INT128", "decfloat, DECIMALFLOAT, DECFLOAT(34)",
			"decfloat(16), DECIMALFLOAT, DECFLOAT(16)", "decfloat(34), DECIMALFLOAT, DECFLOAT(34)",
			"binary(12), BINARY, BINARY(12)", "varbinary(12), VARBINARY, VARBINARY(12)",
			"time with time zone, TIME_WITH_TIMEZONE, TIME WITH TIME ZONE",
			"timestamp with time zone, TIMESTAMP_WITH_TIMEZONE, TIMESTAMP WITH TIME ZONE" })
	void resolvesVersion4Types(String name, DataType type, String expected) {
		for (int major : new int[] { 4, 5 }) {
			var dialect = DialectResolver.getInstance().getDialect("Firebird", major, 0, null);
			var column = new Column("VALUE");
			assertTrue(dialect.setDbType(name, null, null, column));
			assertEquals(type, column.getDataType(), name);
			assertEquals(expected, dialect.createSqlBuilder().addTypeDefinition(column).toString());
		}
	}

	@Test
	void generatesPortableDoubleAndLobsWithoutSourceNames() {
		for (int major : new int[] { 2, 3, 4, 5 }) {
			var dialect = DialectResolver.getInstance().getDialect("Firebird", major, 0, null);
			for (String name : new String[] { null, "DOUBLE", "DOUBLE PRECISION" })
				assertEquals("DOUBLE PRECISION",
						dialect.createSqlBuilder()
								.addTypeDefinition(
										new Column("VALUE").setDataType(DataType.DOUBLE).setDataTypeName(name))
								.toString());
			for (DataType type : new DataType[] { DataType.BLOB, DataType.CLOB })
				assertEquals("BLOB SUB_TYPE " + (type == DataType.BLOB ? "BINARY" : "TEXT"),
						dialect.createSqlBuilder().addTypeDefinition(new Column("VALUE").setDataType(type)).toString());
		}
	}

	@Test
	void recreatesMetadataFloatAndExplicitBlobSegments() {
		var dialect = DialectResolver.getInstance().getDialect("Firebird", 5, 0, null);
		var column = new Column("VALUE");
		com.sqlapp.data.db.dialect.firebird.metadata.FirebirdUtils.setDbType(column, 10, 0, 4, 0, 0, 0);
		assertEquals(DataType.REAL, column.getDataType());
		assertEquals("FLOAT", dialect.createSqlBuilder().addTypeDefinition(column).toString());
		for (int subtype : new int[] { 0, 1 }) {
			column = new Column("VALUE");
			com.sqlapp.data.db.dialect.firebird.metadata.FirebirdUtils.setDbType(column, 261, subtype, 8, 0, 0, 80);
			assertEquals(80L, column.getLength());
			assertEquals("BLOB SUB_TYPE " + (subtype == 0 ? "BINARY" : "TEXT") + " SEGMENT SIZE 80",
					dialect.createSqlBuilder().addTypeDefinition(column).toString());
			for (String invalid : new String[] { "0", "65536", "invalid" }) {
				column.getSpecifics().put(com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder.BLOB_SEGMENT_SIZE,
						invalid);
				Column value = column;
				assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().addTypeDefinition(value));
			}
		}
	}

	@Test
	void validatesModernPrecisionAndVersionBoundaries() {
		for (int major : new int[] { 3, 4, 5 }) {
			var dialect = DialectResolver.getInstance().getDialect("Firebird", major, 0, null);
			for (int precision : new int[] { 0, 15, 17, 35 }) {
				var column = new Column("VALUE").setDataType(DataType.DECIMALFLOAT).setLength(precision);
				assertThrows(IllegalArgumentException.class,
						() -> dialect.createSqlBuilder().addTypeDefinition(column));
			}
			var column = new Column("VALUE");
			dialect.setDbType("BOOLEAN", null, null, column);
			assertEquals(DataType.BOOLEAN, column.getDataType());
			for (String name : new String[] { "INT128", "BINARY(12)", "VARBINARY(12)", "DECFLOAT(16)" }) {
				if (major == 3) {
					dialect.setDbType(name, null, null, column);
					assertEquals(DataType.OTHER, column.getDataType(), name);
				}
			}
		}
		var legacy = DialectResolver.getInstance().getDialect("Firebird", 2, 5, null);
		assertEquals("DECIMAL(1,0)", legacy.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.BOOLEAN)).toString());
	}

	@Test
	void validatesNumericBoundariesAndArrayRestrictions() {
		for (int major : new int[] { 2, 3, 4, 5 }) {
			var dialect = DialectResolver.getInstance().getDialect("Firebird", major, 0, null);
			int maximum = major < 4 ? 18 : 38;
			for (DataType type : new DataType[] { DataType.NUMERIC, DataType.DECIMAL }) {
				assertEquals(type.name() + "(" + maximum + ",0)",
						dialect.createSqlBuilder()
								.addTypeDefinition(new Column("VALUE").setDataType(type).setLength(maximum).setScale(0))
								.toString());
				for (int precision : new int[] { 0, maximum + 1 }) {
					var value = new Column("VALUE").setDataType(type).setLength(precision);
					assertThrows(IllegalArgumentException.class,
							() -> dialect.createSqlBuilder().addTypeDefinition(value));
				}
				for (int scale : new int[] { -1, 3 }) {
					var value = new Column("VALUE").setDataType(type).setLength(2).setScale(scale);
					assertThrows(IllegalArgumentException.class,
							() -> dialect.createSqlBuilder().addTypeDefinition(value));
				}
			}
			var array = new Column("VALUE").setDataType(DataType.DOUBLE).setArrayDimension(2);
			array.getSpecifics().put(com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder.ARRAY_BOUNDS,
					"0:2,1:3");
			assertEquals("DOUBLE PRECISION[0:2,1:3]", dialect.createSqlBuilder().addTypeDefinition(array).toString());
			array.setDataType(DataType.BLOB);
			assertThrows(IllegalArgumentException.class, () -> dialect.createSqlBuilder().addTypeDefinition(array));
		}
	}
}
