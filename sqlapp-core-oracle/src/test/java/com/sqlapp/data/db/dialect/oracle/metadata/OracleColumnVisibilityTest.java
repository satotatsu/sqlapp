/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class OracleColumnVisibilityTest {
	private Table table() {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add(new Column("MixedColumn").setDataType(DataType.INT)); return table;
	}
	private String diff(Table oldTable, Table target) {
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		return factory.createDiffSql(oldTable.diff(target)).get(0).getSqlText().replaceAll("\\s+", " ").replaceAll("\\(\\s+", "(");
	}
	@Test
	void invisibleUserColumnsRemainSeparateFromSystemHiddenColumns() throws Exception {
		for (String userGenerated : java.util.List.of("YES", "NO")) {
			var values = Map.of("COLUMN_NAME", "C", "DATA_TYPE", "NUMBER", "HIDDEN_COLUMN", "YES", "USER_GENERATED", userGenerated, "NULLABLE", "Y");
			Column column = new Oracle12cColumnReader(DialectHolder.oracle12cDialect).createColumn(new ExResultSet(null) {
				@Override public String getString(String key) { return values.get(key.toUpperCase(java.util.Locale.ROOT)); }
				@Override public Object getObject(String key) { return null; }
				@Override public long getLong(String key) { return 0; }
				@Override public int getInt(String key) { return 0; }
				@Override public boolean wasNull() { return true; }
			});
			assertEquals(userGenerated.equals("NO"), column.isHidden());
			assertEquals(userGenerated.equals("YES") ? "true" : null, column.getSpecifics().get("oracle.column.invisible"));
		}
	}
	@Test
	void visibilitySurvivesXmlAndCreateForOrdinaryIdentityAndVirtualColumns() throws Exception {
		for (String kind : java.util.List.of("ordinary", "identity", "virtual")) {
			Table table = table(); Column column = table.getColumns().get(0);
			column.getSpecifics().put("oracle.column.invisible", true);
			if (kind.equals("identity")) column.setIdentity(true);
			if (kind.equals("virtual")) column.setFormula("1+1");
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertEquals(1, sql.split("INVISIBLE", -1).length - 1, sql);
			assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle11gR2Dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
		}
	}
	@Test
	void visibilityChangesUseOnlyTheVisibilityClauseAndRejectCombinedChanges() {
		Table original = table(), target = original.clone();
		target.getColumns().get(0).getSpecifics().put("oracle.column.invisible", true);
		assertTrue(diff(original, target).contains("MODIFY (\"MixedColumn\" INVISIBLE)"));
		assertTrue(diff(target, original).contains("MODIFY (\"MixedColumn\" VISIBLE)"));
		target.getColumns().get(0).setDefaultValue("42"); assertThrows(IllegalArgumentException.class, () -> diff(original, target));
	}
	@Test
	void lobTablespaceMetadataRequiresAnUnambiguousAggregate() throws Exception {
		var values = Map.of("OWNER", "APP", "TABLE_NAME", "T", "LOB_TABLESPACE_NAME", "LOB_TS", "STATUS", "VALID");
		Table table = new OracleTableReader(DialectHolder.oracle26aiDialect).createTable(new ExResultSet(null) {
			@Override public String getString(String key) { return values.get(key.toUpperCase(java.util.Locale.ROOT)); }
			@Override public Object getObject(String key) { return null; }
			@Override public Long getLongValue(String key) { return null; }
		});
		assertEquals("LOB_TS", table.getLobTableSpaceName());
		try (var stream = OracleTableReader.class.getResourceAsStream("tables.sql")) {
			String query = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			assertTrue(query.contains("COUNT(*)=COUNT(TABLESPACE_NAME)")); assertTrue(query.contains("COUNT(DISTINCT TABLESPACE_NAME)=1"));
		}
	}
	@Test
	void lobTablespaceUsesTheSharedModelAndRejectsTablesWithoutLobs() throws Exception {
		Table table = table(); table.setLobTableSpaceName("Lob Space");
		table.getColumns().add(new Column("Body").setDataType(DataType.CLOB));
		table.getColumns().add(new Column("Bytes").setDataType(DataType.BLOB));
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("LOB ( \"Body\", \"Bytes\") STORE AS (TABLESPACE \"Lob Space\")"), sql);
		restored.getColumns().remove("Body"); restored.getColumns().remove("Bytes");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
	}
	@Test
	void identityModeChangesDoNotRestartItsSequence() {
		Table original = table(); original.getColumns().get(0).setIdentity(true).setIdentityGenerationType(IdentityGenerationType.Always).setIdentityStartValue(50);
		Table target = original.clone(); target.getColumns().get(0).setIdentityGenerationType(IdentityGenerationType.ByDefault).getSpecifics().put("DEFAULT_ON_NULL", true);
		String sql = diff(original, target);
		assertTrue(sql.contains("GENERATED BY DEFAULT ON NULL AS IDENTITY"), sql); assertFalse(sql.contains("START WITH")); assertFalse(sql.contains("DROP"));
		assertTrue(diff(target, original).contains("GENERATED ALWAYS AS IDENTITY"));
		target.getColumns().get(0).setIdentityStartValue(51); assertThrows(IllegalArgumentException.class, () -> diff(original, target));
	}
}
