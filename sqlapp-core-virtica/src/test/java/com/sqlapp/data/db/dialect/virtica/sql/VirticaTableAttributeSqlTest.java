/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class VirticaTableAttributeSqlTest {
	@Test
	void identityReplacesTypeAndKeepsOptionsWithoutMutatingColumn() throws Exception {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Table table = table();
		table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT).setIdentity(true)
				.setIdentityStep(2L).setIdentityCacheSize(20).setNotNull(true));
		String before = table.asXml();
		String sql = registry.createSql(table, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"id\" IDENTITY"), sql);
		assertFalse(sql.contains("BIGINT IDENTITY"), sql);
		assertTrue(sql.matches("(?s).*IDENTITY\\s*\\(1,\\s*2,\\s*20\\).*"), sql);
		assertEquals(before, table.asXml());
	}

	private Table table() {
		Table table = new Table("events");
		table.getColumns().add("event day", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.DATE));
		return table;
	}

	@Test
	void flexAndPartitionExpressionSurviveXmlWithoutMutatingModel() throws Exception {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Table table = table();
		table.setTableType(Table.TableType.Flex);
		table.getSpecifics().put("PARTITION_EXPRESSION", "EXTRACT(YEAR FROM \"event day\")");
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		String before = restored.asXml();
		String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.startsWith("CREATE FLEX TABLE"), sql);
		assertTrue(sql.contains("PARTITION BY EXTRACT(YEAR FROM \"event day\")"), sql);
		assertEquals(before, restored.asXml());
	}

	@Test
	void temporaryCommitMetadataOverridesDefaultAndRejectsInvalidValues() throws Exception {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		for (String action : new String[] { "DELETE", "PRESERVE" }) {
			Table table = table();
			table.getSpecifics().put("COMMIT_ACTION", action);
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			String sql = registry.createSql(restored, SqlType.CREATE_TEMPORARY).get(0).getSqlText();
			assertTrue(sql.endsWith("ON COMMIT " + action + " ROWS"), sql);
		}
		Table invalid = table();
		invalid.getSpecifics().put("COMMIT_ACTION", "drop");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(invalid, SqlType.CREATE_TEMPORARY));
	}

	@Test
	void incompleteSpecialTableRepresentationsDoNotBecomeOrdinaryTables() {
		var registry = DialectResolver.getInstance().getDialect("Vertica", 25, 1, 0).createSqlFactoryRegistry();
		Table table = table();
		table.setTableType(Table.TableType.Temporary);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		table.setTableType(Table.TableType.Flex);
		table.getSpecifics().put("IS_TEMP_TABLE", true);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE_TEMPORARY));
		table.getSpecifics().remove("IS_TEMP_TABLE");
		table.getColumns().add("__raw__");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		Table external = table();
		external.getSpecifics().put("TABLE_DEFINITION", "COPY FROM '/data/events.csv'");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(external, SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(external, SqlType.CREATE_TEMPORARY));
	}
}
