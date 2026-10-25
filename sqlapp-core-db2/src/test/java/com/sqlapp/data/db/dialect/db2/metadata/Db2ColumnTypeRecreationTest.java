/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2ColumnTypeRecreationTest {
	private Map<String, String> fields(String type) {
		return new HashMap<>(Map.of("column_name", "C", "table_name", "T", "schema_name", "APP", "TYPENAME", type,
				"TYPESCHEMA", "SYSIBM", "NULLS", "Y", "LENGTH", "20", "SCALE", "0"));
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
	void resolvedUserTypeCannotBeMistakenForBuiltinWithSameName() throws Exception {
		var fields = fields("VARCHAR");
		fields.put("TYPESCHEMA", "Types.\"Q");
		fields.put("LENGTH", "0");
		// A custom type must not be reclassified by the later string-unit reader.
		fields.put("TYPESTRINGUNITS", "CODEUNITS32");
		fields.put("STRINGUNITSLENGTH", "5");
		Column column = read(fields);
		assertEquals(DataType.OTHER, column.getDataType());
		assertNull(column.getLength());
		assertEquals("\"Types.\"\"Q\".\"VARCHAR\"", column.getDataTypeName());
		assertTrue(create(column).contains("\"Types.\"\"Q\".\"VARCHAR\""));
	}

	@Test
	void declaredUnicodeUnitsAndBinaryCharacterAttributeSurviveXmlAndCreate() throws Exception {
		var fields = fields("VARCHAR");
		fields.putAll(Map.of("CODEPAGE", "1208", "TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5"));
		Column unicode = read(fields);
		assertEquals(Long.valueOf(5), unicode.getLength());
		assertEquals("CODEUNITS32", unicode.getStringUnits());
		assertTrue(create(unicode).contains("VARCHAR(5 CODEUNITS32)"));
		fields.putAll(Map.of("CODEPAGE", "0", "TYPESTRINGUNITS", "OCTETS", "STRINGUNITSLENGTH", "20"));
		assertTrue(create(read(fields)).contains("VARCHAR(20) FOR BIT DATA"));
		fields = fields("VARGRAPHIC");
		fields.putAll(Map.of("CODEPAGE", "1200", "TYPESTRINGUNITS", "CODEUNITS16", "STRINGUNITSLENGTH", "10"));
		assertTrue(create(read(fields)).contains("VARGRAPHIC(10 CODEUNITS16)"));
	}

	@Test
	void lobLengthsAndInvalidUnitsAreNotSilentlyLost() throws Exception {
		var fields = fields("BLOB");
		assertTrue(create(read(fields)).contains("BLOB(20)"));
		fields = fields("CLOB");
		fields.putAll(Map.of("CODEPAGE", "1208", "TYPESTRINGUNITS", "CODEUNITS32", "STRINGUNITSLENGTH", "5"));
		assertTrue(create(read(fields)).contains("CLOB(5 CODEUNITS32)"));
		Column invalid = new Column("BAD").setDataType(DataType.INT).setStringUnits("OCTETS").setLength(10);
		assertThrows(IllegalArgumentException.class, () -> create(invalid));
		Column binary = new Column("BAD").setDataType(DataType.VARCHAR).setLength(10).setStringUnits("CODEUNITS32");
		binary.getSpecifics().put("CODEPAGE", "0");
		assertThrows(IllegalArgumentException.class, () -> create(binary));
	}

	@Test
	void multipleTablesStillUseOneColumnQueryAndLegacyReaderKeepsOldRepresentation() throws Exception {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2_1050ColumnReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				for (int i = 0; i < 20; i++)
					try {
						var fields = fields("VARCHAR");
						fields.put("table_name", "T" + i);
						fields.put("TYPESCHEMA", "TYPES");
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
		assertTrue(columns.stream().allMatch(c -> c.getDataType() == DataType.OTHER));
		var fields = fields("VARCHAR");
		fields.put("TYPESCHEMA", "TYPES");
		assertEquals(DataType.VARCHAR, new Db2ColumnReader(DialectHolder.Db2_1010Dialect)
				.createColumn(Db2MetadataParityTest.row(fields)).getDataType());
	}
}
