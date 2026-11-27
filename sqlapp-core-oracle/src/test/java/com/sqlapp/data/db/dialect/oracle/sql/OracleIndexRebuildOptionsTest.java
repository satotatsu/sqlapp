/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleIndexRebuildOptionsTest {
	private Table table(boolean partitioned, boolean composite) {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID");
		table.getColumns().add("CODE");
		Index index = new Index("IX");
		index.getColumns().add("ID");
		index.getColumns().add("CODE");
		table.getIndexes().add(index);
		index.getSpecifics().put("oracle.index.status", partitioned ? "N/A" : "VALID");
		index.setTableSpaceName("OLD");
		if (partitioned) {
			index.getSpecifics().put("oracle.index.locality", "LOCAL");
			Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range);
			info.getPartitioningColumns().add("ID");
			Partition partition = new Partition("P").setHighValue("100").setTableSpaceName("OLD");
			partition.getSpecifics().put("oracle.index.status", composite ? "N/A" : "USABLE");
			info.getPartitions().add(partition);
			if (composite) {
				info.setSubPartitioningType(PartitioningType.Range);
				info.getSubPartitioningColumns().add("CODE");
				SubPartition sub = new SubPartition("SP").setHighValue("100").setTableSpaceName("OLD");
				sub.getSpecifics().put("oracle.index.status", "USABLE");
				partition.getSubPartitions().add(sub);
			}
			index.setPartitioning(info);
		}
		return table;
	}

	private Index index(Table table) {
		return table.getIndexes().get(0);
	}

	private Partition partition(Table table) {
		return index(table).getPartitioning().getPartitions().get(0);
	}

	private List<String> diff(Table before, Table after, boolean online, Dialect dialect) {
		var registry = dialect.createSqlFactoryRegistry();
		registry.getTableOptions().setOnlineIndex(online);
		var factory = registry.getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText().replaceAll("\\s+", " "))
				.toList();
	}

	private List<String> diff(Table before, Table after, boolean online) {
		return diff(before, after, online, DialectHolder.oracle26aiDialect);
	}

	@Test
	void moveAndCompressUseOneOnlineRebuildAfterXmlRoundTrip() throws Exception {
		Table before = table(false, false), after = before.clone();
		index(after).setTableSpaceName("New Space").setCompression(true).setCompressionType("ADVANCED HIGH");
		Table restored = SchemaUtils.readXml(new java.io.StringReader(after.asXml()));
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD TABLESPACE \"New Space\" COMPRESS ADVANCED HIGH ONLINE"),
				diff(before, restored, true));
	}

	@Test
	void prefixCompressionCanBeEnabledAndDisabled() {
		Table before = table(false, false), after = before.clone();
		index(after).setCompression(true).getSpecifics().put("oracle.index.prefixLength", "1");
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD COMPRESS 1"), diff(before, after, false));
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD NOCOMPRESS"), diff(after, before, false));
	}

	@Test
	void physicalRebuildAndRecoveryAreNotDuplicated() {
		Table before = table(false, false), after = before.clone();
		index(before).getSpecifics().put("oracle.index.status", "UNUSABLE");
		index(after).setTableSpaceName("NEW");
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD TABLESPACE NEW"), diff(before, after, false));
	}

	@Test
	void directRebuildUsesTheExistingOnlineOption() {
		var registry = DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setOnlineIndex(true);
		assertTrue(registry.createSql(index(table(false, false)), SqlType.REBUILD).get(0).getSqlText()
				.endsWith("REBUILD ONLINE"));
	}

	@Test
	void partitionMoveAndCompressionUseIndividualRebuild() {
		Table before = table(true, false), after = before.clone();
		partition(after).setTableSpaceName("NEW").setCompression(true).setCompressionType("ADVANCED LOW");
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD PARTITION P TABLESPACE NEW COMPRESS ADVANCED LOW ONLINE"),
				diff(before, after, true));
	}

	@Test
	void subpartitionCanMoveOnlineWithoutIllegalCompressionClause() {
		Table before = table(true, true), after = before.clone();
		partition(after).getSubPartitions().get(0).setTableSpaceName("NEW");
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD SUBPARTITION SP TABLESPACE NEW ONLINE"),
				diff(before, after, true));
		assertThrows(IllegalArgumentException.class, () -> diff(before, after, true, DialectHolder.oracle10gDialect));
	}

	@Test
	void compositeCompressionChangesParentThenRebuildsEveryChild() {
		Table before = table(true, true);
		index(before).setCompression(true);
		partition(before).setCompression(true);
		partition(before).getSubPartitions().get(0).setCompression(true);
		Table after = before.clone();
		partition(after).setCompression(false).setCompressionType("DISABLED").getSpecifics()
				.put("oracle.index.compression", "DISABLED");
		partition(after).getSubPartitions().get(0).setCompression(false).setCompressionType("DISABLED").getSpecifics()
				.put("oracle.index.compression", "DISABLED");
		assertEquals(List.of("ALTER INDEX APP.IX MODIFY PARTITION P NOCOMPRESS",
				"ALTER INDEX APP.IX REBUILD SUBPARTITION SP ONLINE"), diff(before, after, true));
	}

	@Test
	void hashSegmentOnlineAndIndividualSubpartitionCompressionAreRejected() {
		Table before = table(true, true);
		index(before).getPartitioning().setSubPartitioningType(PartitioningType.Hash);
		Table after = before.clone();
		partition(after).getSubPartitions().get(0).setTableSpaceName("NEW");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after, true));
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD SUBPARTITION SP TABLESPACE NEW"), diff(before, after, false));
		partition(after).getSubPartitions().get(0).setCompression(true);
		assertThrows(IllegalArgumentException.class, () -> diff(before, after, false));
	}

	@Test
	void invalidMovesAndCompressionVersionsFailClearly() {
		Table before = table(false, false), after = before.clone();
		index(after).setTableSpaceName(null);
		assertThrows(IllegalArgumentException.class, () -> diff(before, after, false));
		index(after).setTableSpaceName("OLD").setCompression(true).setCompressionType("ADVANCED HIGH");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after, false, DialectHolder.oracle12cDialect));
	}

	@Test
	void onlineRejectsDeferrableConstraintsAndJoinIndexes() {
		Table temporary = table(false, false).setTableType(Table.TableType.Temporary);
		Table unusable = temporary.clone();
		index(unusable).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> diff(temporary, unusable, false));
		Table before = table(false, false);
		UniqueConstraint unique = new UniqueConstraint("UK", false);
		unique.getColumns().add("ID");
		unique.getColumns().add("CODE");
		unique.setIndexName("IX");
		unique.setDeferrability(Deferrability.InitiallyImmediate);
		before.getConstraints().add(unique);
		Table after = before.clone();
		index(after).setTableSpaceName("NEW");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after, true));
		Table join = table(false, false);
		index(join).getSpecifics().put("oracle.index.join", true);
		Table moved = join.clone();
		index(moved).setTableSpaceName("NEW");
		assertThrows(IllegalArgumentException.class, () -> diff(join, moved, true));
	}

	@Test
	void partitionedIndexTablespaceChangesOnlyItsDefault() {
		Table before = table(true, false), after = before.clone();
		index(after).setTableSpaceName("NEW");
		assertEquals(List.of("ALTER INDEX APP.IX MODIFY DEFAULT ATTRIBUTES TABLESPACE NEW"),
				diff(before, after, false));
		assertEquals("OLD", partition(after).getTableSpaceName());
	}

	@Test
	void explicitIndexRenameRunsAfterItsRebuild() {
		Index before = index(table(false, false)), after = before.clone().setName("New Index");
		after.setTableSpaceName("NEW");
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		var sql = factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText().replaceAll("\\s+", " "))
				.toList();
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD TABLESPACE NEW", "ALTER INDEX APP.IX RENAME TO \"New Index\""),
				sql);
	}
}
