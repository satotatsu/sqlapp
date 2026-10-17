/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.util.OracleTableDefinition;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table;

class OracleTablePartitionMaintenanceTest {
	private Table table(boolean iot, boolean local) {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range);
		info.getPartitioningColumns().add("ID");
		info.getPartitions().add(new Partition("P1").setHighValue("100"));
		info.getPartitions().add(new Partition("P2").setHighValue("200"));
		table.setPartitioning(info);
		if (iot)
			table.getSpecifics().put("oracle.table.organization", "INDEX");
		Index index = new Index("IX");
		table.getIndexes().add(index);
		index.getColumns().add("ID");
		index.getSpecifics().put("oracle.index.locality", local ? "LOCAL" : "GLOBAL");
		if (local) {
			index.setPartitioning(info.clone());
			for (Partition partition : index.getPartitioning().getPartitions())
				partition.getSpecifics().put("oracle.index.status", "USABLE");
		}
		return table.clone();
	}

	private Table append(Table before) {
		Table after = before.clone();
		after.getPartitioning().getPartitions().add(new Partition("P3").setHighValue("300").setTableSpaceName("DATA"));
		Index index = after.getIndexes().get(0);
		if (index.getPartitioning() != null) {
			Partition segment = new Partition("IP3").setHighValue("300").setTableSpaceName("INDEX_DATA");
			segment.getSpecifics().put("oracle.index.status", "USABLE");
			index.getPartitioning().getPartitions().add(segment);
		}
		return after;
	}

	private Table drop(Table before, int position) {
		Table after = before.clone();
		after.getPartitioning().getPartitions().remove(position);
		for (Index index : after.getIndexes())
			if (index.getPartitioning() != null)
				index.getPartitioning().getPartitions().remove(position);
		return after;
	}

	private List<String> sql(Table before, Table after, Dialect dialect) {
		var factory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream()
				.map(operation -> operation.getSqlText().replaceAll("\\s+", " ").trim()).toList();
	}

	@Test
	void appendRenamesAndMovesTheAutomaticallyCreatedLocalSegment() {
		Table before = table(false, true), after = append(before);
		assertEquals(
				List.of("ALTER TABLE APP.T ADD PARTITION P3 VALUES LESS THAN (300) TABLESPACE DATA",
						"ALTER INDEX APP.IX RENAME PARTITION P3 TO IP3",
						"ALTER INDEX APP.IX REBUILD PARTITION IP3 TABLESPACE INDEX_DATA"),
				sql(before, after, DialectHolder.oracle10gDialect));
		assertEquals(2, before.getPartitioning().getPartitions().size());
	}

	private Table listTable() {
		Table table = table(false, true);
		table.getPartitioning().setPartitioningType(PartitioningType.List);
		table.getIndexes().get(0).getPartitioning().setPartitioningType(PartitioningType.List);
		for (Partitioning info : List.of(table.getPartitioning(), table.getIndexes().get(0).getPartitioning())) {
			info.getPartitions().get(0).setHighValue("'JP', 'US'");
			info.getPartitions().get(1).setHighValue("'DEFAULT'");
		}
		return table;
	}

	@Test
	void listAppendMaintainsLocalSegmentsAndDropMaintainsGlobalIndexes() {
		Table before = listTable(), after = append(before);
		assertEquals(
				List.of("ALTER TABLE APP.T ADD PARTITION P3 VALUES (300) TABLESPACE DATA",
						"ALTER INDEX APP.IX RENAME PARTITION P3 TO IP3",
						"ALTER INDEX APP.IX REBUILD PARTITION IP3 TABLESPACE INDEX_DATA"),
				sql(before, after, DialectHolder.oracle10gDialect));
		assertEquals(List.of("ALTER TABLE APP.T DROP PARTITION P1 UPDATE INDEXES"),
				sql(before, drop(before, 0), DialectHolder.oracle10gDialect));
	}

	@Test
	void listDefaultBlocksAppendButAllowsDrop() {
		Table before = listTable();
		before.getPartitioning().getPartitions().get(0).setHighValue(" default ");
		before.getIndexes().get(0).getPartitioning().getPartitions().get(0).setHighValue(" default ");
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, append(before), DialectHolder.oracle26aiDialect));
		assertEquals(1, sql(before, drop(before, 0), DialectHolder.oracle26aiDialect).size());
	}

	@Test
	void listIotAndUncoordinatedListChangesAreRejected() {
		Table before = listTable();
		before.getSpecifics().put("oracle.table.organization", "INDEX");
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, append(before), DialectHolder.oracle26aiDialect));
		Table heap = listTable(), changed = heap.clone();
		changed.getPartitioning().getPartitions().get(0).setHighValue("'GB'");
		assertThrows(IllegalArgumentException.class, () -> sql(heap, changed, DialectHolder.oracle26aiDialect));
	}

	@Test
	void listMaintenanceKeepsVersionCompositeAndOperationGuards() {
		Table before = listTable(), after = append(before);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.defaultDialect));
		before.getPartitioning().setSubPartitioningType(PartitioningType.Hash);
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, append(before), DialectHolder.oracle26aiDialect));
		Table source = listTable(), target = append(source);
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setAllowAddPartition(false);
		registry.getTableOptions().setAllowDropPartition(false);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(target, SqlType.ALTER).createDiffSql(source.diff(target)));
		Table deleted = drop(source, 0);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(deleted, SqlType.ALTER).createDiffSql(source.diff(deleted)));
	}

	@Test
	void appendCanInheritTheLocalIndexDefaultTablespace() {
		Table before = table(false, true);
		before.getIndexes().get(0).setTableSpaceName("DEFAULT_INDEX_SPACE");
		Table after = append(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(2).setTableSpaceName(null);
		assertEquals(2, sql(before, after, DialectHolder.oracle26aiDialect).size());
	}

	@Test
	void existingIdentifierQuotingOptionsApplyToEveryStatement() {
		Table before = table(false, true), after = append(before);
		after.getPartitioning().getPartitions().get(2).setName("p3");
		after.getIndexes().get(0).getPartitioning().getPartitions().get(2).setName("ip3");
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setQuateObjectName(false);
		var operations = factory.createDiffSql(before.diff(after));
		assertTrue(operations.get(0).getSqlText().contains("ADD PARTITION p3"), operations.get(0).getSqlText());
		assertTrue(operations.get(1).getSqlText().contains("RENAME PARTITION p3 TO ip3"),
				operations.get(1).getSqlText());
	}

	@Test
	void dropRemovesOnlyTheCorrespondingLocalPartitionAndMaintainsGlobalIndexes() {
		Table before = table(false, true);
		Index global = new Index("GLOBAL_IX");
		before.getIndexes().add(global);
		global.getColumns().add("ID");
		Table after = drop(before, 0);
		assertEquals(List.of("ALTER TABLE APP.T DROP PARTITION P1 UPDATE INDEXES"),
				sql(before, after, DialectHolder.oracle26aiDialect));
		var operation = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER)
				.createDiffSql(before.diff(after)).get(0);
		assertEquals(SqlType.DROP, operation.getSqlType());
	}

	@Test
	void addAndDropOptionsAreHonored() {
		Table before = table(false, true), added = append(before), deleted = drop(before, 0);
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setAllowAddPartition(false);
		registry.getTableOptions().setAllowDropPartition(false);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(added, SqlType.ALTER).createDiffSql(before.diff(added)));
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(deleted, SqlType.ALTER).createDiffSql(before.diff(deleted)));
	}

	@Test
	void maxvalueAndAutomaticNameCollisionsAreRejected() {
		Table before = table(false, true);
		before.getPartitioning().getPartitions().get(1).setHighValue("MAXVALUE");
		Table after = append(before);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table collision = table(false, true);
		collision.getIndexes().get(0).getPartitioning().getPartitions().get(0).setName("P3");
		Table target = append(collision);
		assertThrows(IllegalArgumentException.class, () -> sql(collision, target, DialectHolder.oracle26aiDialect));
	}

	@Test
	void localCorrespondenceAndUnusableNewSegmentsAreValidated() {
		Table before = table(false, true), after = append(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(2).getSpecifics().put("oracle.index.status",
				"UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table deleted = before.clone();
		deleted.getPartitioning().getPartitions().remove(0);
		deleted.getIndexes().get(0).getPartitioning().getPartitions().remove(1);
		assertThrows(IllegalArgumentException.class, () -> sql(before, deleted, DialectHolder.oracle26aiDialect));
	}

	@Test
	void iotAppendAndDropUseTheirNativeIndexMaintenanceRules() {
		Table before = table(true, false), after = append(before);
		assertEquals(List.of("ALTER TABLE APP.T ADD PARTITION P3 VALUES LESS THAN (300) TABLESPACE DATA"),
				sql(before, after, DialectHolder.oracle12cDialect));
		assertEquals(List.of("ALTER TABLE APP.T DROP PARTITION P1 UPDATE GLOBAL INDEXES"),
				sql(before, drop(before, 0), DialectHolder.oracle12cDialect));
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle11gDialect));
	}

	private Table splitIot(Table before) {
		Table after = before.clone();
		Partition original = after.getPartitioning().getPartitions().get(1);
		original.setName("LOW");
		original.setHighValue("150");
		after.getPartitioning().getPartitions().add(new Partition("HIGH").setHighValue("200"));
		return after;
	}

	@Test
	void iotSplitAndMergePreserveRowsWithoutUpdateIndexes() {
		Table before = table(true, false), after = splitIot(before);
		assertEquals(List.of("ALTER TABLE APP.T SPLIT PARTITION P2 AT (150) INTO (PARTITION LOW, PARTITION HIGH)"),
				sql(before, after, DialectHolder.oracle12cDialect));
		assertEquals(List.of("ALTER TABLE APP.T MERGE PARTITIONS LOW, HIGH INTO PARTITION P2"),
				sql(after, before, DialectHolder.oracle12cDialect));
	}

	@Test
	void opaqueIotDdlIsNeverReplayedAfterPartitionChanges() {
		Table before = table(true, false);
		OracleTableDefinition.set(before,
				"CREATE TABLE APP.T (ID NUMBER PRIMARY KEY) ORGANIZATION INDEX PARTITION BY RANGE(ID) (PARTITION P1 VALUES LESS THAN (100), PARTITION P2 VALUES LESS THAN (200))");
		Table after = splitIot(before);
		assertEquals(1, sql(before, after, DialectHolder.oracle26aiDialect).size());
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().createSql(after, SqlType.CREATE));
	}

	@Test
	void specialIndexesAndAllPartitionRemovalAreRejected() {
		Table before = table(false, true);
		before.getIndexes().get(0).setIndexType(IndexType.Domain);
		Table after = drop(before, 0);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table all = table(false, true), empty = all.clone();
		empty.getPartitioning().getPartitions().clear();
		empty.getIndexes().get(0).getPartitioning().getPartitions().clear();
		assertThrows(IllegalArgumentException.class, () -> sql(all, empty, DialectHolder.oracle26aiDialect));
	}

	@Test
	void unmodeledCatalogIndexesCannotBypassTypeValidation() {
		Table before = table(true, false);
		before.getIndexes().get(0).setIndexType((IndexType) null);
		before.getIndexes().get(0).getSpecifics().put("oracle.index.unmodeledType", "CUSTOM");
		Table after = drop(before, 0);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
	}

	@Test
	void iotPartitionMoveKeepsLocalSecondaryIndexesUsable() {
		Table before = table(true, true), after = before.clone();
		after.getPartitioning().getPartitions().get(0).setTableSpaceName("New Space");
		assertEquals(List.of("ALTER TABLE APP.T MOVE PARTITION P1 TABLESPACE \"New Space\""),
				sql(before, after, DialectHolder.oracle12cDialect));
		assertEquals("USABLE", after.getIndexes().get(0).getPartitioning().getPartitions().get(0).getSpecifics()
				.get("oracle.index.status"));
	}

	@Test
	void iotMoveRejectsMultiplePartitionsAndIndexChanges() {
		Table before = table(true, true), after = before.clone();
		after.getPartitioning().getPartitions().get(0).setTableSpaceName("NEW");
		after.getPartitioning().getPartitions().get(1).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle12cDialect));
		Table indexChange = before.clone();
		indexChange.getPartitioning().getPartitions().get(0).setTableSpaceName("NEW");
		indexChange.getIndexes().get(0).getSpecifics().put("oracle.index.visibility", "INVISIBLE");
		assertThrows(IllegalArgumentException.class, () -> sql(before, indexChange, DialectHolder.oracle12cDialect));
	}

	@Test
	void iotSplitRejectsMissingBoundsAndSimultaneousColumnChanges() {
		Table before = table(true, false), after = splitIot(before);
		after.getColumns().add("EXTRA");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle12cDialect));
		Table missing = table(true, false);
		missing.getPartitioning().getPartitions().get(1).setHighValue((String) null);
		Table target = splitIot(missing);
		target.getPartitioning().getPartitions().get(2).setHighValue((String) null);
		assertThrows(IllegalArgumentException.class, () -> sql(missing, target, DialectHolder.oracle12cDialect));
	}
}
