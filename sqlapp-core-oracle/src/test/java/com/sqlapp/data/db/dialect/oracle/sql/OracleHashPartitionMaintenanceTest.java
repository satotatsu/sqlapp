/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleHashPartitionMaintenanceTest {
	private Table source(boolean local) {
		Table table = new Table("T").setSchemaName("APP"); table.getColumns().add("ID");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Hash);
		info.getPartitioningColumns().add("ID"); info.getPartitions().add(new Partition("P1")); info.getPartitions().add(new Partition("P2")); table.setPartitioning(info);
		Index global = new Index("GIX"); table.getIndexes().add(global); global.getColumns().add("ID");
		if (local) {
			Index index = new Index("IX"); table.getIndexes().add(index); index.getColumns().add("ID");
			index.getSpecifics().put("oracle.index.locality", "LOCAL"); index.setPartitioning(info.clone());
			for (Partition part : index.getPartitioning().getPartitions()) part.getSpecifics().put("oracle.index.status", "USABLE");
		}
		return table.clone();
	}
	private Table append(Table before) {
		Table after = before.clone(); after.getPartitioning().getPartitions().add(new Partition("P3").setTableSpaceName("DATA"));
		Index index = after.getIndexes().get("IX");
		if (index != null) { Partition segment = new Partition("IP3").setTableSpaceName("INDEX_DATA"); segment.getSpecifics().put("oracle.index.status", "USABLE"); index.getPartitioning().getPartitions().add(segment); }
		return after;
	}
	private Table remove(Table before, int position) {
		Table after = before.clone(); after.getPartitioning().getPartitions().remove(position);
		Index index = after.getIndexes().get("IX"); if (index != null) index.getPartitioning().getPartitions().remove(position);
		return after;
	}
	private List<String> sql(Table before, Table after, Dialect dialect) {
		var factory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER); factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream().map(operation -> operation.getSqlText().replaceAll("\\s+", " ").trim()).toList();
	}
	@Test void addRehashesAndMaintainsAllIndexesBeforeLocalRenameAndMove() {
		Table before = source(true);
		assertEquals(List.of("ALTER TABLE APP.T ADD PARTITION P3 TABLESPACE DATA UPDATE INDEXES", "ALTER INDEX APP.IX RENAME PARTITION P3 TO IP3", "ALTER INDEX APP.IX REBUILD PARTITION IP3 TABLESPACE INDEX_DATA"), sql(before, append(before), DialectHolder.oracle10gDialect));
	}
	@Test void coalescePreservesRowsAndIsAnAlterOperation() {
		Table before = source(true), after = remove(before, 1);
		assertEquals(List.of("ALTER TABLE APP.T COALESCE PARTITION UPDATE INDEXES"), sql(before, after, DialectHolder.oracle10gDialect));
		var factory = DialectHolder.oracle10gDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		assertEquals(SqlType.ALTER, factory.createDiffSql(before.diff(after)).get(0).getSqlType());
		assertEquals(2, before.getPartitioning().getPartitions().size());
	}
	@Test void globalOnlyAndNoIndexesUseTheSameSafeHashPath() {
		Table before = source(false); assertEquals(1, sql(before, append(before), DialectHolder.oracle26aiDialect).size());
		assertEquals(List.of("ALTER TABLE APP.T COALESCE PARTITION UPDATE INDEXES"), sql(before, remove(before, 1), DialectHolder.oracle26aiDialect));
		before.getIndexes().clear(); assertEquals(1, sql(before, append(before), DialectHolder.oracle26aiDialect).size());
	}
	@Test void nonLastRemovalAndRemovingAllPartitionsAreRejected() {
		Table before = source(true);
		assertThrows(IllegalArgumentException.class, () -> sql(before, remove(before, 0), DialectHolder.oracle26aiDialect));
		Table one = remove(before, 1), empty = remove(one, 0);
		assertThrows(IllegalArgumentException.class, () -> sql(one, empty, DialectHolder.oracle26aiDialect));
	}
	@Test void boundsCompressionAndUnusableLocalSegmentsAreRejected() {
		Table before = source(true), after = append(before); after.getPartitioning().getPartitions().get(2).setHighValue("100");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table compressed = source(true); compressed.getIndexes().get("IX").setCompression(true);
		assertThrows(IllegalArgumentException.class, () -> sql(compressed, append(compressed), DialectHolder.oracle26aiDialect));
		Table unusable = source(true); unusable.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> sql(unusable, remove(unusable, 1), DialectHolder.oracle26aiDialect));
	}
	@Test void oldVersionsIotAndCompositePartitioningAreRejected() {
		Table before = source(true); assertThrows(IllegalArgumentException.class, () -> sql(before, append(before), DialectHolder.defaultDialect));
		before.getSpecifics().put("oracle.table.organization", "INDEX");
		assertThrows(IllegalArgumentException.class, () -> sql(before, append(before), DialectHolder.oracle26aiDialect));
		Table composite = source(true); composite.getPartitioning().setSubPartitioningType(PartitioningType.Hash);
		assertThrows(IllegalArgumentException.class, () -> sql(composite, append(composite), DialectHolder.oracle26aiDialect));
	}
	@Test void indexCorrespondenceAndConcurrentChangesAreValidated() {
		Table before = source(true), after = append(before); after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).setName("CHANGED");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table changed = append(before); changed.getIndexes().get("GIX").setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, changed, DialectHolder.oracle26aiDialect));
	}
	@Test void operationAndOnlineOptionsAreHonored() {
		Table before = source(true), after = append(before), reduced = remove(before, 1);
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setAllowAddPartition(false); registry.getTableOptions().setAllowDropPartition(false);
		assertThrows(IllegalArgumentException.class, () -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		assertThrows(IllegalArgumentException.class, () -> registry.getSqlFactory(reduced, SqlType.ALTER).createDiffSql(before.diff(reduced)));
		var online = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry(); online.getTableOptions().setOnlineIndex(true);
		assertThrows(IllegalArgumentException.class, () -> online.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}
	@Test void automaticNameCollisionsAndIndexBoundsAreRejected() {
		Table before = source(true); before.getIndexes().get("IX").getPartitioning().getPartitions().get(0).setName("P3");
		assertThrows(IllegalArgumentException.class, () -> sql(before, append(before), DialectHolder.oracle26aiDialect));
		Table bounded = source(true); bounded.getIndexes().get("IX").getPartitioning().getPartitions().get(0).setHighValue("100");
		assertThrows(IllegalArgumentException.class, () -> sql(bounded, append(bounded), DialectHolder.oracle26aiDialect));
	}
	@Test void quotingAndDefaultIndexTablespaceArePreserved() {
		Table before = source(true); before.getIndexes().get("IX").setTableSpaceName("DEFAULT_INDEX"); Table after = append(before);
		after.getPartitioning().getPartitions().get(2).setName("new part");
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(2).setTableSpaceName(null);
		List<String> statements = sql(before, after, DialectHolder.oracle26aiDialect);
		assertEquals(2, statements.size());
		assertEquals("ALTER TABLE APP.T ADD PARTITION \"new part\" TABLESPACE DATA UPDATE INDEXES", statements.get(0));
		assertEquals("ALTER INDEX APP.IX RENAME PARTITION \"new part\" TO IP3", statements.get(1));
	}
}
