package com.sqlapp.data.db.dialect.oracle;

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

class OracleDataTypeMatrixTest {
	@org.junit.jupiter.api.Test
	void generatesPortableDoubleWithoutSourceTypeName() {
		var dialect = DialectResolver.getInstance().getDialect("oracle", 19, 0, null);
		for (String nativeName : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
			var column = new Column("value").setDataType(DataType.DOUBLE).setDataTypeName(nativeName);
			assertEquals("BINARY_DOUBLE",
					dialect.createSqlBuilder().addTypeDefinition(column).toString().toUpperCase(Locale.ROOT),
					nativeName);
			assertEquals(nativeName, column.getDataTypeName());
		}
	}

	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource({ "binary_double, DOUBLE, BINARY_DOUBLE", "binary_float, REAL, BINARY_FLOAT", "float, FLOAT, FLOAT(126)",
			"varchar2(12), VARCHAR, VARCHAR2(12)", "char(7), CHAR, CHAR(7)", "nvarchar2(12), NVARCHAR, NVARCHAR2(12)",
			"raw(12), VARBINARY, RAW(12)", "'number(12,3)', DECIMAL, 'NUMBER(12,3)'", "date, DATETIME, DATE",
			"timestamp(3), TIMESTAMP, TIMESTAMP(3)", "clob, CLOB, CLOB", "blob, BLOB, BLOB",
			"XMLType, SQLXML, XMLTYPE" })
	void resolvesAndGeneratesNativeTypes(String input, DataType expectedType, String expectedSql) {
		for (int[] version : new int[][] { { 11, 2 }, { 19, 0 }, { 23, 0 } }) {
			var dialect = DialectResolver.getInstance().getDialect("oracle", version[0], version[1], null);
			String context = dialect.getClass().getSimpleName() + ": " + input;
			assertEquals("oracle", dialect.getSimpleName(), context);
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
