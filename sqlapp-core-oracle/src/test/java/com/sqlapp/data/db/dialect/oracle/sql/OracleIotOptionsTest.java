/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleIotOptionsTest {
	private Table table() {
		Table table = new Table("T"); table.getSpecifics().put("oracle.table.organization", "INDEX");
		for (String name : java.util.List.of("ID", "SEQ", "BODY")) table.getColumns().add(new Column(name).setDataType(DataType.INT));
		UniqueConstraint pk = new UniqueConstraint("PK", true); pk.getColumns().add("ID"); pk.getColumns().add("SEQ"); table.getConstraints().add(pk); return table;
	}
	private String create(Table table) { return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " "); }
	@Test void explicitOverflowMappingAndPrefixCompressionSurviveXml() throws Exception {
		Table table = table(); table.setTableSpaceName("ROOT");
		table.getSpecifics().put(OracleIotOptions.PREFIX + "pctThreshold", "20"); table.getSpecifics().put(OracleIotOptions.PREFIX + "mapping", true);
		table.getSpecifics().put(OracleIotOptions.PREFIX + "compressionPrefixLength", "1"); table.getSpecifics().put(OracleIotOptions.PREFIX + "overflow", true);
		table.getSpecifics().put(OracleIotOptions.PREFIX + "including", "BODY"); table.getSpecifics().put(OracleIotOptions.PREFIX + "overflowTableSpace", "Overflow Space");
		String sql = create(SchemaUtils.readXml(new java.io.StringReader(table.asXml())));
		assertTrue(sql.contains("ORGANIZATION INDEX PCTTHRESHOLD 20 MAPPING TABLE COMPRESS 1 TABLESPACE ROOT"), sql);
		assertTrue(sql.contains("INCLUDING BODY OVERFLOW TABLESPACE \"Overflow Space\""), sql); assertFalse(sql.contains("oracle.table.iot"));
	}
	@Test void invalidThresholdPrefixAndUnknownOptionsFail() {
		for (String value : java.util.List.of("0", "51", "x")) { Table table = table(); table.getSpecifics().put(OracleIotOptions.PREFIX + "pctThreshold", value); assertThrows(IllegalArgumentException.class, () -> create(table)); }
		Table table = table(); table.getSpecifics().put(OracleIotOptions.PREFIX + "compressionPrefixLength", "2"); assertThrows(IllegalArgumentException.class, () -> create(table));
		Table unknown = table(); unknown.getSpecifics().put(OracleIotOptions.PREFIX + "unknown", true); assertThrows(IllegalArgumentException.class, () -> create(unknown));
	}
	@Test void includingRequiresOverflowAndNonKeyColumn() {
		Table table = table(); table.getSpecifics().put(OracleIotOptions.PREFIX + "including", "BODY"); assertThrows(IllegalArgumentException.class, () -> create(table));
		table.getSpecifics().put(OracleIotOptions.PREFIX + "overflow", true); table.getSpecifics().put(OracleIotOptions.PREFIX + "including", "ID"); assertThrows(IllegalArgumentException.class, () -> create(table));
		table.getSpecifics().put(OracleIotOptions.PREFIX + "including", "MISSING"); assertThrows(IllegalArgumentException.class, () -> create(table));
	}
	@Test void heapTablesAndInvalidFlagsRejectIotOptions() {
		Table table = table(); table.getSpecifics().remove("oracle.table.organization"); table.getSpecifics().put(OracleIotOptions.PREFIX + "mapping", true); assertThrows(IllegalArgumentException.class, () -> create(table));
		Table invalid = table(); invalid.getSpecifics().put(OracleIotOptions.PREFIX + "overflow", "YES"); assertThrows(IllegalArgumentException.class, () -> create(invalid));
	}
	@Test void isolatedRootTablespaceMovePreservesTheIot() {
		Table before = table(); before.setTableSpaceName("OLD"); Table after = before.clone(); after.setTableSpaceName("New Space");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		var statements = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(1, statements.size()); assertEquals("ALTER TABLE T MOVE TABLESPACE \"New Space\"", statements.get(0).getSqlText());
	}
	@Test void mappingOrCombinedChangesCannotUseSimpleMove() {
		Table before = table(); before.setTableSpaceName("OLD"); before.getSpecifics().put(OracleIotOptions.PREFIX + "mapping", true);
		Table after = before.clone(); after.setTableSpaceName("NEW");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}
}
