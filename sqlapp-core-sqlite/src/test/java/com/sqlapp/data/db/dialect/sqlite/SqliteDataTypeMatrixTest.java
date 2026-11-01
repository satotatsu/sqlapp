package com.sqlapp.data.db.dialect.sqlite;

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

class SqliteDataTypeMatrixTest {
	@ParameterizedTest
	@CsvSource({ "INTEGER, BIGINT, INTEGER", "REAL, DOUBLE, REAL", "DOUBLE PRECISION, DOUBLE, REAL",
			"TEXT, VARCHAR, TEXT", "BLOB, BLOB, BLOB" })
	void resolvesAndGeneratesNativeTypes(String input, DataType type, String expected) throws Exception {
		for (int[] version : new int[][] { { 3, 15 }, { 3, 37 }, { 3, 53 } }) {
			var dialect = DialectResolver.getInstance().getDialect("SQLite", version[0], version[1], null);
			assertEquals("sqlite", dialect.getSimpleName().toLowerCase(java.util.Locale.ROOT));
			var column = new Column("VALUE");
			assertTrue(dialect.setDbType(input, null, null, column), input);
			assertEquals(type, column.getDataType(), input);
			assertEquals(expected, dialect.createSqlBuilder().addTypeDefinition(column).toString());
			var table = new Table("TYPE_MATRIX");
			table.getColumns().add(column);
			SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
			String ddl = factory.createSql(table).getFirst().getSqlText();
			assertTrue(ddl.contains(expected));
			executeInMemory(ddl);
		}
	}

	@Test
	void generatesPortableDoubleWithRetainedSourceAliases() {
		var dialect = DialectResolver.getInstance().getDialect("SQLite", 3, 53, null);
		for (String name : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
			var column = new Column("VALUE").setDataType(DataType.DOUBLE).setDataTypeName(name);
			assertEquals("REAL", dialect.createSqlBuilder().addTypeDefinition(column).toString());
			assertEquals(name, column.getDataTypeName());
		}
	}

	@Test
	void portableTypesGenerateExecutableStrictTable() throws Exception {
		var dialect = DialectResolver.getInstance().getDialect("SQLite", 3, 53, null);
		for (DataType type : new DataType[] { DataType.TINYINT, DataType.SMALLINT, DataType.INT, DataType.BIGINT }) {
			var column = new Column("VALUE").setDataType(type);
			assertEquals(type, dialect.getDbDataType(column).getDataType());
			assertEquals("INTEGER", dialect.createSqlBuilder().addTypeDefinition(column).toString());
		}
		var table = new Table("TYPE_MATRIX");
		table.getSpecifics().put("strict", "true");
		for (DataType type : new DataType[] { DataType.DOUBLE, DataType.BIGINT, DataType.VARCHAR, DataType.BLOB })
			table.getColumns().add(new Column(type.name()).setDataType(type));
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
		executeInMemory(factory.createSql(table).getFirst().getSqlText());
	}

	private static void executeInMemory(String ddl) throws java.sql.SQLException {
		try (java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
				java.sql.Statement statement = connection.createStatement()) {
			statement.execute(ddl);
		}
	}
}
