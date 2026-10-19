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
	@Test
	void trailingMaxvalueAllowsMulticolumnParentAndChildAdd() {
		Table before = source(PartitioningType.Range, PartitioningType.Range);
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning())) {
			info.getPartitioningColumns().add("REGION");
			info.getSubPartitioningColumns().add("ID");
			for (Partition part : info.getPartitions()) {
				part.setHighValue(part.getHighValue() + ", MAXVALUE");
				for (SubPartition child : part.getSubPartitions())
					child.setHighValue(child.getHighValue() + ", MAXVALUE");
			}
		}
		Table parent = appendParent(before);
		for (Partitioning info : List.of(parent.getPartitioning(), parent.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(2).setHighValue("300, MAXVALUE");
		assertTrue(sql(before, parent, DialectHolder.oracle26aiDialect).contains("ADD PARTITION"));
		assertTrue(sql(before, append(before, "30, MAXVALUE"), DialectHolder.oracle26aiDialect)
				.contains("ADD SUBPARTITION"));
	}

	@Test
	void finiteTextMaxvalueDoesNotBlockParentOrChildAdd() {
		Table before = source(PartitioningType.Range, PartitioningType.Range);
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning())) {
			info.getPartitions().get(1).setHighValue("'MAXVALUE'");
			info.getPartitions().get(0).getSubPartitions().get(1).setHighValue("q'[MAXVALUE]'");
		}
		assertTrue(sql(before, appendParent(before), DialectHolder.oracle26aiDialect).contains("ADD PARTITION"));
		assertTrue(sql(before, append(before, "'ZZZ'"), DialectHolder.oracle26aiDialect).contains("ADD SUBPARTITION"));
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(0).getSubPartitions().get(1).setHighValue("MAXVALUE, 10");
		assertThrows(IllegalArgumentException.class,
				() -> sql(before, append(before, "'ZZZ'"), DialectHolder.oracle26aiDialect));
	}

	@Test
	void quotedChildNamesKeepCaseDuringAddDropAndSplit() {
		Table before = source(PartitioningType.Range, PartitioningType.Range);
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(1).getSubPartitions().get(0).setName("\"s11\"");
		assertTrue(
				sql(before, remove(before, 0, 0), DialectHolder.oracle26aiDialect).contains("DROP SUBPARTITION S11"));
		Table added = append(before, "30");
		for (Partitioning info : List.of(added.getPartitioning(), added.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(0).getSubPartitions().get(2).setName("\"s12\"");
		assertTrue(sql(before, added, DialectHolder.oracle26aiDialect).contains("\"s12\""));
		assertTrue(
				sql(before, splitChild(before, false), DialectHolder.oracle26aiDialect).contains("SPLIT SUBPARTITION"));
		Table parents = splitParent(before);
		assertFalse(DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(parents, SqlType.ALTER)
				.createDiffSql(before.diff(parents)).isEmpty());
		Table collision = append(before, "30");
		collision.getPartitioning().getPartitions().get(0).getSubPartitions().get(2).setName("\"S11\"");
		assertThrows(IllegalArgumentException.class, () -> sql(before, collision, DialectHolder.oracle26aiDialect));
	}

	@Test
	void identifierIdentityFollowsBuilderQuoting() {
		var dialect = DialectHolder.oracle26aiDialect;
		var naming = new com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder(dialect);
		naming.setQuateObjectName(false);
		assertEquals(OracleCompositeSegmentNames.identifier("s", dialect, naming),
				OracleCompositeSegmentNames.identifier("S", dialect, naming));
		assertNotEquals(OracleCompositeSegmentNames.identifier("\"s\"", dialect, naming),
				OracleCompositeSegmentNames.identifier("S", dialect, naming));
		naming.setQuateObjectName(true);
		assertNotEquals(OracleCompositeSegmentNames.identifier("s", dialect, naming),
				OracleCompositeSegmentNames.identifier("S", dialect, naming));
	}

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
	void observedChildStatisticsDoNotConstrainMergeOrMutateTheCanonicalModel() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), split = splitParent(before);
		for (Partitioning info : List.of(split.getPartitioning(), split.getIndexes().get("IX").getPartitioning())) {
			info.getPartitions().get(1).getStatistics().put("NUM_ROWS", "1");
			info.getPartitions().get(2).getStatistics().put("NUM_ROWS", "2");
			info.getPartitions().get(1).getSubPartitions().get(0).getStatistics().put("NUM_ROWS", "1");
			info.getPartitions().get(2).getSubPartitions().get(0).getStatistics().put("NUM_ROWS", "2");
		}
		Table snapshot = split.clone();
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertEquals(2, registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(split.diff(before)).size());
		assertEquals(snapshot, split);
	}

	@Test
	void parentRangeHashSplitAndMergeUseBatchedCountAndNameRestoration() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash);
		for (Partition part : before.getIndexes().get("IX").getPartitioning().getPartitions())
			for (SubPartition child : part.getSubPartitions())
				child.setCompressionType("DISABLED");
		Table after = splitParent(before);
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var split = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(2, split.size());
		assertTrue(split.get(0).getSqlText().contains("SPLIT PARTITION P2 AT (150)"));
		String block = split.get(1).getSqlText();
		assertTrue(block.contains("BULK COLLECT INTO segments"));
		assertEquals(2, block.split("BULK COLLECT", -1).length - 1);
		assertTrue(block.contains("ADD SUBPARTITION UPDATE INDEXES"));
		assertTrue(block.contains("COALESCE SUBPARTITION UPDATE INDEXES"));
		assertTrue(block.contains("RENAME SUBPARTITION"));
		assertTrue(block.contains("ALL_TAB_SUBPARTITIONS"));
		assertTrue(block.contains("ALL_IND_SUBPARTITIONS"));
		var merge = registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before));
		assertEquals(2, merge.size());
		assertTrue(merge.get(0).getSqlText().contains("MERGE PARTITIONS PL, PR"));
		before.getIndexes().remove("IX");
		after.getIndexes().remove("IX");
		after.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setName("O'Brien");
		var globalOnly = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(2, globalOnly.size());
		assertTrue(globalOnly.get(1).getSqlText().toUpperCase(java.util.Locale.ROOT).contains("O''BRIEN"));
	}

	@Test
	void parentListHashSplitMergeAndVersionBoundary() {
		Table before = source(PartitioningType.List, PartitioningType.Hash);
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(1).setHighValue("150,200");
		Table after = splitParent(before);
		var registry = DialectHolder.oracle11gDialect.createSqlFactoryRegistry();
		var split = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertEquals(2, split.size());
		assertTrue(split.get(0).getSqlText().contains("SPLIT PARTITION P2 VALUES (150)"));
		assertEquals(2, registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before)).size());
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle10gDialect.createSqlFactoryRegistry()
				.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		Table invalid = after.clone();
		for (Partitioning info : List.of(invalid.getPartitioning(), invalid.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(2).setHighValue("999");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(invalid, SqlType.ALTER).createDiffSql(before.diff(invalid)));
	}

	@Test
	void defaultListHashParentUsesSharedListValidationWithoutLocalIndexes() {
		Table before = source(PartitioningType.List, PartitioningType.Hash);
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(1).setHighValue("DEFAULT");
		Table after = splitParent(before);
		for (Partitioning info : List.of(after.getPartitioning(), after.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(2).setHighValue("DEFAULT");
		before.getIndexes().remove("IX");
		after.getIndexes().remove("IX");
		var registry = DialectHolder.oracle11gR2Dialect.createSqlFactoryRegistry();
		assertEquals(2, registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).size());
		assertEquals(2, registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before)).size());
		assertEquals("DEFAULT", before.getPartitioning().getPartitions().get(1).getHighValue());
	}

	private Table splitParent(Table before) {
		Table after = before.clone();
		for (Partitioning info : List.of(after.getPartitioning(), after.getIndexes().get("IX").getPartitioning())) {
			Partition original = info.getPartitions().remove(1);
			boolean index = info.getIndex() != null;
			for (boolean left : List.of(true, false)) {
				Partition part = original.clone().setName((index ? "I" : "P") + (left ? "L" : "R"))
						.setHighValue(left ? "150" : "200");
				for (int j = 0; j < part.getSubPartitions().size(); j++)
					part.getSubPartitions().get(j).setName((index ? "IC" : "C") + (left ? "L" : "R") + j);
				info.getPartitions().add(part);
			}
		}
		return after;
	}

	@Test
	void rangeAndListParentsRestoreRangeAndListChildRouting() {
		for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List))
			for (PartitioningType sub : List.of(PartitioningType.Range, PartitioningType.List)) {
				Table before = source(root, sub);
				for (Partitioning info : List.of(before.getPartitioning(),
						before.getIndexes().get("IX").getPartitioning())) {
					if (root == PartitioningType.List)
						info.getPartitions().get(1).setHighValue("150,200");
					for (Partition parent : info.getPartitions())
						parent.getSubPartitions().get(1)
								.setHighValue(sub == PartitioningType.List ? "DEFAULT" : "MAXVALUE");
				}
				Table after = splitParent(before);
				var registry = DialectHolder.oracle11gDialect.createSqlFactoryRegistry();
				var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
				assertEquals(3, operations.size());
				assertTrue(operations.get(1).getSqlText().contains("expected_bound"));
				assertFalse(operations.get(1).getSqlText().contains("MERGE SUBPARTITIONS"));
				assertTrue(operations.get(2).getSqlText().contains("IF 0=0"));
				var merged = registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before));
				assertEquals(3, merged.size());
				assertTrue(merged.get(1).getSqlText().contains("BULK COLLECT INTO children"));
				before.getIndexes().remove("IX");
				after.getIndexes().remove("IX");
				assertEquals(3, registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).size());
			}
	}

	@Test
	void parentRoutingRejectsChangedBoundsDuplicateValuesAndIndividualPhysicalOverrides() {
		Table before = source(PartitioningType.Range, PartitioningType.List);
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
			for (Partition parent : info.getPartitions())
				parent.getSubPartitions().get(1).setHighValue("DEFAULT");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		Table after = splitParent(before);
		after.getPartitioning().getPartitions().get(1).getSubPartitions().get(1).setHighValue("20");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		before.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).getSpecifics().put("PCT_FREE", "20");
		Table physical = splitParent(before);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(physical, SqlType.ALTER).createDiffSql(before.diff(physical)));
		before.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).getSpecifics().clear();
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning())) {
			var children = info.getPartitions().get(1).getSubPartitions();
			children.add(1, children.get(0).clone().setName(info.getIndex() == null ? "DUP" : "IDUP"));
		}
		Table duplicate = splitParent(before);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(duplicate, SqlType.ALTER).createDiffSql(before.diff(duplicate)));
	}

	@Test
	void finiteChildrenSurviveParentSplitButStillCannotMerge() {
		for (PartitioningType sub : List.of(PartitioningType.Range, PartitioningType.List)) {
			Table before = source(PartitioningType.Range, sub), after = splitParent(before);
			var registry = DialectHolder.oracle11gR2Dialect.createSqlFactoryRegistry();
			var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
			assertEquals(3, operations.size());
			assertTrue(operations.get(1).getSqlText().contains("'20' expected_bound"));
			assertThrows(IllegalArgumentException.class,
					() -> registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before)));
		}
	}

	@Test
	void inheritedBoundaryCheckEscapesLiteralsAndRejectsOversizedBounds() {
		Table before = source(PartitioningType.Range, PartitioningType.List);
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(1).getSubPartitions().get(0).setHighValue("'O''Brien'");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		Table after = splitParent(before);
		var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
		assertTrue(operations.get(1).getSqlText().contains("'''O''''Brien''' expected_bound"));
		for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
			info.getPartitions().get(1).getSubPartitions().get(0).setHighValue("1".repeat(4001));
		Table large = splitParent(before);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(large, SqlType.ALTER).createDiffSql(before.diff(large)));
	}

	@Test
	void compressedParentsPreserveInheritedChildCompression() {
		for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.Range, PartitioningType.List))
			for (String mode : List.of("BASIC", "ADVANCED")) {
				Table before = source(PartitioningType.Range, sub);
				if (sub != PartitioningType.Hash)
					for (Partitioning info : List.of(before.getPartitioning(),
							before.getIndexes().get("IX").getPartitioning()))
						for (Partition parent : info.getPartitions())
							parent.getSubPartitions().get(1)
									.setHighValue(sub == PartitioningType.List ? "DEFAULT" : "MAXVALUE");
				Partition parent = before.getPartitioning().getPartitions().get(1);
				parent.setCompression(true);
				parent.getSpecifics().put("COMPRESS_FOR", mode);
				parent.getSpecifics().put("PCT_FREE", mode.equals("BASIC") ? "0" : "10");
				for (SubPartition child : parent.getSubPartitions()) {
					child.setCompression(true);
					child.getSpecifics().put("COMPRESS_FOR", mode);
					child.getSpecifics().put("PCT_FREE", mode.equals("BASIC") ? "0" : "10");
				}
				Table after = splitParent(before);
				var registry = DialectHolder.oracle11gR2Dialect.createSqlFactoryRegistry();
				var operations = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
				assertEquals(sub == PartitioningType.Hash ? 2 : 3, operations.size());
				assertTrue(operations.get(0).getSqlText().contains("PARTITION PL COMPRESS"));
				assertEquals(operations.size(),
						registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before)).size());
				parent.getSubPartitions().get(0).setCompression(false);
				parent.getSubPartitions().get(0).getSpecifics().remove("COMPRESS_FOR");
				Table mixed = splitParent(before);
				assertThrows(IllegalArgumentException.class,
						() -> registry.getSqlFactory(mixed, SqlType.ALTER).createDiffSql(before.diff(mixed)));
			}
	}

	@Test
	void tableCompressionDefaultsRemainUnchangedDuringParentMaintenance() {
		for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List))
			for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.Range, PartitioningType.List))
				for (String mode : List.of("BASIC", "ADVANCED")) {
					Table before = source(root, sub);
					before.setCompression(true);
					before.setCompressionType(mode);
					if (root == PartitioningType.List)
						for (Partitioning info : List.of(before.getPartitioning(),
								before.getIndexes().get("IX").getPartitioning()))
							info.getPartitions().get(1).setHighValue("150,200");
					for (Partition parent : before.getPartitioning().getPartitions()) {
						parent.setCompression(true);
						parent.getSpecifics().put("COMPRESS_FOR", mode);
						parent.getSpecifics().put("PCT_FREE", mode.equals("BASIC") ? "0" : "10");
						for (SubPartition child : parent.getSubPartitions()) {
							child.setCompression(true);
							child.getSpecifics().put("COMPRESS_FOR", mode);
							child.getSpecifics().put("PCT_FREE", mode.equals("BASIC") ? "0" : "10");
						}
					}
					if (sub != PartitioningType.Hash)
						for (Partitioning info : List.of(before.getPartitioning(),
								before.getIndexes().get("IX").getPartitioning()))
							for (Partition parent : info.getPartitions())
								parent.getSubPartitions().get(1)
										.setHighValue(sub == PartitioningType.List ? "DEFAULT" : "MAXVALUE");
					Table after = splitParent(before);
					var registry = DialectHolder.oracle11gR2Dialect.createSqlFactoryRegistry();
					assertEquals(sub == PartitioningType.Hash ? 2 : 3,
							registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).size());
					assertEquals(sub == PartitioningType.Hash ? 2 : 3,
							registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before)).size());
					assertThrows(IllegalArgumentException.class,
							() -> sql(before, after, DialectHolder.oracle11gDialect));
					after.setCompression(false);
					after.setCompressionType(null);
					assertThrows(IllegalArgumentException.class,
							() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
				}
	}

	@Test
	void compressedLocalIndexesInheritRootDuringParentSplitAndMerge() throws Exception {
		for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List))
			for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.Range, PartitioningType.List))
				for (String mode : List.of("ENABLED", "ADVANCED LOW", "ADVANCED HIGH")) {
					Table before = source(root, sub);
					for (Partitioning info : List.of(before.getPartitioning(),
							before.getIndexes().get("IX").getPartitioning())) {
						if (root == PartitioningType.List)
							info.getPartitions().get(1).setHighValue("150,200");
						if (sub != PartitioningType.Hash)
							for (Partition parent : info.getPartitions())
								parent.getSubPartitions().get(1)
										.setHighValue(sub == PartitioningType.List ? "DEFAULT" : "MAXVALUE");
					}
					compressIndex(before, mode);
					Table after = splitParent(before);
					String snapshot = before.asXml();
					var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
					var split = registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after));
					assertEquals(sub == PartitioningType.Hash ? 2 : 3, split.size());
					assertEquals(split.size(),
							registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(after.diff(before)).size());
					assertEquals(snapshot, before.asXml(), "Projection must not mutate compression metadata");
					if (sub != PartitioningType.Hash) {
						for (Partitioning info : List.of(before.getPartitioning(),
								before.getIndexes().get("IX").getPartitioning()))
							for (Partition parent : info.getPartitions())
								parent.getSubPartitions().get(1).setHighValue("20");
						Table finite = splitParent(before);
						assertEquals(3, registry.getSqlFactory(finite, SqlType.ALTER).createDiffSql(before.diff(finite))
								.size());
						assertThrows(IllegalArgumentException.class,
								() -> registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(finite.diff(before)));
					}
				}
	}

	private void compressIndex(Table table, String mode) {
		Index index = table.getIndexes().get("IX");
		index.setCompression(true);
		index.setCompressionType(mode);
		if (mode.equals("ENABLED"))
			index.getSpecifics().put("oracle.index.prefixLength", "1");
		for (Partition parent : index.getPartitioning().getPartitions()) {
			parent.setCompression(true);
			parent.setCompressionType(mode);
			for (SubPartition child : parent.getSubPartitions()) {
				child.setCompression(true);
				child.setCompressionType(mode);
			}
		}
	}

	@Test
	void compressedLocalParentMaintenanceRejectsModeChangesAndOldVersions() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash);
		compressIndex(before, "ADVANCED LOW");
		Table after = splitParent(before);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle11gR2Dialect));
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(1).getSubPartitions().get(0)
				.setCompressionType("ADVANCED HIGH");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		Table high = source(PartitioningType.Range, PartitioningType.Hash);
		compressIndex(high, "ADVANCED HIGH");
		Table highSplit = splitParent(high);
		assertThrows(IllegalArgumentException.class, () -> sql(high, highSplit, DialectHolder.oracle12cDialect));
	}

	@Test
	void compressedLocalChildSplitMergeAndMoveRetainModes() {
		for (String mode : List.of("ENABLED", "ADVANCED LOW", "ADVANCED HIGH"))
			for (PartitioningType sub : List.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash)) {
				Table before = source(PartitioningType.Range, sub);
				compressIndex(before, mode);
				Table moved = before.clone();
				moved.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("OTHER");
				assertTrue(sql(before, moved, DialectHolder.oracle26aiDialect).contains("MOVE SUBPARTITION"));
				if (sub == PartitioningType.Hash)
					continue;
				Table split = splitChild(before, sub == PartitioningType.List);
				assertTrue(sql(before, split, DialectHolder.oracle26aiDialect).contains("SPLIT SUBPARTITION"));
				assertTrue(sql(split, before, DialectHolder.oracle26aiDialect).contains("MERGE SUBPARTITIONS"));
			}
	}

	@Test
	void parentSplitCannotDiscardChildRoutingAttributesOrOtherParents() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = splitParent(before);
		after.getPartitioning().getPartitions().get(1).getSubPartitions().remove(1);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table space = splitParent(before);
		space.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, space, DialectHolder.oracle26aiDialect));
		Table unrelated = splitParent(before);
		unrelated.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, unrelated, DialectHolder.oracle26aiDialect));
		Table wrongBoundary = splitParent(before);
		wrongBoundary.getIndexes().get("IX").getPartitioning().getPartitions().get(1).setHighValue("999");
		assertThrows(IllegalArgumentException.class, () -> sql(before, wrongBoundary, DialectHolder.oracle26aiDialect));
	}

	@Test
	void parentSplitRejectsUnsupportedCompressionAndNameCollisions() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = splitParent(before);
		after.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setName("S11");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		before.getIndexes().get("IX").setCompression(true);
		before.getIndexes().get("IX").getPartitioning().getPartitions().get(1).setCompressionType("DISABLED");
		Table compressed = splitParent(before);
		assertThrows(IllegalArgumentException.class, () -> sql(before, compressed, DialectHolder.oracle26aiDialect));
	}

	@Test
	void childRangeSplitAndMergeCoordinateNamedLocalSegments() {
		Table before = source(PartitioningType.Range, PartitioningType.Range);
		Table split = splitChild(before, false);
		assertEquals(
				"ALTER TABLE APP.T SPLIT SUBPARTITION S12 AT (15) INTO (SUBPARTITION SL, SUBPARTITION SR) UPDATE INDEXES (IX (SUBPARTITION IL, SUBPARTITION IR))",
				sql(before, split, DialectHolder.oracle11gDialect));
		assertEquals(
				"ALTER TABLE APP.T MERGE SUBPARTITIONS SL, SR INTO SUBPARTITION S12 UPDATE INDEXES (IX (SUBPARTITION S12))",
				sql(split, before, DialectHolder.oracle11gR2Dialect));
		assertEquals(2, before.getPartitioning().getPartitions().get(0).getSubPartitions().size());
	}

	private Table splitChild(Table before, boolean list) {
		if (list)
			for (Partitioning info : List.of(before.getPartitioning(), before.getIndexes().get("IX").getPartitioning()))
				info.getPartitions().get(0).getSubPartitions().get(1).setHighValue("20,30");
		Table after = before.clone();
		for (Partitioning info : List.of(after.getPartitioning(), after.getIndexes().get("IX").getPartitioning())) {
			var children = info.getPartitions().get(0).getSubPartitions();
			SubPartition original = children.remove(1);
			boolean index = info.getIndex() != null;
			children.add(original.clone().setName(index ? "IL" : "SL").setHighValue(list ? "20" : "15"));
			children.add(original.clone().setName(index ? "IR" : "SR").setHighValue(list ? "30" : "20"));
		}
		return after;
	}

	@Test
	void childListSplitAndMergeUseSetValidation() {
		Table before = source(PartitioningType.Range, PartitioningType.List), after = splitChild(before, true);
		assertTrue(sql(before, after, DialectHolder.oracle10gDialect).contains("SPLIT SUBPARTITION S12 VALUES (20)"));
		assertTrue(sql(after, before, DialectHolder.oracle10gDialect).contains("MERGE SUBPARTITIONS"));
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(2).setHighValue("40");
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(2)
				.setHighValue("40");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
	}

	@Test
	void childMoveWorksForEverySupportedCompositeFamilyWithoutChangingIndexes() {
		for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash))
			for (PartitioningType sub : List.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash)) {
				Table before = source(root, sub), after = before.clone();
				after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0)
						.setTableSpaceName("Data Space");
				assertEquals("ALTER TABLE APP.T MOVE SUBPARTITION S11 TABLESPACE \"Data Space\" UPDATE INDEXES",
						sql(before, after, DialectHolder.oracle26aiDialect));
			}
	}

	@Test
	void childSplitNeedsMatchingBoundariesUniqueNamesAndUnchangedOtherParents() {
		Table before = source(PartitioningType.Range, PartitioningType.Range), after = splitChild(before, false);
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(1)
				.setHighValue("16");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table duplicate = splitChild(before, false);
		duplicate.getPartitioning().getPartitions().get(0).getSubPartitions().get(1).setName("S21");
		assertThrows(IllegalArgumentException.class, () -> sql(before, duplicate, DialectHolder.oracle26aiDialect));
		Table concurrent = splitChild(before, false);
		concurrent.getPartitioning().getPartitions().get(1).getSubPartitions().get(0).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, concurrent, DialectHolder.oracle26aiDialect));
	}

	@Test
	void childSplitAndMoveHonorVersionOnlineAndSpecialIndexBoundaries() {
		Table before = source(PartitioningType.Range, PartitioningType.Range), after = splitChild(before, false);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle10gDialect));
		before.getIndexes().get("IX").setIndexType(IndexType.Domain);
		after.getIndexes().get("IX").setIndexType(IndexType.Domain);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table move = source(PartitioningType.Range, PartitioningType.Hash), moved = move.clone();
		moved.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
		var online = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		online.getTableOptions().setOnlineIndex(true);
		assertThrows(IllegalArgumentException.class,
				() -> online.getSqlFactory(moved, SqlType.ALTER).createDiffSql(move.diff(moved)));
		assertThrows(IllegalArgumentException.class, () -> sql(move, moved, DialectHolder.defaultDialect));
	}

	@Test
	void childMoveRejectsUnusableAffectedIndexes() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash);
		before.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(0).getSpecifics()
				.put("oracle.index.status", "UNUSABLE");
		Table after = before.clone();
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
	}

	@Test
	void childMovesRejectConcurrentAttributesCompressionAndIndexChanges() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = before.clone();
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(1).setTableSpaceName("DATA");
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(1).setRemarks("changed");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after, DialectHolder.oracle26aiDialect));
		Table changed = before.clone();
		changed.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
		changed.getIndexes().get("GIX").setRemarks("changed");
		assertThrows(IllegalArgumentException.class, () -> sql(before, changed, DialectHolder.oracle26aiDialect));
		before.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setCompression(true)
				.setCompressionType("UNSUPPORTED");
		Table compressed = before.clone();
		compressed.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
		assertThrows(IllegalArgumentException.class, () -> sql(before, compressed, DialectHolder.oracle26aiDialect));
	}

	@Test
	void childMoveCanCompressPreserveCompressionAndDecompress() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), compressed = before.clone();
		SubPartition child = compressed.getPartitioning().getPartitions().get(0).getSubPartitions().get(0);
		child.setCompression(true);
		child.getSpecifics().put("COMPRESS_FOR", "BASIC");
		assertThrows(IllegalArgumentException.class, () -> sql(before, compressed, DialectHolder.oracle10gDialect));
		assertTrue(sql(before, compressed, DialectHolder.oracle11gR2Dialect).endsWith("COMPRESS UPDATE INDEXES"));
		Table moved = compressed.clone();
		moved.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
		assertTrue(sql(compressed, moved, DialectHolder.oracle11gR2Dialect)
				.contains("TABLESPACE DATA COMPRESS UPDATE INDEXES"));
		assertTrue(sql(compressed, before, DialectHolder.oracle11gR2Dialect).endsWith("NOCOMPRESS UPDATE INDEXES"));
		child.getSpecifics().put("COMPRESS_FOR", "OLTP");
		assertTrue(sql(before, compressed, DialectHolder.oracle11gR2Dialect).contains("COMPRESS FOR OLTP"));
		assertThrows(IllegalArgumentException.class, () -> sql(before, compressed, DialectHolder.oracle10gDialect));
		child.setCompressionType("BASIC");
		assertThrows(IllegalArgumentException.class, () -> sql(before, compressed, DialectHolder.oracle26aiDialect));
	}

	@Test
	void compressedChildrenSplitAndMergePreserveExplicitMode() {
		for (boolean list : List.of(false, true)) {
			Table before = source(PartitioningType.Range, list ? PartitioningType.List : PartitioningType.Range);
			SubPartition child = before.getPartitioning().getPartitions().get(0).getSubPartitions().get(1);
			child.setCompression(true);
			child.getSpecifics().put("COMPRESS_FOR", "BASIC");
			Table after = splitChild(before, list);
			String split = sql(before, after, DialectHolder.oracle11gR2Dialect);
			assertTrue(split.contains("SUBPARTITION SL COMPRESS"));
			assertTrue(split.contains("SUBPARTITION SR COMPRESS"));
			assertTrue(sql(after, before, DialectHolder.oracle11gR2Dialect).contains("SUBPARTITION S12 COMPRESS"));
			child.getSpecifics().put("COMPRESS_FOR", "OLTP");
			child.getSpecifics().put("PCT_FREE", "0");
			Table incompatible = splitChild(before, list);
			assertThrows(IllegalArgumentException.class,
					() -> sql(before, incompatible, DialectHolder.oracle11gR2Dialect));
		}
	}

	@Test
	void childSplitMergeUnderCompressedDefaultsPreservesExplicitModes() {
		for (boolean list : List.of(false, true))
			for (boolean tableDefault : List.of(false, true))
				for (String mode : List.of("BASIC", "ADVANCED"))
					for (boolean compressedChild : List.of(false, true)) {
						Table before = source(PartitioningType.Range,
								list ? PartitioningType.List : PartitioningType.Range);
						if (tableDefault) {
							before.setCompression(true);
							before.setCompressionType(mode);
						}
						Partition parent = before.getPartitioning().getPartitions().get(0);
						tableMode(parent, mode);
						parent.getSpecifics().put("PCT_FREE", "BASIC".equals(mode) ? "0" : "10");
						for (SubPartition child : parent.getSubPartitions()) {
							tableMode(child, compressedChild ? mode : null);
							child.getSpecifics().put("PCT_FREE", parent.getSpecifics().get("PCT_FREE"));
						}
						Table after = splitChild(before, list);
						String clause = !compressedChild ? "NOCOMPRESS"
								: "BASIC".equals(mode) ? "COMPRESS" : "COMPRESS FOR OLTP";
						assertTrue(sql(before, after, DialectHolder.oracle11gR2Dialect)
								.contains("SUBPARTITION SL " + clause));
						assertTrue(sql(after, before, DialectHolder.oracle11gR2Dialect)
								.contains("SUBPARTITION S12 " + clause));
						if (compressedChild)
							assertThrows(IllegalArgumentException.class,
									() -> sql(before, after, DialectHolder.oracle10gDialect));
						if (!compressedChild) {
							Table incompatible = before.clone();
							incompatible.getPartitioning().getPartitions().get(0).getSubPartitions().get(1)
									.getSpecifics().put("PCT_FREE", "37");
							assertThrows(IllegalArgumentException.class, () -> sql(incompatible,
									splitChild(incompatible, list), DialectHolder.oracle26aiDialect));
						}
						Table changed = after.clone();
						changed.getPartitioning().getPartitions().get(0).setCompression(false);
						assertThrows(IllegalArgumentException.class,
								() -> sql(before, changed, DialectHolder.oracle26aiDialect));
					}
	}

	@Test
	void multipleParentsCanMoveChildrenWithoutMutatingSource() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = before.clone();
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
		after.getPartitioning().getPartitions().get(1).getSubPartitions().get(1).setTableSpaceName("DATA");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		assertEquals(2, registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).size());
		assertNull(before.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).getTableSpaceName());
		after.getPartitioning().getPartitions().get(1).getSubPartitions().get(1).setRemarks("invalid");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}

	@Test
	void compressedMoveRejectsUnrepresentablePctFreeAndAcceptsExplicitOracleDefault() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash);
		before.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).getSpecifics().put("PCT_FREE", "10");
		Table after = before.clone();
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setCompression(true);
		var factory = (OracleAlterTableFactory) DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.getSqlFactory(after, SqlType.ALTER);
		factory.setOnlinePartitionMaintenance(true);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(before.diff(after)));
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).getSpecifics().put("PCT_FREE", "0");
		var operations = factory.createDiffSql(before.diff(after));
		assertEquals(1, operations.size());
		assertTrue(operations.get(0).getSqlText().endsWith("COMPRESS UPDATE INDEXES ONLINE"));
		Table decompressed = after.clone();
		decompressed.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setCompression(false);
		assertTrue(factory.createDiffSql(after.diff(decompressed)).get(0).getSqlText()
				.endsWith("NOCOMPRESS UPDATE INDEXES ONLINE"));
		decompressed.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).getSpecifics().put("PCT_FREE",
				"10");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(after.diff(decompressed)));
	}

	@Test
	void onlinePartitionMaintenanceHasDedicatedOptInAndVersionGuards() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = before.clone();
		for (SubPartition child : after.getPartitioning().getPartitions().get(0).getSubPartitions())
			child.setTableSpaceName("DATA");
		var registry = DialectHolder.oracle18cDialect.createSqlFactoryRegistry();
		var factory = (OracleAlterTableFactory) registry.getSqlFactory(after, SqlType.ALTER);
		assertFalse(factory.isOnlinePartitionMaintenance());
		factory.setOnlinePartitionMaintenance(true);
		var operations = factory.createDiffSql(before.diff(after));
		assertEquals(2, operations.size());
		assertTrue(operations.stream().allMatch(operation -> operation.getSqlText().endsWith("UPDATE INDEXES ONLINE")));
		var old = (OracleAlterTableFactory) DialectHolder.oracle12cDialect.createSqlFactoryRegistry()
				.getSqlFactory(after, SqlType.ALTER);
		old.setOnlinePartitionMaintenance(true);
		assertThrows(IllegalArgumentException.class, () -> old.createDiffSql(before.diff(after)));
		Table split = splitParent(before);
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(before.diff(split)));
		before.setSchemaName("SYS");
		after.setSchemaName("SYS");
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(before.diff(after)));
	}

	@Test
	void allChildrenOfOneParentCanMoveWithDifferentDestinations() {
		for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash))
			for (PartitioningType sub : List.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash)) {
				Table before = source(root, sub), after = before.clone(), snapshot = before.clone();
				after.getPartitioning().getPartitions().get(0).getSubPartitions().get(0).setTableSpaceName("DATA");
				after.getPartitioning().getPartitions().get(0).getSubPartitions().get(1)
						.setTableSpaceName("Other Space");
				var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after,
						SqlType.ALTER);
				factory.getOptions().setDecorateSchemaName(true);
				var operations = factory.createDiffSql(before.diff(after));
				assertEquals(
						List.of("ALTER TABLE APP.T MOVE SUBPARTITION S11 TABLESPACE DATA UPDATE INDEXES",
								"ALTER TABLE APP.T MOVE SUBPARTITION S12 TABLESPACE \"Other Space\" UPDATE INDEXES"),
						operations.stream().map(operation -> operation.getSqlText().replaceAll("\\s+", " ").trim())
								.toList());
				assertEquals(snapshot, before);
			}
	}

	@Test
	void groupedChildMoveValidatesLastIndexAndParentDefaultsBeforeReturningSql() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), after = before.clone();
		for (SubPartition child : after.getPartitioning().getPartitions().get(0).getSubPartitions())
			child.setTableSpaceName("DATA");
		before.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(1).getSpecifics()
				.put("oracle.index.status", "UNUSABLE");
		after.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(1).getSpecifics()
				.put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		Table defaults = source(PartitioningType.Range, PartitioningType.Hash), changed = defaults.clone();
		changed.getPartitioning().getPartitions().get(0).setTableSpaceName("DATA");
		for (SubPartition child : changed.getPartitioning().getPartitions().get(0).getSubPartitions())
			child.setTableSpaceName("DATA");
		assertThrows(IllegalArgumentException.class, () -> sql(defaults, changed, DialectHolder.oracle26aiDialect));
	}

	@Test
	void groupedMoveWithoutLocalIndexesHonorsVersionAndDoesNotRequireAddOrDrop() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash);
		before.getIndexes().remove("IX");
		for (SubPartition child : before.getPartitioning().getPartitions().get(0).getSubPartitions())
			child.setTableSpaceName("USERS");
		Table after = before.clone();
		for (SubPartition child : after.getPartitioning().getPartitions().get(0).getSubPartitions())
			child.setTableSpaceName("DATA");
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setAllowAddPartition(false);
		registry.getTableOptions().setAllowDropPartition(false);
		assertEquals(2, registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)).size());
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
		after.getPartitioning().getPartitions().get(0).getSubPartitions().get(1).setTableSpaceName(" ");
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}

	@Test
	void tableCompressedAddUsesExplicitNewSegmentModes() {
		for (String mode : List.of("BASIC", "ADVANCED"))
			for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List))
				for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.Range,
						PartitioningType.List)) {
					Table before = source(root, sub);
					before.setCompression(true);
					before.setCompressionType(mode);
					for (Partition part : before.getPartitioning().getPartitions()) {
						tableMode(part, mode);
						for (SubPartition child : part.getSubPartitions())
							tableMode(child, mode);
					}
					compressIndex(before, "ENABLED");
					Table parent = appendParent(before);
					assertTrue(sql(before, parent, DialectHolder.oracle26aiDialect)
							.contains(mode.equals("BASIC") ? " COMPRESS" : " COMPRESS FOR OLTP"));
					assertThrows(IllegalArgumentException.class,
							() -> sql(before, parent, DialectHolder.oracle11gDialect));
					tableMode(parent.getPartitioning().getPartitions().get(2), null);
					for (SubPartition child : parent.getPartitioning().getPartitions().get(2).getSubPartitions())
						tableMode(child, null);
					assertTrue(sql(before, parent, DialectHolder.oracle26aiDialect).contains(" NOCOMPRESS"));
					if (root == PartitioningType.List && sub != PartitioningType.List)
						continue;
					Table child = append(before, sub == PartitioningType.Hash ? null : "30");
					assertTrue(sql(before, child, DialectHolder.oracle26aiDialect).contains(" NOCOMPRESS"));
					tableMode(child.getPartitioning().getPartitions().get(0).getSubPartitions().get(2), mode);
					assertTrue(sql(before, child, DialectHolder.oracle26aiDialect)
							.contains(mode.equals("BASIC") ? " COMPRESS" : " COMPRESS FOR OLTP"));
					// A NONE parent default can still fall back to table compression.
					tableMode(before.getPartitioning().getPartitions().get(0), null);
					Table fallback = append(before, sub == PartitioningType.Hash ? null : "30");
					assertTrue(sql(before, fallback, DialectHolder.oracle26aiDialect).contains(" NOCOMPRESS"));
				}
	}

	private void tableMode(AbstractPartition<?> part, String mode) {
		part.setCompression(mode != null);
		part.setCompressionType(mode);
		if (mode == null)
			part.getSpecifics().remove("COMPRESS_FOR");
		else
			part.getSpecifics().put("COMPRESS_FOR", mode);
	}

	@Test
	void compressedAddRejectsConflictingModesAndPhysicalOverrides() {
		Table before = source(PartitioningType.Range, PartitioningType.Hash), parent = appendParent(before);
		Partition added = parent.getPartitioning().getPartitions().get(2);
		tableMode(added, "ADVANCED");
		added.getSpecifics().put("COMPRESS_FOR", "BASIC");
		assertThrows(IllegalArgumentException.class, () -> sql(before, parent, DialectHolder.oracle26aiDialect));
		tableMode(added, "BASIC");
		added.getSpecifics().put("PCT_FREE", "0");
		assertTrue(sql(before, parent, DialectHolder.oracle26aiDialect).contains(" COMPRESS"));
		added.getSpecifics().put("PCT_FREE", "1");
		assertThrows(IllegalArgumentException.class, () -> sql(before, parent, DialectHolder.oracle26aiDialect));
	}

	@Test
	void basicAddAcceptsOnlyCompressionDerivedPctFree() {
		for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List))
			for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.Range, PartitioningType.List)) {
				Table before = source(root, sub), parent = appendParent(before);
				Partition added = parent.getPartitioning().getPartitions().get(2);
				tableMode(added, "BASIC");
				added.getSpecifics().put("PCT_FREE", "0");
				for (SubPartition child : added.getSubPartitions()) {
					tableMode(child, "BASIC");
					child.getSpecifics().put("PCT_FREE", "0");
				}
				String sql = sql(before, parent, DialectHolder.oracle11gR2Dialect);
				assertTrue(sql.contains(" COMPRESS"));
				assertFalse(sql.contains("PCTFREE"));
				added.getSubPartitions().get(0).getSpecifics().put("PCT_FREE", "1");
				assertThrows(IllegalArgumentException.class,
						() -> sql(before, parent, DialectHolder.oracle26aiDialect));
				if (root == PartitioningType.List && sub != PartitioningType.List)
					continue;
				Table after = append(before, sub == PartitioningType.Hash ? null : "30");
				SubPartition child = after.getPartitioning().getPartitions().get(0).getSubPartitions().get(2);
				tableMode(child, "BASIC");
				child.getSpecifics().put("PCT_FREE", "0");
				assertTrue(sql(before, after, DialectHolder.oracle11gR2Dialect).contains(" COMPRESS"));
				for (String mode : List.of("ADVANCED", "OFF")) {
					tableMode(child, "OFF".equals(mode) ? null : mode);
					assertThrows(IllegalArgumentException.class,
							() -> sql(before, after, DialectHolder.oracle26aiDialect));
				}
			}
	}

	@Test
	void compressedLocalAddParentsAndChildrenInheritRoot() {
		for (String mode : List.of("ENABLED", "ADVANCED LOW", "ADVANCED HIGH"))
			for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List))
				for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.Range,
						PartitioningType.List)) {
					Table before = source(root, sub);
					compressIndex(before, mode);
					Table parent = appendParent(before);
					assertTrue(sql(before, parent, DialectHolder.oracle26aiDialect).contains("ADD PARTITION"));
					if (!mode.equals("ENABLED"))
						assertThrows(IllegalArgumentException.class,
								() -> sql(before, parent, mode.equals("ADVANCED LOW") ? DialectHolder.oracle11gR2Dialect
										: DialectHolder.oracle12cDialect));
					if (root == PartitioningType.List && sub != PartitioningType.List)
						continue;
					Table child = append(before, sub == PartitioningType.Hash ? null : "30");
					compressIndex(child, mode);
					assertTrue(sql(before, child, DialectHolder.oracle26aiDialect).contains("ADD SUBPARTITION"));
					child.getIndexes().get("IX").getPartitioning().getPartitions().get(0).getSubPartitions().get(2)
							.setCompressionType("DISABLED").setCompression(false);
					assertThrows(IllegalArgumentException.class,
							() -> sql(before, child, DialectHolder.oracle26aiDialect));
				}
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
