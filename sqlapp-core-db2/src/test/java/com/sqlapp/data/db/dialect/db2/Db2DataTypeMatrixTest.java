package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class Db2DataTypeMatrixTest {
	@org.junit.jupiter.api.Test
	void portableSignedTinyIntRetainsItsFullRange() {
		var dialect = DialectResolver.getInstance().getDialect("db2", 12, 1, null);
		assertEquals("SMALLINT", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("C").setDataType(DataType.TINYINT)).toString().toUpperCase(Locale.ROOT));
	}

	@org.junit.jupiter.api.Test
	void nativeVarbinaryReplacesTheTypeResolvedDuringBaseInitialization() {
		var dialect = DialectResolver.getInstance().getDialect("db2", 11, 1, null);
		var types = dialect.getDbDataTypes();
		assertEquals("VARBINARY", types.getDbType(DataType.VARBINARY).getTypeName());
		assertSame(types.getDbType(DataType.VARBINARY, 100L), types.getDbType(DataType.VARBINARY));
	}

	@ParameterizedTest(name = "Db2 {0}.{1}: {2}")
	@CsvSource({ "10, 5, VARCHAR(1000) FOR BIT DATA", "11, 1, VARBINARY(1000)", "12, 1, VARBINARY(1000)" })
	void deprecatedLongVarbinaryUsesTheVersionVarbinary(int major, int minor, String expectedSql) {
		var dialect = DialectResolver.getInstance().getDialect("db2", major, minor, null);
		var types = dialect.getDbDataTypes();
		assertSame(types.getDbType(DataType.VARBINARY), types.getDbType(DataType.LONGVARBINARY));
		assertSame(types.getDbType(DataType.VARBINARY), types.getDbType(DataType.LONGVARBINARY, 1000L));
		assertEquals(expectedSql,
				dialect.createSqlBuilder()
						.addTypeDefinition(new Column("C").setDataType(DataType.LONGVARBINARY).setLength(1000L))
						.toString().toUpperCase(Locale.ROOT));
	}

	@ParameterizedTest(name = "Db2 {0}.{1} {2}: {3}")
	@CsvSource({ "10, 5, VARBINARY, VARCHAR(32672) FOR BIT DATA", "10, 5, LONGVARBINARY, VARCHAR(32672) FOR BIT DATA",
			"11, 1, VARBINARY, VARBINARY(32672)", "11, 1, LONGVARBINARY, VARBINARY(32672)",
			"12, 1, VARBINARY, VARBINARY(32672)", "12, 1, LONGVARBINARY, VARBINARY(32672)" })
	void binaryStringWithoutLengthUsesTheMaximumLength(int major, int minor, DataType type, String expectedSql) {
		var dialect = DialectResolver.getInstance().getDialect("db2", major, minor, null);
		assertEquals(expectedSql, dialect.createSqlBuilder().addTypeDefinition(new Column("C").setDataType(type))
				.toString().toUpperCase(Locale.ROOT));
	}

	@org.junit.jupiter.api.Test
	void generatesPortableDoubleWithoutSourceTypeName() {
		var dialect = DialectResolver.getInstance().getDialect("db2", 11, 5, null);
		for (String nativeName : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
			var column = new Column("value").setDataType(DataType.DOUBLE).setDataTypeName(nativeName);
			assertEquals("DOUBLE",
					dialect.createSqlBuilder().addTypeDefinition(column).toString().toUpperCase(Locale.ROOT),
					nativeName);
			assertEquals(nativeName, column.getDataTypeName());
		}
	}

	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource({ "double, DOUBLE, DOUBLE", "real, REAL, REAL", "smallint, SMALLINT, SMALLINT", "integer, INT, INTEGER",
			"bigint, BIGINT, BIGINT", "varchar(12), VARCHAR, VARCHAR(12)", "char(7), CHAR, CHAR(7)",
			"'decimal(12,3)', DECIMAL, 'DECIMAL(12,3)'", "date, DATE, DATE", "time, TIME, TIME",
			"timestamp(3), TIMESTAMP, TIMESTAMP(3)", "xml, SQLXML, XML", "xmlvarchar(100), SQLXML, XML",
			"xmlclob(100), SQLXML, XML" })
	void resolvesAndGeneratesNativeTypes(String input, DataType expectedType, String expectedSql) {
		for (int[] version : new int[][] { { 10, 5 }, { 11, 5 }, { 12, 1 } }) {
			var dialect = DialectResolver.getInstance().getDialect("db2", version[0], version[1], null);
			String context = dialect.getClass().getSimpleName() + ": " + input;
			assertEquals("db2", dialect.getSimpleName(), context);
			var column = new Column("value");
			assertTrue(dialect.setDbType(input, null, null, column), context);
			assertEquals(expectedType, column.getDataType(), context);
			String actual = dialect.createSqlBuilder().addTypeDefinition(column).toString().toUpperCase(Locale.ROOT);
			assertEquals(expectedSql, actual, context);
			var table = new Table("type_matrix");
			table.getColumns().add(column);
			SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
			String ddl = factory.createSql(table).getFirst().getSqlText().toUpperCase(Locale.ROOT);
			assertTrue(ddl.contains(expectedSql), context + "\n" + ddl);
		}
	}
}
