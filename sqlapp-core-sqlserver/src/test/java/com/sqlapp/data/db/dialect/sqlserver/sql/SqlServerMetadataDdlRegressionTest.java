/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class SqlServerMetadataDdlRegressionTest {
	@Test
	void decimalSequenceAlwaysUsesIntegralScale() {
		var registry = com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver.getInstance()
				.getDialect(11, 0).createSqlFactoryRegistry();
		var sequence = new com.sqlapp.data.schemas.Sequence("SEQ").setDataType(DataType.DECIMAL).setPrecision(20)
				.setScale(0);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("DECIMAL(20,0)"));
		sequence.setScale(2);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(sequence, SqlType.CREATE));
	}

	@Test
	void noCacheSequenceRemainsUncached() {
		var registry = com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver.getInstance()
				.getDialect(11, 0).createSqlFactoryRegistry();
		var sequence = new com.sqlapp.data.schemas.Sequence("SEQ").setCache(false);
		assertTrue(registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().contains("NO CACHE"));
	}

	@Test
	void lockEscalationAndTrackingAreSeparateAlterOperations() {
		Table table = new Table("Audit");
		table.setSchemaName("dbo");
		table.getColumns().add(new com.sqlapp.data.schemas.Column("id").setDataType(DataType.INT));
		table.getSpecifics().put("LOCK_ESCALATION", "AUTO");
		table.getSpecifics().put("HAS_CHANGE_TRACKING", "true");
		table.getSpecifics().put("is_edge", "false");
		table.getSpecifics().put("is_node", "0");
		table.getSpecifics().put("large_value_types_out_of_row", "false");
		var registry = com.sqlapp.data.db.dialect.sqlserver.resolver.SqlServerDialectResolver.getInstance()
				.getDialect(16, 0).createSqlFactoryRegistry();
		var operations = registry.createSql(table, SqlType.CREATE);
		assertEquals(3, operations.size());
		assertFalse(operations.get(0).getSqlText().contains("LOCK_ESCALATION"));
		assertFalse(operations.get(0).getSqlText().contains("IS_EDGE"));
		assertTrue(operations.get(1).getSqlText().contains("SET (LOCK_ESCALATION = AUTO)"));
		assertTrue(operations.get(2).getSqlText().contains("ENABLE CHANGE_TRACKING"));
		table.getSpecifics().put("LOCK_ESCALATION", "invalid");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void tableOptionsDoNotEmitForeignMetadataOrIndexOptions() {
		var registry = com.sqlapp.data.db.dialect.sqlserver.DialectHolder.defaultDialect2022.createSqlFactoryRegistry();
		Table table = new Table("Audit");
		table.getColumns().add(new com.sqlapp.data.schemas.Column("id").setDataType(DataType.INT));
		table.getSpecifics().put("engine", "InnoDB");
		table.getSpecifics().put("FILLFACTOR", "80");
		table.getSpecifics().put("ledger_type", "NON_LEDGER_TABLE");
		table.getSpecifics().put("is_node", "0");
		table.setCompression(true).setCompressionType("PAGE");
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("DATA_COMPRESSION"));
		assertFalse(sql.contains("ENGINE"));
		assertFalse(sql.contains("FILLFACTOR"));
	}

	@Test
	void rejectsTableKindsWhoseStorageAndConstraintsCannotBeRecreated() {
		var registry = com.sqlapp.data.db.dialect.sqlserver.DialectHolder.defaultDialect2022.createSqlFactoryRegistry();
		for (String[] option : new String[][] { { "is_memory_optimized", "true" }, { "is_node", "1" },
				{ "is_edge", "true" }, { "ledger_type", "APPEND_ONLY_LEDGER_TABLE" } }) {
			Table table = new Table("Audit");
			table.getSpecifics().put(option[0], option[1]);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE), option[0]);
		}
	}

	@Test
	void memoryTableTypeFromOtherDialectsIsCreatedAsOrdinaryTable() {
		// HSQL/H2の通常テーブル(MEMORY)はSQL Serverのメモリ最適化テーブルではない
		var registry = com.sqlapp.data.db.dialect.sqlserver.DialectHolder.defaultDialect2022.createSqlFactoryRegistry();
		Table memory = new Table("Audit").setTableType(com.sqlapp.data.schemas.Table.TableType.Memory);
		memory.getColumns().add(new com.sqlapp.data.schemas.Column("id").setDataType(DataType.INT));
		String sql = registry.createSql(memory, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.startsWith("CREATE TABLE"), sql);
		assertFalse(sql.contains("MEMORY_OPTIMIZED"), sql);
		assertFalse(sql.contains("DURABILITY"), sql);
	}
}
