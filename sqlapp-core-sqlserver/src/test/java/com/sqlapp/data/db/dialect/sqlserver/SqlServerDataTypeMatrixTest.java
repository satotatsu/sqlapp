package com.sqlapp.data.db.dialect.sqlserver;

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

class SqlServerDataTypeMatrixTest {
	@org.junit.jupiter.api.Test
	void generatesPortableDoubleWithoutSourceTypeName() {
		var dialect = DialectResolver.getInstance().getDialect("Microsoft SQL Server", 16, 0, null);
		for (String nativeName : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
			var column = new Column("value").setDataType(DataType.DOUBLE).setDataTypeName(nativeName);
			assertEquals("FLOAT(53)",
					dialect.createSqlBuilder().addTypeDefinition(column).toString().toUpperCase(Locale.ROOT),
					nativeName);
			assertEquals(nativeName, column.getDataTypeName());
		}
	}

	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource({ "real, REAL, REAL", "float(53), FLOAT, FLOAT(53)", "float, FLOAT, FLOAT(53)",
			"smallint, SMALLINT, SMALLINT", "int, INT, INT", "bigint, BIGINT, BIGINT",
			"varchar(12), VARCHAR, VARCHAR(12)", "nvarchar(12), NVARCHAR, NVARCHAR(12)",
			"varbinary(12), VARBINARY, VARBINARY(12)", "'decimal(12,3)', DECIMAL, 'DECIMAL(12,3)'", "date, DATE, DATE",
			"xml, SQLXML, XML" })
	void resolvesAndGeneratesNativeTypes(String input, DataType expectedType, String expectedSql) {
		for (int[] version : new int[][] { { 9, 0 }, { 10, 0 }, { 16, 0 } }) {
			var dialect = DialectResolver.getInstance().getDialect("Microsoft SQL Server", version[0], version[1],
					null);
			String context = dialect.getClass().getSimpleName() + ": " + input;
			assertEquals("sqlserver", dialect.getSimpleName(), context);
			var column = new Column("value");
			assertTrue(dialect.setDbType(input, null, null, column), context);
			if (input.equals("date") && version[0] < 10) {
				assertEquals(DataType.OTHER, column.getDataType(), context);
				continue;
			}
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
