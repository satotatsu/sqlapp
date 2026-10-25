/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2ColumnStorageTest {
	private Map<String, String> fields() {
		return new HashMap<>(Map.of("column_name", "DATA", "table_name", "T", "schema_name", "APP", "TYPENAME", "BLOB",
				"TYPESCHEMA", "SYSIBM", "NULLS", "N", "LENGTH", "2000", "LOGGED", "N", "COMPACT", "Y", "INLINE_LENGTH",
				"100"));
	}

	private Column read(Map<String, String> fields) throws SQLException {
		return new Db2_1050ColumnReader(DialectHolder.Db2_1110Dialect).createColumn(Db2MetadataParityTest.row(fields));
	}

	private String create(Column column) throws Exception {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(column);
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		SqlFactory<Table> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		return factory.createSql(restored).getFirst().getSqlText().replaceAll("\\s+", " ");
	}

	@Test
	void loggingCompactionInlineBytesAndCollationSchemaSurviveXml() throws Exception {
		var fields = fields();
		fields.put("COLLATIONSCHEMA", "SYSIBM");
		Column column = read(fields);
		assertEquals("2000", column.getSpecifics().get("DB2_LOB_OCTET_LENGTH"));
		assertEquals("SYSIBM", column.getSpecifics().get("COLLATIONSCHEMA"));
		assertTrue(create(column).contains("BLOB(2000) NOT LOGGED COMPACT INLINE LENGTH 100 NOT NULL"));
		fields.putAll(Map.of("LOGGED", "Y", "COMPACT", "N"));
		assertTrue(create(read(fields)).contains("LOGGED NOT COMPACT INLINE LENGTH 100"));
	}

	@Test
	void unicodeInlineValidationUsesByteLengthInsteadOfDeclaredUnits() throws Exception {
		var fields = fields();
		fields.putAll(Map.of("TYPENAME", "CLOB", "LENGTH", "4000", "CODEPAGE", "1208", "TYPESTRINGUNITS", "CODEUNITS32",
				"STRINGUNITSLENGTH", "1000", "INLINE_LENGTH", "80"));
		assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
		fields.put("INLINE_LENGTH", "92");
		assertTrue(create(read(fields)).contains("CLOB(1000 CODEUNITS32) NOT LOGGED COMPACT INLINE LENGTH 92"));
	}

	@Test
	void xmlSupportsInlineStorageWithoutLobLoggingOptions() throws Exception {
		var fields = fields();
		fields.put("TYPENAME", "XML");
		fields.remove("LOGGED");
		fields.remove("COMPACT");
		fields.put("INLINE_LENGTH", "292");
		assertTrue(create(read(fields)).contains("XML INLINE LENGTH 292"));
		fields.put("INLINE_LENGTH", "291");
		assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
	}

	@Test
	void invalidFlagsLengthsAndWrongTypesFailAndZeroMeansNoInlineClause() throws Exception {
		for (String bad : List.of("91", "-1", "32674", "bad")) {
			var fields = fields();
			fields.put("INLINE_LENGTH", bad);
			assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
		}
		var fields = fields();
		fields.put("LOGGED", "?");
		assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
		fields.put("LOGGED", "Y");
		fields.put("COMPACT", "?");
		assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
		fields.put("COMPACT", "N");
		fields.put("TYPENAME", "INTEGER");
		assertThrows(IllegalArgumentException.class, () -> create(read(fields)));
		fields.put("TYPENAME", "BLOB");
		fields.put("INLINE_LENGTH", "0");
		assertFalse(create(read(fields)).contains("INLINE LENGTH"));
	}

	@Test
	void twentyTablesStillUseOneBulkColumnQuery() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2_1050ColumnReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				for (int i = 0; i < 20; i++)
					try {
						var fields = fields();
						fields.put("table_name", "T" + i);
						handler.handleResultSetNext(Db2MetadataParityTest.row(fields));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		var columns = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(20, columns.size());
		assertTrue(columns.stream().allMatch(c -> "100".equals(c.getSpecifics().get("INLINE_LENGTH"))));
	}
}
