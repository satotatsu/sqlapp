package com.sqlapp.data.db.dialect.informix;

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

class InformixDataTypeMatrixTest {
	@ParameterizedTest
	@CsvSource({ "smallint, SMALLINT, SMALLINT", "integer, INT, INTEGER", "int8, BIGINT, INT8",
			"bigint, BIGINT, BIGINT", "smallfloat, REAL, SMALLFLOAT", "real, REAL, SMALLFLOAT", "float, DOUBLE, FLOAT",
			"double precision, DOUBLE, FLOAT", "char(12), CHAR, CHAR(12)", "varchar(12), VARCHAR, VARCHAR(12)",
			"lvarchar(12), LONGVARCHAR, LVARCHAR(12)", "byte, BINARY, BYTE", "text, CLOB, TEXT",
			"boolean, BOOLEAN, BOOLEAN", "'decimal(12,3)', DECIMAL, 'DECIMAL(12,3)'",
			"decimal(12), DECIMALFLOAT, DECIMAL(12)", "decimal, DECIMALFLOAT, DECIMAL(16)" })
	void resolvesAndGeneratesNativeTypes(String input, DataType type, String expected) {
		for (int[] version : new int[][] { { 11, 70 }, { 12, 10 }, { 14, 10 }, { 15, 0 } }) {
			var dialect = DialectResolver.getInstance().getDialect("Informix Dynamic Server", version[0], version[1],
					null);
			assertEquals("informix", dialect.getSimpleName().toLowerCase(java.util.Locale.ROOT));
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
	void generatesPortableDoubleWithRetainedSourceAliases() {
		var dialect = DialectResolver.getInstance().getDialect("Informix Dynamic Server", 14, 10, null);
		for (String name : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
			var column = new Column("VALUE").setDataType(DataType.DOUBLE).setDataTypeName(name);
			assertEquals("FLOAT", dialect.createSqlBuilder().addTypeDefinition(column).toString());
			assertEquals(name, column.getDataTypeName());
		}
	}

	@Test
	void portableTemporalTypesGenerateInformixDeclarations() {
		var dialect = DialectHolder.defaultDialect;
		assertEquals("DATETIME HOUR TO SECOND", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.TIME)).toString());
		assertEquals("DATETIME YEAR TO SECOND", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.DATETIME)).toString());
		assertEquals("DATETIME YEAR TO FRACTION(5)", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.TIMESTAMP)).toString());
		assertEquals("DECIMAL(16)", dialect.createSqlBuilder()
				.addTypeDefinition(new Column("VALUE").setDataType(DataType.DECIMALFLOAT)).toString());
	}
}
