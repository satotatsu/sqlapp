/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

class Db2IndexRecreationTest {
	@Test
	void onlyProvenSystemConstraintBackingIndexesAreImplicitlyRecreated() throws Exception {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true));
		UniqueConstraint primary = new UniqueConstraint("PK_T", true);
		primary.getColumns().add("ID");
		primary.getIndex().setName("SQL_GENERATED");
		primary.getIndex().getSpecifics().put("INDEX_SCHEMA", "SYSIBM  ");
		table.getConstraints().add(primary);
		Index backing = new Index("SQL_GENERATED").setUnique(true);
		backing.getColumns().add("ID");
		backing.getSpecifics().put("INDEX_SCHEMA", "SYSIBM  ");
		backing.getSpecifics().put("DB2_CONSTRAINT_NAME", "PK_T");
		table.getIndexes().add(backing);
		Index ordinary = new Index("USER_INDEX");
		ordinary.getColumns().add("ID");
		table.getIndexes().add(ordinary);
		SqlFactory<Table> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(table,
				SqlType.CREATE);
		var statements = factory.createSql(table).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(statements.stream().anyMatch(sql -> sql.contains("PRIMARY KEY")));
		assertTrue(statements.stream().anyMatch(sql -> sql.contains("USER_INDEX")));
		assertTrue(statements.stream().noneMatch(sql -> sql.contains("SQL_GENERATED")));
		assertEquals(2, table.getIndexes().size());
		Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		assertTrue(factory.createSql(restored).stream().noneMatch(o -> o.getSqlText().contains("SQL_GENERATED")));
	}

	private Table table() {
		Table table = new Table("T\"Q").setSchemaName("Table\"Schema");
		table.getColumns().add("C\"Q");
		table.getColumns().add("Included\"Q");
		Index index = new Index("I\"Q").setUnique(true);
		index.getColumns().add("C\"Q");
		index.getColumns().add(new ReferenceColumn("Included\"Q").setIncludedColumn(true));
		index.getSpecifics().put("INDEX_SCHEMA", "Index\"Schema");
		index.setRemarks("index's comment");
		table.getIndexes().add(index);
		return table;
	}

	private List<String> sql(Index index, SqlType type) {
		SqlFactory<Index> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(index, type);
		return factory.createSql(index).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void catalogUnspecifiedPctfreeDoesNotBecomeInvalidNativeDdl() {
		var index = table().getIndexes().getFirst();
		index.getSpecifics().put("PCTFREE", "-1");
		assertFalse(sql(index, SqlType.CREATE).getFirst().contains("PCTFREE"));
		assertEquals("-1", index.getSpecifics().get("PCTFREE"));
		index.getSpecifics().put("PCTFREE", "0");
		assertTrue(sql(index, SqlType.CREATE).getFirst().contains("PCTFREE 0"));
		index.getSpecifics().put("PCTFREE", "-2");
		assertThrows(IllegalArgumentException.class, () -> sql(index, SqlType.CREATE));
	}

	@Test
	void nativeSchemaAndQuotedTableKeyIncludeNamesSurviveXmlForCreateDropAndComment() throws Exception {
		Table restored = SchemaUtils.readXml(new StringReader(table().asXml()));
		Index index = restored.getIndexes().getFirst();
		String ddl = sql(index, SqlType.CREATE).getFirst();
		for (String name : List.of("\"Index\"\"Schema\".\"I\"\"Q\"", "\"Table\"\"Schema\".\"T\"\"Q\"", "\"C\"\"Q\"",
				"\"Included\"\"Q\""))
			assertTrue(ddl.contains(name), ddl);
		assertEquals("COMMENT ON INDEX \"Index\"\"Schema\".\"I\"\"Q\" IS 'index''s comment'",
				sql(index, SqlType.CREATE).getLast());
		assertEquals(List.of("DROP INDEX \"Index\"\"Schema\".\"I\"\"Q\""), sql(index, SqlType.DROP));
	}

	@Test
	void multilineExpressionsAndBlankLinesRemainOneExpressionWithCreationPathAfterXml() throws Exception {
		Table table = table();
		Index index = table.getIndexes().getFirst();
		index.getColumns().getFirst().setStatement("CASE\n\n WHEN \"C\"\"Q\" > 0 THEN 1\n ELSE 0 END");
		index.getSpecifics().put("FUNC_PATH", "\"SYSIBM\",\"Fns.Q\"");
		Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		var sql = sql(restored.getIndexes().getFirst(), SqlType.CREATE);
		assertEquals("SET CURRENT PATH = \"SYSIBM\", \"Fns.Q\"", sql.getFirst());
		assertTrue(sql.get(1).contains("(CASE\n\n WHEN \"C\"\"Q\" > 0 THEN 1\n ELSE 0 END)"), sql.toString());
	}

	@Test
	void detachedCompleteDefinitionDoesNotNeedParentButModeledIndexDoes() throws Exception {
		Index index = new Index("I").setSchemaName("APP");
		assertThrows(IllegalArgumentException.class, () -> sql(index, SqlType.CREATE));
		String ddl = "CREATE INDEX APP.I ON APP.T (\n\n ID\n)";
		index.setDefinition(ddl);
		Index restored = SchemaUtils.readXml(new StringReader(index.asXml()));
		assertEquals(List.of(ddl), sql(restored, SqlType.CREATE));
		assertEquals(List.of("DROP INDEX \"APP\".\"I\""), sql(restored, SqlType.DROP));
		restored.getSpecifics().put("FUNC_PATH", "\"SYSIBM\",\"Fns.Q\"");
		assertEquals(List.of("SET CURRENT PATH = \"SYSIBM\", \"Fns.Q\"", ddl), sql(restored, SqlType.CREATE));
	}

	@Test
	void droppedIndexThroughTableDiffUsesItsOwnSchema() {
		Table before = table(), after = before.clone();
		after.getIndexes().clear();
		SqlFactory<Table> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		var sql = factory.createDiffSql(before.diff(after)).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(sql.contains("DROP INDEX \"Index\"\"Schema\".\"I\"\"Q\""), sql.toString());
	}

	@Test
	void tableDiffRecreationRetainsExpressionContextAndComments() {
		Table before = table(), after = before.clone();
		Index index = after.getIndexes().getFirst();
		index.getColumns().getFirst().setStatement("ABS(\n \"C\"\"Q\")");
		index.getSpecifics().put("FUNC_PATH", "\"SYSIBM\",\"Fns.Q\"");
		SqlFactory<Table> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		var sql = factory.createDiffSql(before.diff(after)).stream().map(SqlOperation::getSqlText).toList();
		assertTrue(sql.contains("SET CURRENT PATH = \"SYSIBM\", \"Fns.Q\""), sql.toString());
		assertTrue(sql.stream().anyMatch(s -> s.contains("(ABS(\n \"C\"\"Q\"))")), sql.toString());
		assertTrue(sql.contains("COMMENT ON INDEX \"Index\"\"Schema\".\"I\"\"Q\" IS 'index''s comment'"),
				sql.toString());
	}
}
