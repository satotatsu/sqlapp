/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleHeapMoveTest {
	private String sql(Table before, Table after, Dialect dialect) {
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(1, operations.size());
		return operations.get(0).getSqlText();
	}

	@Test
	void rootMoveKeepsIndexesAndUsesIdentifierQuoting() {
		Table before = new Table("T").setSchemaName("APP").setTableSpaceName("OLD");
		Table after = before.clone();
		after.setTableSpaceName("New Space");
		assertEquals("ALTER TABLE APP.T MOVE TABLESPACE \"New Space\" UPDATE INDEXES",
				sql(before, after, DialectHolder.oracle18cDialect));
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle12cDialect));
	}

	@Test
	void rootMoveRejectsCombinedChangesAndSpecialIndexes() {
		Table before = new Table("T").setTableSpaceName("OLD");
		Table after = before.clone();
		after.setTableSpaceName("NEW");
		after.setRemarks("changed");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Index index = new Index("IX");
		index.setIndexType(IndexType.Domain);
		before.getIndexes().add(index);
		Table special = before.clone();
		special.setTableSpaceName("NEW");
		assertThrows(IllegalArgumentException.class, () -> sql(before, special, DialectHolder.oracle26aiDialect));
	}

	@Test
	void partitionMoveMaintainsGlobalAndLocalIndexes() {
		Table before = new Table("T").setSchemaName("APP");
		before.getColumns().add("ID");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range);
		info.getPartitioningColumns().add("ID");
		info.getPartitions().add(new Partition("P1").setHighValue("100").setTableSpaceName("OLD"));
		before.setPartitioning(info);
		Table after = before.clone();
		after.getPartitioning().getPartitions().get(0).setTableSpaceName("NEW");
		assertEquals("ALTER TABLE APP.T MOVE PARTITION P1 TABLESPACE NEW UPDATE INDEXES",
				sql(before, after, DialectHolder.oracle10gDialect));
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.defaultDialect));
	}
}
