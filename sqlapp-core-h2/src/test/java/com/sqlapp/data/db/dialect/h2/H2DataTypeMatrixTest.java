package com.sqlapp.data.db.dialect.h2;

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

class H2DataTypeMatrixTest {
	@org.junit.jupiter.api.Test
	void generatesPortableDoubleWithoutSourceTypeName() {
		var dialect = DialectResolver.getInstance().getDialect("h2", 2, 0, null);
		for (String nativeName : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
			var column = new Column("value").setDataType(DataType.DOUBLE).setDataTypeName(nativeName);
			assertEquals("DOUBLE",
					dialect.createSqlBuilder().addTypeDefinition(column).toString().toUpperCase(Locale.ROOT),
					nativeName);
			assertEquals(nativeName, column.getDataTypeName());
		}
	}

	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource({ "double precision, DOUBLE, DOUBLE", "real, REAL, REAL", "smallint, SMALLINT, SMALLINT",
			"integer, INT, INTEGER", "bigint, BIGINT, BIGINT", "varchar(12), VARCHAR, VARCHAR(12)",
			"char(7), CHAR, CHAR(7)", "varbinary(12), VARBINARY, VARBINARY(12)",
			"'decimal(12,3)', DECIMAL, 'DECIMAL(12,3)'", "date, DATE, DATE", "time(3), TIME, TIME(3)",
			"timestamp(3), TIMESTAMP, TIMESTAMP(3)", "bool, BOOLEAN, BOOLEAN", "bit, BOOLEAN, BOOLEAN",
			"blob, BLOB, BLOB" })
	void resolvesAndGeneratesNativeTypes(String input, DataType expectedType, String expectedSql) throws Exception {
		for (int[] version : new int[][] { { 1, 4 }, { 2, 2 } }) {
			var dialect = DialectResolver.getInstance().getDialect("h2", version[0], version[1], null);
			String context = dialect.getClass().getSimpleName() + ": " + input;
			assertEquals("h2", dialect.getSimpleName(), context);
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
			if (version[0] == 2) {
				executeInMemory(ddl);
			}
		}
	}

	private static void executeInMemory(String ddl) throws java.sql.SQLException {
		try (java.sql.Connection connection = java.sql.DriverManager
				.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID(), "SA", "");
				java.sql.Statement statement = connection.createStatement()) {
			statement.execute(ddl);
		}
	}
}
