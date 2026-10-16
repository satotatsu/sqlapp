/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleIndexPartitionLifecycleTest {
	private Table table(boolean composite) {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID");
		Index index = new Index("IX");
		index.getColumns().add("ID");
		table.getIndexes().add(index);
		index.getSpecifics().put("oracle.index.status", "N/A");
		index.getSpecifics().put("oracle.index.locality", "LOCAL");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range);
		info.getPartitioningColumns().add("ID");
		Partition partition = new Partition("Mixed Partition").setHighValue("100");
		partition.getSpecifics().put("oracle.index.status", composite ? "N/A" : "USABLE");
		info.getPartitions().add(partition);
		if (composite) {
			info.setSubPartitioningType(PartitioningType.Hash);
			info.getSubPartitioningColumns().add("ID");
			SubPartition sub = new SubPartition("Mixed Subpartition");
			sub.getSpecifics().put("oracle.index.status", "USABLE");
			partition.getSubPartitions().add(sub);
		}
		index.setPartitioning(info);
		return table;
	}

	private AbstractPartition<?> segment(Table table, boolean composite) {
		Partition partition = table.getIndexes().get(0).getPartitioning().getPartitions().get(0);
		return composite ? partition.getSubPartitions().get(0) : partition;
	}

	private List<String> diff(Table before, Table after) {
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText().replaceAll("\\s+", " "))
				.toList();
	}

	@Test
	void partitionAndSubpartitionTransitionsUsePhysicalSegments() throws Exception {
		for (boolean composite : new boolean[] { false, true }) {
			Table before = table(composite), after = before.clone();
			segment(after, composite).getSpecifics().put("oracle.index.status", "UNUSABLE");
			Table restored = SchemaUtils.readXml(new java.io.StringReader(after.asXml()));
			String name = composite ? "SUBPARTITION \"Mixed Subpartition\"" : "PARTITION \"Mixed Partition\"";
			assertEquals(List.of("ALTER INDEX APP.IX MODIFY " + name + " UNUSABLE"), diff(before, restored));
			assertEquals(List.of("ALTER INDEX APP.IX REBUILD " + name), diff(after, before));
		}
	}

	@Test
	void globalPartitionUsesSameStatePath() {
		Table before = table(false);
		before.getIndexes().get(0).getSpecifics().put("oracle.index.locality", "GLOBAL");
		Table after = before.clone();
		segment(after, false).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertEquals(List.of("ALTER INDEX APP.IX MODIFY PARTITION \"Mixed Partition\" UNUSABLE"), diff(before, after));
	}

	@Test
	void structuralChangesCannotAccompanyStateChanges() {
		Table before = table(false), after = before.clone();
		segment(after, false).getSpecifics().put("oracle.index.status", "UNUSABLE");
		segment(after, false).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
	}

	@Test
	void compositeParentStateAndMissingTargetStateAreRejected() {
		Table before = table(true), after = before.clone();
		segment(after, false).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		Table removed = before.clone();
		segment(removed, true).getSpecifics().remove("oracle.index.status");
		assertThrows(IllegalArgumentException.class, () -> diff(before, removed));
	}

	@Test
	void invalidStatusAndTemporaryRebuildAreRejected() {
		Table before = table(false), after = before.clone();
		segment(after, false).getSpecifics().put("oracle.index.status", "VALID");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		before.setTableType(Table.TableType.Temporary);
		after.setTableType(Table.TableType.Temporary);
		segment(before, false).getSpecifics().put("oracle.index.status", "UNUSABLE");
		segment(after, false).getSpecifics().put("oracle.index.status", "USABLE");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
	}

	@Test
	void unusableSegmentsSurviveCreateWithoutMarkingTheCompositeParent() throws Exception {
		for (boolean composite : new boolean[] { false, true }) {
			Table table = table(composite);
			segment(table, composite).getSpecifics().put("oracle.index.status", "UNUSABLE");
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			String sql = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
					.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText()
					.replaceAll("\\s+", " ");
			assertTrue(
					sql.contains((composite ? "SUBPARTITION \"Mixed Subpartition\"" : "PARTITION \"Mixed Partition\"")
							+ " UNUSABLE"),
					sql);
			assertFalse(sql.contains("N/A"), sql);
		}
	}
}
