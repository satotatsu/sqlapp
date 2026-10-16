/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleCompositePartitionMaintenanceTest {
	private Table source(PartitioningType root, PartitioningType sub) {
		Table table = new Table("T").setSchemaName("APP"); table.getColumns().add("ID"); table.getColumns().add("REGION");
		Partitioning info = new Partitioning().setPartitioningType(root).setSubPartitioningType(sub);
		info.getPartitioningColumns().add("ID"); info.getSubPartitioningColumns().add("REGION");
		for (int i = 1; i <= 2; i++) {
			Partition part = new Partition("P" + i).setHighValue(root == PartitioningType.Hash ? null : Integer.toString(i * 100));
			for (int j = 1; j <= 2; j++) part.getSubPartitions().add(new SubPartition("S" + i + j).setHighValue(sub == PartitioningType.Hash ? null : Integer.toString(j * 10)));
			info.getPartitions().add(part);
		}
		table.setPartitioning(info);
		Index index = new Index("IX"); table.getIndexes().add(index); index.getColumns().add("ID"); index.getSpecifics().put("oracle.index.locality", "LOCAL"); index.setPartitioning(info.clone());
		for (Partition part : index.getPartitioning().getPartitions()) for (SubPartition segment : part.getSubPartitions()) segment.getSpecifics().put("oracle.index.status", "USABLE");
		Index global = new Index("GIX"); table.getIndexes().add(global); global.getColumns().add("ID");
		return table.clone();
	}
	private Table remove(Table before, int parent, Integer child) {
		Table after = before.clone();
		for (Partitioning info : List.of(after.getPartitioning(), after.getIndexes().get("IX").getPartitioning())) {
			if (child == null) info.getPartitions().remove(parent); else info.getPartitions().get(parent).getSubPartitions().remove((int) child);
		}
		return after;
	}
	private String sql(Table before, Table after, Dialect dialect) {
		var factory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER); factory.getOptions().setDecorateSchemaName(true);
		var operations = factory.createDiffSql(before.diff(after)); assertEquals(1, operations.size());
		return operations.get(0).getSqlText().replaceAll("\\s+", " ").trim();
	}
	@Test void rootDropAlsoRemovesCorrespondingLocalSubpartitions() {
		for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.List)) {
			Table before = source(PartitioningType.Range, sub), after = remove(before, 0, null);
			assertEquals("ALTER TABLE APP.T DROP PARTITION P1 UPDATE INDEXES", sql(before, after, DialectHolder.oracle10gDialect));
			assertEquals(2, before.getPartitioning().getPartitions().size());
		}
	}
	@Test void listAndRangeSubpartitionDropPreserveAllOtherSegments() {
		for (PartitioningType sub : List.of(PartitioningType.Range, PartitioningType.List)) {
			Table before = source(PartitioningType.Range, sub), after = remove(before, 0, 0);
			assertEquals("ALTER TABLE APP.T DROP SUBPARTITION S11 UPDATE INDEXES", sql(before, after, DialectHolder.oracle12cDialect));
			var factory = DialectHolder.oracle12cDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
			assertEquals(SqlType.DROP, factory.createDiffSql(before.diff(after)).get(0).getSqlType());
		}
	}
	@Test void hashSubpartitionReductionCoalescesWithoutDeletingRows() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = remove(before, 0, 1);
		assertEquals("ALTER TABLE APP.T MODIFY PARTITION P1 COALESCE SUBPARTITION UPDATE INDEXES", sql(before, after, DialectHolder.oracle10gDialect));
		var factory = DialectHolder.oracle10gDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		assertEquals(SqlType.ALTER, factory.createDiffSql(before.diff(after)).get(0).getSqlType());
		assertThrows(IllegalArgumentException.class, () -> sql(before, remove(before, 0, 0), DialectHolder.oracle26aiDialect));
	}
	@Test void compositeHashRootCoalescesOnlyTheLastPartition() {
		Table before = source(PartitioningType.Hash, PartitioningType.List);
		assertEquals("ALTER TABLE APP.T COALESCE PARTITION UPDATE INDEXES", sql(before, remove(before, 1, null), DialectHolder.oracle12cDialect));
		assertThrows(IllegalArgumentException.class, () -> sql(before, remove(before, 0, null), DialectHolder.oracle26aiDialect));
	}
	@Test void localIndexRemovalMustMatchTheTablePosition() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		var children = after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions();
		children.clear(); children.add(before.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(0).clone());
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
	}
	@Test void parentDefaultsTemplatesAndConcurrentChangesAreRejected() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		after.getPartitioning().getPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table changed = remove(before, 0, 0); changed.getColumns().add("EXTRA");
		assertThrows(IllegalArgumentException.class, () -> sql(before, changed, DialectHolder.oracle26aiDialect));
		Table defaults = remove(before, 0, 0); defaults.getPartitioning().setSubPartitionSize(4);
		assertThrows(IllegalArgumentException.class, () -> sql(before, defaults, DialectHolder.oracle26aiDialect));
	}
	@Test void allChildrenAndAllParentsCannotBeRemoved() {
		Table before = source(PartitioningType.Range, PartitioningType.List), one = remove(before, 0, 1);
		assertThrows(IllegalArgumentException.class, () -> sql(one, remove(one, 0, 0), DialectHolder.oracle26aiDialect));
		Table last = remove(before, 1, null);
		assertThrows(IllegalArgumentException.class, () -> sql(last, remove(last, 0, null), DialectHolder.oracle26aiDialect));
	}
	@Test void versionAndIotBoundariesRemainExplicit() {
		Table before = source(PartitioningType.Range, PartitioningType.Range);
		assertThrows(IllegalArgumentException.class, () -> sql(before, remove(before, 0, 0), DialectHolder.oracle10gDialect));
		before.getSpecifics().put("oracle.table.organization", "INDEX");
		assertThrows(IllegalArgumentException.class, () -> sql(before, remove(before, 0, 0), DialectHolder.oracle26aiDialect));
	}
	@Test void dropAndOnlineOptionsAreHonored() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry(); registry.getTableOptions().setAllowDropPartition(false);
		assertThrows(IllegalArgumentException.class, () -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		var online = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry(); online.getTableOptions().setOnlineIndex(true);
		assertThrows(IllegalArgumentException.class, () -> online.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}
	@Test void ambiguousNamesAndUnusableCoalesceSegmentsAreRejected() {
		Table before = source(PartitioningType.Range, PartitioningType.List); before.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setName("S11");
		assertThrows(IllegalArgumentException.class, () -> sql(before, remove(before, 0, 0), DialectHolder.oracle26aiDialect));
		Table hash = source(PartitioningType.Range, PartitioningType.Hash); hash.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(0).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> sql(hash, remove(hash, 0, 1), DialectHolder.oracle26aiDialect));
	}
	@Test void globalOnlyTablesAndQuotedNamesUseTheCompositePlanner() {
		Table before = source(PartitioningType.Range, PartitioningType.List); before.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setName("child part");
		Table after = remove(before, 0, 0); before.getIndexes().remove("IX"); after.getIndexes().remove("IX");
		assertEquals("ALTER TABLE APP.T DROP SUBPARTITION \"child part\" UPDATE INDEXES", sql(before, after, DialectHolder.oracle10gDialect));
		before.getIndexes().clear(); after.getIndexes().clear();
		assertTrue(sql(before, after, DialectHolder.oracle10gDialect).contains("DROP SUBPARTITION"));
	}
	@Test void multipleChildEditsAndSpecialIndexesAreRejected() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		after.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table special = source(PartitioningType.Range, PartitioningType.List); special.getIndexes().get("IX").setIndexType(IndexType.Domain);
		assertThrows(IllegalArgumentException.class, () -> sql(special, remove(special, 0, 0), DialectHolder.oracle26aiDialect));
	}
}
