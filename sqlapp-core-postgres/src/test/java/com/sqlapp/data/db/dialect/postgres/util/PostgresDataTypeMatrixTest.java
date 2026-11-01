package com.sqlapp.data.db.dialect.postgres.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class PostgresDataTypeMatrixTest {

	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource({ "smallint, SMALLINT, SMALLINT", "int2, SMALLINT, SMALLINT", "integer, INT, INT", "int4, INT, INT",
			"bigint, BIGINT, BIGINT", "int8, BIGINT, BIGINT", "real, REAL, FLOAT4", "float4, REAL, FLOAT4",
			"float8, DOUBLE, DOUBLE PRECISION", "double precision, DOUBLE, DOUBLE PRECISION",
			"boolean, BOOLEAN, BOOLEAN", "bool, BOOLEAN, BOOLEAN", "float, DOUBLE, DOUBLE PRECISION",
			"float(1), REAL, FLOAT4", "float(24), REAL, FLOAT4", "float(25), DOUBLE, DOUBLE PRECISION",
			"float(53), DOUBLE, DOUBLE PRECISION", "char(7), CHAR, CHAR(7)", "character(7), CHAR, CHAR(7)",
			"bpchar(7), CHAR, CHAR(7)", "varchar(7), VARCHAR, VARCHAR(7)", "character varying(7), VARCHAR, VARCHAR(7)",
			"text, VARCHAR, TEXT", "bytea, BLOB, BYTEA", "bit(7), BINARY, BIT(7)",
			"bit varying(7), VARBINARY, VARBIT(7)", "'numeric(7,2)', NUMERIC, 'NUMERIC(7,2)'", "date, DATE, DATE",
			"time(3), TIME, TIME(3)", "time(3) without time zone, TIME, TIME(3)",
			"timestamp(3) without time zone, TIMESTAMP, TIMESTAMP(3)",
			"time(3) with time zone, TIME_WITH_TIMEZONE, TIME(3) WITH TIME ZONE",
			"timestamp(3), TIMESTAMP, TIMESTAMP(3)",
			"timestamp(3) with time zone, TIMESTAMP_WITH_TIMEZONE, TIMESTAMP(3) WITH TIME ZONE",
			"timetz(3), TIME_WITH_TIMEZONE, TIME(3) WITH TIME ZONE",
			"timestamptz(3), TIMESTAMP_WITH_TIMEZONE, TIMESTAMP(3) WITH TIME ZONE", "uuid, UUID, UUID",
			"money, MONEY, MONEY", "xml, SQLXML, XML", "inet, INET, INET", "cidr, CIDR, CIDR",
			"macaddr, MACADDR, MACADDR", "point, POINT, POINT", "line, LINE, LINE", "lseg, LSEG, LSEG", "box, BOX, BOX",
			"path, PATH, PATH", "polygon, POLYGON, POLYGON", "circle, CIRCLE, CIRCLE" })
	void resolvesAndRecreatesBuiltInTypes(String input, DataType type, String expected) {
		for (int[] version : versions()) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", version[0], version[1], null);
			for (int dimension : new int[] { 0, 1, 2 }) {
				String suffix = "[]".repeat(dimension);
				String context = version[0] + "." + version[1] + ": " + input + suffix;
				var column = new Column("value");
				assertTrue(dialect.setDbType(input + suffix, null, null, column), context);
				assertEquals(type, column.getDataType(), context);
				assertEquals(dimension, column.getArrayDimension(), context);
				assertEquals(expected + suffix, new PostgresSqlBuilder(dialect).typeDefinition(column).toString(),
						context);
				var table = new Table("type_matrix");
				table.getColumns().add(column);
				SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
				String ddl = factory.createSql(table).getFirst().getSqlText();
				assertTrue(ddl.contains("value " + expected + suffix), context + "\n" + ddl);
				var recreated = new Column("value");
				assertTrue(dialect.setDbType(expected + suffix, null, null, recreated), context);
				assertEquals(type, recreated.getDataType(), context);
				assertEquals(column.getLength(), recreated.getLength(), context);
				assertEquals(column.getScale(), recreated.getScale(), context);
				assertEquals(dimension, recreated.getArrayDimension(), context);
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "9, 2, json, JSON", "9, 4, jsonb, JSONB", "18, 0, json, JSON", "18, 0, jsonb, JSONB" })
	void preservesVersionedJsonTypes(int major, int minor, String name, DataType type) {
		var dialect = DialectResolver.getInstance().getDialect("postgres", major, minor, null);
		for (int dimension : new int[] { 0, 1, 2 }) {
			String suffix = "[]".repeat(dimension);
			var column = new Column("value");
			assertTrue(dialect.setDbType(name + suffix, null, null, column));
			assertEquals(type, column.getDataType());
			assertEquals(dimension, column.getArrayDimension());
			assertEquals(type.name() + suffix, new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
		}
	}

	@ParameterizedTest
	@CsvSource({ "DOUBLE, DOUBLE PRECISION", "REAL, FLOAT4", "SQLXML, XML", "MACADDR, MACADDR", "LINE, LINE",
			"CIRCLE, CIRCLE", "INT, INT", "BIGINT, BIGINT", "BLOB, BYTEA", "UUID, UUID" })
	void generatesTypesFromPortableSchemaWithoutNativeNames(DataType type, String expected) {
		for (int[] version : versions()) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", version[0], version[1], null);
			for (int dimension : new int[] { 0, 1, 2 }) {
				var column = new Column("value").setDataType(type).setArrayDimension(dimension);
				assertEquals(expected + "[]".repeat(dimension),
						new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "11, abstime", "12, TIMESTAMP(0) WITH TIME ZONE", "18, TIMESTAMP(0) WITH TIME ZONE" })
	void replacesRemovedAbstimeOnlyFrom12(int major, String expected) {
		var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
		for (int dimension : new int[] { 0, 1, 2 }) {
			var column = new Column("value").setDataType(DataType.SMALLDATETIME).setArrayDimension(dimension);
			assertEquals(expected + "[]".repeat(dimension),
					new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
		}
	}

	private static int[][] versions() {
		return new int[][] { { 8, 0 }, { 8, 2 }, { 8, 3 }, { 8, 4 }, { 9, 0 }, { 9, 1 }, { 9, 2 }, { 9, 3 }, { 9, 4 },
				{ 9, 5 }, { 9, 6 }, { 10, 0 }, { 11, 0 }, { 12, 0 }, { 13, 0 }, { 14, 0 }, { 15, 0 }, { 16, 0 },
				{ 17, 0 }, { 18, 0 } };
	}
}
