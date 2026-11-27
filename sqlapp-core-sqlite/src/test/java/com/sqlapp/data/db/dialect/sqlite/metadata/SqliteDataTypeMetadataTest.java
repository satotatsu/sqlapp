package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;

class SqliteDataTypeMetadataTest {
	@ParameterizedTest
	@ValueSource(ints = { 15, 53 })
	void integerMetadataAndJdbcConversionRetainSigned64BitValues(int minor) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value INTEGER)");
			statement.execute("INSERT INTO t VALUES(9223372036854775807),(-9223372036854775808),(2147483648),(256)");
			var context = new ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "t");
			var column = new SqliteColumnReader(DialectHolder.defaultDialect)
					.doGetAll(connection, context, new ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor))
					.getFirst();
			assertEquals(DataType.BIGINT, column.getDataType());
			assertEquals("INTEGER", column.getSpecifics().get("sqlite.declaredType"));
			var handler = DialectHolder.defaultDialect.getDbDataType(column).getJdbcTypeHandler();
			try (var rows = statement.executeQuery("SELECT value FROM t ORDER BY rowid")) {
				for (long expected : new long[] { Long.MAX_VALUE, Long.MIN_VALUE, 2147483648L, 256L }) {
					assertTrue(rows.next());
					assertEquals(expected, handler.getObject(rows, 1));
				}
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "'FLOATING POINT', BIGINT", "'VARCHAR(12)', VARCHAR", "'DOUBLE PRECISION', DOUBLE", "BLOB, BLOB",
			"STRING, NUMERIC", "INT2, BIGINT", "BIGINT, BIGINT" })
	void preservesAffinityPrecedenceAndDeclaredNames(String declared, DataType expected) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value " + declared + ")");
			var context = new ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "t");
			var column = new SqliteColumnReader(DialectHolder.defaultDialect)
					.doGetAll(connection, context, new ProductVersionInfo().setMajorVersion(3).setMinorVersion(53))
					.getFirst();
			assertEquals(expected, column.getDataType());
			assertEquals(declared, column.getSpecifics().get("sqlite.declaredType"));
			assertEquals(declared,
					DialectHolder.defaultDialect.createSqlBuilder().addTypeDefinition(column).toString());
		}
	}
}
