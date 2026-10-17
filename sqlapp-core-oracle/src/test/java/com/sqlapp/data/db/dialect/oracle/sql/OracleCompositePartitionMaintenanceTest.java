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
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID");
		table.getColumns().add("REGION");
		Partitioning info = new Partitioning().setPartitioningType(root).setSubPartitioningType(sub);
		info.getPartitioningColumns().add("ID");
		info.getSubPartitioningColumns().add("REGION");
		for (int i = 1; i <= 2; i++) {
			Partition part = new Partition("P" + i)
					.setHighValue(root == PartitioningType.Hash ? null : Integer.toString(i * 100));
			for (int j = 1; j <= 2; j++)
				part.getSubPartitions().add(new SubPartition("S" + i + j)
						.setHighValue(sub == PartitioningType.Hash ? null : Integer.toString(j * 10)));
			info.getPartitions().add(part);
		}
		table.setPartitioning(info);
		Index index = new Index("IX");
		table.getIndexes().add(index);
		index.getColumns().add("ID");
		index.getSpecifics().put("oracle.index.locality", "LOCAL");
		index.setPartitioning(info.clone());
		for (Partition part : index.getPartitioning().getPartitions())
			for (SubPartition segment : part.getSubPartitions())
				segment.getSpecifics().put("oracle.index.status", "USABLE");
		Index global = new Index("GIX");
		table.getIndexes().add(global);
		global.getColumns().add("ID");
		return table.clone();
	}

	private Table remove(Table before, int parent, Integer child) {
		Table after = before.clone();
		for (Partitioning info : List.of(after.getPartitioning(), after.getIndexes().get("IX").getPartitioning())) {
			if (child == null)
				info.getPartitions().remove(parent);
			else
				info.getPartitions().get(parent).getSubPartitions().remove((int) child);
		}
		return after;
	}

	private String sql(Table before, Table after, Dialect dialect) {
		var factory = dialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		var operations = factory.createDiffSql(before.diff(after));
		assertEquals(1, operations.size());
		return operations.get(0).getSqlText().replaceAll("\\s+", " ").replace("( ", "(").trim();
	}

	private Table append(Table before, String boundary) {
		Table after = before.clone();
		after.getPartitioning().getPartitions().get(0).getSubPartitions()
				.add(new SubPartition("S13").setHighValue(boundary).setTableSpaceName("DATA"));
		SubPartition segment = new SubPartition("IS13").setHighValue(boundary).setTableSpaceName("INDEX_DATA");
		segment.getSpecifics().put("oracle.index.status", "USABLE");
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().add(segment);
		return after;
	}

	private Table appendParent(Table before) {
		Table after = before.clone();
		for (Partitioning info : List.of(after.getPartitioning(), after.getIndexes().get("IX").getPartitioning())) {
			Partition added = info.getPartitions().get(1).clone();
			added.setName(info.getIndex() == null ? "P3" : "IP3");
			added.setHighValue("300");
			for (SubPartition child : added.getSubPartitions())
				child.setName("NEW_" + child.getName() + (info.getIndex() == null ? "" : "_I"));
			info.getPartitions().add(added);
		}
		return after;
	}

	@Test
	void parentAddNamesEveryLocalChildAndPreservesExistingPartitions() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = appendParent(before);
		assertEquals(
				"ALTER TABLE APP.T ADD PARTITION P3 VALUES LESS THAN (300) (SUBPARTITION NEW_S21, SUBPARTITION NEW_S22) UPDATE INDEXES (IX (PARTITION IP3 (SUBPARTITION NEW_S21_I, SUBPARTITION NEW_S22_I)))",
				sql(before, after, DialectHolder.oracle10gDialect));
		assertEquals(2, before.getPartitioning().getPartitions().size());
	}

	@Test
	void parentAddHandlesListRangeChildrenAndExplicitStorage() {
		for (PartitioningType type : List.of(PartitioningType.List, PartitioningType.Range)) {
			Table before = source(PartitioningType.Range, type), after = appendParent(before);
			after.getPartitioning().getPartitions().get(2).getSubPartitions().get(0).setTableSpaceName("Data Space");
			after.getIndexes().get("IX").getPartitioning().getPartitions().get(2).getSubPartitions().get(0)
					.setTableSpaceName("Index Space");
			String statement = sql(before, after, DialectHolder.oracle12cDialect);
			assertTrue(statement.contains(type == PartitioningType.List ? "VALUES (10)" : "VALUES LESS THAN (10)"));
			assertTrue(statement.contains("TABLESPACE \"Data Space\""));
			assertTrue(statement.contains("TABLESPACE \"Index Space\""));
		}
	}

	@Test
	void parentAddRejectsMissingChildrenAndMismatchedLocalBounds() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = appendParent(before);
		after.getPartitioning().getPartitions().get(2).getSubPartitions().clear();
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table mismatch = appendParent(before);
		mismatch.getIndexes().get("IX").getPartitioning().getPartitions().get(2).getSubPartitions().get(0)
				.setHighValue("999");
		assertThrows(IllegalArgumentException.class, () -> sql(before, mismatch, DialectHolder.oracle26aiDialect));
	}

	@Test
	void parentAddRejectsBoundariesAndConcurrentChanges() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash);
		before.getPartitioning().getPartitions().get(1).setHighValue("MAXVALUE");
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, appendParent(before), DialectHolder.oracle26aiDialect));
		Table list = source(PartitioningType.List, PartitioningType.List);
		list.getPartitioning().getPartitions().get(0).setHighValue("DEFAULT");
		assertThrows(IllegalArgumentException.class,
				() -> sql(list, appendParent(list), DialectHolder.oracle26aiDialect));
		Table source = source(PartitioningType.Range, PartitioningType.Hash), changed = appendParent(source);
		changed.getIndexes().get("GIX").setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(source, changed, DialectHolder.oracle26aiDialect));
	}

	@Test
	void parentAddValidatesExistingSegmentsAndNewNames() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = appendParent(before);
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(0)
				.setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table collision = appendParent(before);
		collision.getPartitioning().getPartitions().get(2).getSubPartitions().get(0).setName("S11");
		assertThrows(IllegalArgumentException.class, () -> sql(before, collision, DialectHolder.oracle26aiDialect));
	}

	@Test
	void parentAddSupportsNoLocalIndexesAndHonorsAddOption() {
		Table before = source(PartitioningType.List, PartitioningType.List), after = appendParent(before);
		var registry = DialectHolder.oracle12cDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setAllowAddPartition(false);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		before.getIndexes().remove("IX");
		after.getIndexes().remove("IX");
		assertTrue(sql(before, after, DialectHolder.oracle12cDialect).endsWith("UPDATE INDEXES"));
	}

	@Test
	void appendRangeListAndHashSubpartitionsWithExplicitLocalStorage() {
		for (PartitioningType type : List.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash)) {
			Table before = source(PartitioningType.Range, type),
					after = append(before, type == PartitioningType.Hash ? null : "30");
			String values = type == PartitioningType.Hash ? ""
					: type == PartitioningType.Range ? " VALUES LESS THAN (30)" : " VALUES (30)";
			assertEquals(
					"ALTER TABLE APP.T MODIFY PARTITION P1 ADD SUBPARTITION S13" + values
							+ " TABLESPACE DATA UPDATE INDEXES (IX (SUBPARTITION IS13 TABLESPACE INDEX_DATA))",
					sql(before, after, DialectHolder.oracle12cDialect));
			assertEquals(2, before.getPartitioning().getPartitions().get(0).getSubPartitions().size());
		}
	}

	@Test
	void appendRespectsDefaultAndMaxvalueBoundaries() {
		for (PartitioningType type : List.of(PartitioningType.Range, PartitioningType.List)) {
			Table before = source(PartitioningType.Range, type);
			before.getPartitioning().getPartitions().get(0).getSubPartitions().get(1)
					.setHighValue(type == PartitioningType.List ? "DEFAULT" : "MAXVALUE");
			assertThrows(IllegalArgumentException.class,
					() -> sql(before, append(before, "30"), DialectHolder.oracle26aiDialect));
		}
	}

	@Test
	void appendHonorsAddOptionIndependentlyOfDropOption() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = append(before, "30");
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setAllowDropPartition(false);
		assertEquals(1, registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).size());
		registry.getTableOptions().setAllowAddPartition(false);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}

	@Test
	void appendCannotHideExistingSegmentOrGlobalIndexChanges() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = append(before, null);
		after.getIndexes().get("GIX").setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table changed = append(before, null);
		changed.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, changed, DialectHolder.oracle26aiDialect));
	}

	@Test
	void appendValidatesLocalStateBoundsAndNames() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = append(before, "30");
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(2)
				.setHighValue("40");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table unusable = append(before, "30");
		unusable.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(2)
				.getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> sql(before, unusable, DialectHolder.oracle26aiDialect));
		Table collision = append(before, "30");
		collision.getPartitioning().getPartitions().get(0).getSubPartitions().get(2).setName("S21");
		assertThrows(IllegalArgumentException.class, () -> sql(before, collision, DialectHolder.oracle26aiDialect));
	}

	@Test
	void appendWithoutLocalIndexesAndListListUseTheSamePath() {
		Table before = source(PartitioningType.List, PartitioningType.List), after = append(before, "30");
		before.getIndexes().remove("IX");
		after.getIndexes().remove("IX");
		assertEquals(
				"ALTER TABLE APP.T MODIFY PARTITION P1 ADD SUBPARTITION S13 VALUES (30) TABLESPACE DATA UPDATE INDEXES",
				sql(before, after, DialectHolder.oracle12cDialect));
	}

	@Test
	void rootDropAlsoRemovesCorrespondingLocalSubpartitions() {
		for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.List)) {
			Table before = source(PartitioningType.Range, sub), after = remove(before, 0, null);
			assertEquals("ALTER TABLE APP.T DROP PARTITION P1 UPDATE INDEXES",
					sql(before, after, DialectHolder.oracle10gDialect));
			assertEquals(2, before.getPartitioning().getPartitions().size());
		}
	}

	@Test
	void listAndRangeSubpartitionDropPreserveAllOtherSegments() {
		for (PartitioningType sub : List.of(PartitioningType.Range, PartitioningType.List)) {
			Table before = source(PartitioningType.Range, sub), after = remove(before, 0, 0);
			assertEquals("ALTER TABLE APP.T DROP SUBPARTITION S11 UPDATE INDEXES",
					sql(before, after, DialectHolder.oracle12cDialect));
			var factory = DialectHolder.oracle12cDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
			assertEquals(SqlType.DROP, factory.createDiffSql(before.diff(after)).get(0).getSqlType());
		}
	}

	@Test
	void hashSubpartitionReductionCoalescesWithoutDeletingRows() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = remove(before, 0, 1);
		assertEquals("ALTER TABLE APP.T MODIFY PARTITION P1 COALESCE SUBPARTITION UPDATE INDEXES",
				sql(before, after, DialectHolder.oracle10gDialect));
		var factory = DialectHolder.oracle10gDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		assertEquals(SqlType.ALTER, factory.createDiffSql(before.diff(after)).get(0).getSqlType());
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, remove(before, 0, 0), DialectHolder.oracle26aiDialect));
	}

	@Test
	void compositeHashRootCoalescesOnlyTheLastPartition() {
		Table before = source(PartitioningType.Hash, PartitioningType.List);
		assertEquals("ALTER TABLE APP.T COALESCE PARTITION UPDATE INDEXES",
				sql(before, remove(before, 1, null), DialectHolder.oracle18cDialect));
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, remove(before, 1, null), DialectHolder.oracle12cDialect));
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, remove(before, 0, null), DialectHolder.oracle26aiDialect));
	}

	@Test
	void localIndexRemovalMustMatchTheTablePosition() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		var children = after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions();
		children.clear();
		children.add(before.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(0)
				.clone());
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
	}

	@Test
	void parentDefaultsTemplatesAndConcurrentChangesAreRejected() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		after.getPartitioning().getPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table changed = remove(before, 0, 0);
		changed.getColumns().add("EXTRA");
		assertThrows(IllegalArgumentException.class, () -> sql(before, changed, DialectHolder.oracle26aiDialect));
		Table defaults = remove(before, 0, 0);
		defaults.getPartitioning().setSubPartitionSize(4);
		assertThrows(IllegalArgumentException.class, () -> sql(before, defaults, DialectHolder.oracle26aiDialect));
	}

	@Test
	void allChildrenAndAllParentsCannotBeRemoved() {
		Table before = source(PartitioningType.Range, PartitioningType.List), one = remove(before, 0, 1);
		assertThrows(IllegalArgumentException.class,
				() -> sql(one, remove(one, 0, 0), DialectHolder.oracle26aiDialect));
		Table last = remove(before, 1, null);
		assertThrows(IllegalArgumentException.class,
				() -> sql(last, remove(last, 0, null), DialectHolder.oracle26aiDialect));
	}

	@Test
	void versionAndIotBoundariesRemainExplicit() {
		Table before = source(PartitioningType.Range, PartitioningType.Range);
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, remove(before, 0, 0), DialectHolder.oracle10gDialect));
		before.getSpecifics().put("oracle.table.organization", "INDEX");
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, remove(before, 0, 0), DialectHolder.oracle26aiDialect));
	}

	@Test
	void dropAndOnlineOptionsAreHonored() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setAllowDropPartition(false);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		var online = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		online.getTableOptions().setOnlineIndex(true);
		assertThrows(IllegalArgumentException.class,
				() -> online.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}

	@Test
	void ambiguousNamesAndUnusableCoalesceSegmentsAreRejected() {
		Table before = source(PartitioningType.Range, PartitioningType.List);
		before.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setName("S11");
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, remove(before, 0, 0), DialectHolder.oracle26aiDialect));
		Table hash = source(PartitioningType.Range, PartitioningType.Hash);
		hash.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(0).getSpecifics()
				.put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class,
				() -> sql(hash, remove(hash, 0, 1), DialectHolder.oracle26aiDialect));
	}

	@Test
	void globalOnlyTablesAndQuotedNamesUseTheCompositePlanner() {
		Table before = source(PartitioningType.Range, PartitioningType.List);
		before.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setName("child part");
		Table after = remove(before, 0, 0);
		before.getIndexes().remove("IX");
		after.getIndexes().remove("IX");
		assertEquals("ALTER TABLE APP.T DROP SUBPARTITION \"child part\" UPDATE INDEXES",
				sql(before, after, DialectHolder.oracle10gDialect));
		before.getIndexes().clear();
		after.getIndexes().clear();
		assertTrue(sql(before, after, DialectHolder.oracle10gDialect).contains("DROP SUBPARTITION"));
	}

	@Test
	void multipleChildEditsAndSpecialIndexesAreRejected() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = remove(before, 0, 0);
		after.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table special = source(PartitioningType.Range, PartitioningType.List);
		special.getIndexes().get("IX").setIndexType(IndexType.Domain);
		assertThrows(IllegalArgumentException.class,
				() -> sql(special, remove(special, 0, 0), DialectHolder.oracle26aiDialect));
	}
}
