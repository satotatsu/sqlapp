/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleLocalPartitionSplitTest {
	@Test
	void multicolumnSplitAcceptsTrailingMaxvalueButRejectsLeadingMarker() {
		Table before = source();
		before.getColumns().add("REGION");
		for (Partitioning info : java.util.List.of(before.getPartitioning(),
				before.getIndexes().get(0).getPartitioning())) {
			info.getPartitioningColumns().add("REGION");
			info.getPartitions().get(0).setHighValue("MAXVALUE, 0");
		}
		Table after = split(before);
		for (Partitioning info : java.util.List.of(after.getPartitioning(),
				after.getIndexes().get(0).getPartitioning()))
			info.getPartitions().get(0).setHighValue("100, MAXVALUE");
		assertTrue(sql(before, after).contains("AT (100, MAXVALUE)"));
		assertTrue(sql(after, before).contains("MERGE PARTITIONS"));
		for (Partitioning info : java.util.List.of(after.getPartitioning(),
				after.getIndexes().get(0).getPartitioning()))
			info.getPartitions().get(0).setHighValue(" /* bound */ MAXVALUE, MAXVALUE");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}

	private Table source() {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range);
		info.getPartitioningColumns().add("ID");
		info.getPartitions().add(new Partition("P_MAX").setHighValue("MAXVALUE"));
		table.setPartitioning(info);
		Index index = new Index("IX");
		table.getIndexes().add(index);
		index.getColumns().add("ID");
		index.getSpecifics().put("oracle.index.locality", "LOCAL");
		index.setPartitioning(info.clone());
		index.getPartitioning().getPartitions().get(0).setName("IP_MAX");
		index.getPartitioning().getPartitions().get(0).getSpecifics().put("oracle.index.status", "USABLE");
		return table;
	}

	private Table split(Table before) {
		Table after = before.clone();
		for (Partitioning info : java.util.List.of(after.getPartitioning(),
				after.getIndexes().get(0).getPartitioning())) {
			Partition original = info.getPartitions().get(0).clone();
			info.getPartitions().clear();
			Partition left = original.clone();
			left.setName(original.getName() + "_L");
			left.setHighValue("100");
			left.setTableSpaceName("TS_LEFT");
			Partition right = original.clone();
			right.setName(original.getName() + "_R");
			right.setTableSpaceName("TS_RIGHT");
			info.getPartitions().add(left);
			info.getPartitions().add(right);
		}
		return after;
	}

	private String sql(Table before, Table after) {
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		var operations = factory.createDiffSql(before.diff(after));
		assertEquals(1, operations.size());
		return operations.get(0).getSqlText().replaceAll("\\s+", " ").replace("( ", "(").trim();
	}

	@Test
	void splitPreservesRowsAndSpecifiesLocalSegmentStorage() {
		Table before = source();
		Table after = split(before);
		assertEquals(
				"ALTER TABLE APP.T SPLIT PARTITION P_MAX AT (100) INTO (PARTITION P_MAX_L TABLESPACE TS_LEFT, PARTITION P_MAX_R TABLESPACE TS_RIGHT) UPDATE INDEXES (IX (PARTITION IP_MAX_L TABLESPACE TS_LEFT, PARTITION IP_MAX_R TABLESPACE TS_RIGHT))",
				sql(before, after));
		assertEquals(1, before.getPartitioning().getPartitions().size());
		assertEquals(2, after.getIndexes().get(0).getPartitioning().getPartitions().size());
	}

	@Test
	void changedUpperBoundaryIsRejected() {
		Table before = source(), after = split(before);
		after.getPartitioning().getPartitions().get(1).setHighValue("200");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}

	@Test
	void mergeMaintainsRowsAndExplicitLocalSegments() {
		Table merged = source(), before = split(merged);
		String statement = sql(before, merged);
		assertEquals(
				"ALTER TABLE APP.T MERGE PARTITIONS P_MAX_L, P_MAX_R INTO PARTITION P_MAX UPDATE INDEXES (IX (PARTITION IP_MAX))",
				statement);
		assertFalse(statement.contains("DROP"));
	}

	@Test
	void mergeCannotDiscardAnUpperBoundOrChangeOtherAttributes() {
		Table merged = source(), before = split(merged);
		merged.getPartitioning().getPartitions().get(0).setHighValue("200");
		assertThrows(IllegalArgumentException.class, () -> sql(before, merged));
	}

	@Test
	void unaffectedFollowingPartitionsRemainInPlace() {
		Table before = source();
		before.getPartitioning().getPartitions().get(0).setHighValue("200");
		before.getPartitioning().getPartitions().add(new Partition("LAST").setHighValue("MAXVALUE"));
		Partitioning indexes = before.getIndexes().get(0).getPartitioning();
		indexes.getPartitions().get(0).setHighValue("200");
		Partition last = new Partition("ILAST").setHighValue("MAXVALUE");
		last.getSpecifics().put("oracle.index.status", "USABLE");
		indexes.getPartitions().add(last);
		before = before.clone();
		Table after = split(before);
		after.getPartitioning().getPartitions().add(before.getPartitioning().getPartitions().get(1).clone());
		after.getIndexes().get(0).getPartitioning().getPartitions().add(indexes.getPartitions().get(1).clone());
		assertTrue(sql(before, after).contains("SPLIT PARTITION P_MAX"));
	}

	@Test
	void unchangedGlobalIndexesUseTheSameMaintenanceOperation() {
		Table before = source();
		Index global = new Index("GLOBAL_IX");
		before.getIndexes().add(global);
		global.getColumns().add("ID");
		global.getSpecifics().put("oracle.index.locality", "GLOBAL");
		before = before.clone();
		Table after = split(before);
		String statement = sql(before, after);
		assertTrue(statement.contains("UPDATE INDEXES"));
		assertFalse(statement.contains("GLOBAL_IX"));
	}

	@Test
	void localPartitionConfigurationChangesAreRejected() {
		Table before = source(), after = split(before);
		after.getIndexes().get(0).getPartitioning().setPartitioningType(PartitioningType.Hash);
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}

	@Test
	void unusableSegmentsAndSimultaneousColumnChangesAreRejected() {
		Table before = source(), after = split(before);
		after.getIndexes().get(0).getPartitioning().getPartitions().get(0).getSpecifics().put("oracle.index.status",
				"UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
		Table other = split(before);
		other.getColumns().add("EXTRA");
		assertThrows(IllegalArgumentException.class, () -> sql(before, other));
	}

	@Test
	void onlineMaintenanceIsRejected() {
		Table before = source(), after = split(before);
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setOnlineIndex(true);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(before.diff(after)));
	}
}
