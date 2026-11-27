/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2XmlIndexTest {
	private Map<String, String> fields(String name, String type) {
		var fields = new HashMap<String, String>(Map.of("schema_name", "IX.Q", "table_schema", "APP", "table_name", "T",
				"index_name", name, "UNIQUERULE", "D", "INDEXTYPE", type, "column_name", "DOC", "COLORDER", "A"));
		fields.putAll(Map.of("XML_PATTERN", "declare namespace b=\"urn:book\"; /b:book/title[@lang=\"it's\"]",
				"XML_TYPEMODEL", "R", "XML_DATATYPE", "VARCHAR", "XML_HASHED", "N", "XML_LENGTH", "100", "XML_SCALE",
				"0"));
		return fields;
	}

	private List<Index> read(List<Map<String, String>> rows, AtomicInteger count) {
		var reader = new Db2IndexReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				count.incrementAndGet();
				assertTrue(sql.toString().contains("SYSCAT.INDEXXMLPATTERNS"));
				assertTrue(sql.toString().contains("NOT IN ('XPTH','XRGN','XVIP')"));
				for (var row : rows)
					try {
						handler.handleResultSetNext(Db2MetadataParityTest.row(row));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		return reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
	}

	private Table table(Index index) {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("DOC", c -> c.setDataType(DataType.SQLXML));
		table.getIndexes().add(index);
		return table;
	}

	private List<String> sql(Index index) {
		SqlFactory<Index> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(index,
				SqlType.CREATE);
		return factory.createSql(index).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void bulkReadKeepsLogicalPatternsAndExcludesPhysicalSupportIndexes() throws Exception {
		List<Map<String, String>> rows = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			rows.add(fields("XML" + i, "XVIL"));
			rows.add(fields("PHYS" + i, "XVIP"));
		}
		rows.add(fields("PATH", "XPTH"));
		rows.add(fields("REGION", "XRGN"));
		AtomicInteger queries = new AtomicInteger();
		var indexes = read(rows, queries);
		assertEquals(20, indexes.size());
		assertEquals(1, queries.get());
		Table restored = SchemaUtils.readXml(new StringReader(table(indexes.getFirst()).asXml()));
		Index index = restored.getIndexes().getFirst();
		String ddl = sql(index).getFirst();
		assertTrue(ddl.contains("INDEX \"IX.Q\".XML0 ON APP.T"), ddl);
		assertTrue(ddl.contains(
				"GENERATE KEY USING XMLPATTERN 'declare namespace b=\"urn:book\"; /b:book/title[@lang=\"it''s\"]' AS SQL VARCHAR(100) REJECT INVALID VALUES"),
				ddl);
		assertEquals(index, index.clone());
	}

	@Test
	void supportedSqlTypesHashedStringsAndBothValueModesAreRecreated() {
		for (String type : List.of("DOUBLE", "INTEGER", "DATE", "TIMESTAMP", "DECIMAL", "VARCHAR")) {
			Index index = read(List.of(fields("X", "XVIL")), new AtomicInteger()).getFirst();
			table(index);
			index.getSpecifics().put("XML_DATATYPE", type);
			index.getSpecifics().put("XML_LENGTH", type.equals("DECIMAL") ? "18" : "100");
			index.getSpecifics().put("XML_SCALE", "4");
			index.getSpecifics().put("XML_TYPEMODEL", "Q");
			String expected = type.equals("DECIMAL") ? "DECIMAL(18,4)" : type.equals("VARCHAR") ? "VARCHAR(100)" : type;
			assertTrue(sql(index).getFirst().contains("AS SQL " + expected + " IGNORE INVALID VALUES"));
			if (type.equals("VARCHAR")) {
				index.getSpecifics().put("XML_HASHED", "Y");
				assertTrue(sql(index).getFirst().contains("AS SQL VARCHAR HASHED"));
			}
		}
	}

	@Test
	void incompleteOrUnsupportedXmlFormsRequireNativeDefinitions() {
		Index original = read(List.of(fields("X", "XVIL")), new AtomicInteger()).getFirst();
		table(original);
		for (var entry : Map.of("XML_PATTERN", " ", "XML_TYPEMODEL", "?", "XML_HASHED", "?", "XML_DATATYPE", "BLOB",
				"XML_LENGTH", "0").entrySet()) {
			Index invalid = original.clone();
			invalid.getSpecifics().put(entry.getKey(), entry.getValue());
			table(invalid);
			assertThrows(IllegalArgumentException.class, () -> sql(invalid));
		}
		Index invalid = original.clone();
		table(invalid);
		invalid.getColumns().getFirst().setOrder(Order.Desc);
		assertThrows(IllegalArgumentException.class, () -> sql(invalid));
		invalid.getDefinition()
				.add("CREATE INDEX IX.X ON APP.T(DOC) GENERATE KEY USING XMLPATTERN '/book' AS SQL DOUBLE");
		assertEquals(invalid.getDefinition(), sql(invalid));
		Index excluded = original.clone();
		table(excluded);
		excluded.getSpecifics().put("NULLKEYS", "N");
		assertThrows(IllegalArgumentException.class, () -> sql(excluded));
	}

	@Test
	void repeatedPhysicalPatternRowsDoNotDuplicateColumnsButConflictsFailClearly() {
		var first = fields("X", "XVIL");
		var second = fields("X", "XVIL");
		assertEquals(1, read(List.of(first, second), new AtomicInteger()).getFirst().getColumns().size());
		second.put("XML_PATTERN", "/different");
		assertThrows(IllegalStateException.class, () -> read(List.of(first, second), new AtomicInteger()));
		var missingColumn = fields("X", "XVIL");
		missingColumn.remove("column_name");
		missingColumn.remove("COLORDER");
		Index header = read(List.of(missingColumn), new AtomicInteger()).getFirst();
		table(header);
		assertThrows(IllegalArgumentException.class, () -> sql(header));
	}

	@Test
	void olderQueryAndGenerationDoNotUseXmlPatternCatalogs() {
		var reader = new Db2IndexReader(DialectHolder.Db2_1050Dialect);
		assertFalse(reader.getSqlNode(new ProductVersionInfo()).toString().contains("INDEXXMLPATTERNS"));
		Index index = read(List.of(fields("X", "XVIL")), new AtomicInteger()).getFirst();
		table(index);
		SqlFactory<Index> old = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry().getSqlFactory(index,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> old.createSql(index));
	}
}
