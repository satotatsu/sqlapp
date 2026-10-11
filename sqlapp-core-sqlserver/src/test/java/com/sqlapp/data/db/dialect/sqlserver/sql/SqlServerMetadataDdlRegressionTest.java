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
}
