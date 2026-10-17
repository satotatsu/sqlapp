/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleHeapPartitionMaintenanceTest {
	private Table source() {
		Table table = new Table("T").setSchemaName("APP"); table.getColumns().add("ID");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range); info.getPartitioningColumns().add("ID");
		info.getPartitions().add(new Partition("P1").setHighValue("100")); info.getPartitions().add(new Partition("P2").setHighValue("200")); table.setPartitioning(info);
		return table;
	}
	private String sql(Table before, Table after) {
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry(); registry.getOptions().setDecorateSchemaName(true);
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)); assertEquals(1, operations.size());
		return operations.get(0).getSqlText().replaceAll("\\s+", " ").replace("( ", "(").trim();
	}
	@Test void rangeAddAndDropWithoutLocalIndexesUseNativeSyntax() {
		Table before = source(), added = before.clone(); added.getPartitioning().getPartitions().add(new Partition("P3").setHighValue("300"));
		assertEquals("ALTER TABLE APP.T ADD PARTITION P3 VALUES LESS THAN (300)", sql(before, added));
		Table removed = before.clone(); removed.getPartitioning().getPartitions().remove(0);
		assertEquals("ALTER TABLE APP.T DROP PARTITION P1 UPDATE INDEXES", sql(before, removed));
	}
	@Test void globalOnlyRangeSplitAndMergePreserveRowsWithoutEmptyIndexLists() {
		Table before = source(); Index index = new Index("GIX"); before.getIndexes().add(index); index.getColumns().add("ID");
		Table after = before.clone(); after.getPartitioning().getPartitions().get(1).setName("LOW").setHighValue("150"); after.getPartitioning().getPartitions().add(new Partition("HIGH").setHighValue("200"));
		assertEquals("ALTER TABLE APP.T SPLIT PARTITION P2 AT (150) INTO (PARTITION LOW, PARTITION HIGH) UPDATE INDEXES", sql(before, after));
		assertEquals("ALTER TABLE APP.T MERGE PARTITIONS LOW, HIGH INTO PARTITION P2 UPDATE INDEXES", sql(after, before));
	}
	@Test void listSplitWithoutIndexesUsesValuesAndPreservesTheUnion() {
		Table before = source(); before.getPartitioning().setPartitioningType(PartitioningType.List); before.getPartitioning().getPartitions().remove(0); before.getPartitioning().getPartitions().get(0).setHighValue("1,2");
		Table after = before.clone(); after.getPartitioning().getPartitions().get(0).setName("LOW").setHighValue("1"); after.getPartitioning().getPartitions().add(new Partition("HIGH").setHighValue("2"));
		assertTrue(sql(before, after).contains("SPLIT PARTITION P2 VALUES (1)"));
		assertTrue(sql(after, before).endsWith("UPDATE INDEXES"));
	}
	@Test void changingPartitionedStateRequiresExplicitMigration() {
		Table before = source(), plain = before.clone(); plain.setPartitioning(null);
		assertThrows(IllegalArgumentException.class, () -> sql(before, plain));
		assertThrows(IllegalArgumentException.class, () -> sql(plain, before));
	}
}
