/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OraclePartitionRecreationTest {
	private Table table(PartitioningType root, PartitioningType sub) {
		Table table = new Table("T"); table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		Partitioning info = new Partitioning().setPartitioningType(root).setSubPartitioningType(sub);
		info.getPartitioningColumns().add("ID"); if (sub != null) info.getSubPartitioningColumns().add("ID");
		info.getPartitions().add(new Partition("P1").setHighValue(root == PartitioningType.List ? "1, 2" : "100"));
		table.setPartitioning(info); return table;
	}
	private String sql(Table table, Dialect dialect) {
		return dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+", " ").trim();
	}
	@Test void listCreateUsesOracleValuesWithoutInOnSupportedDialects() {
		for (Dialect dialect : List.of(DialectHolder.oracle10gDialect, DialectHolder.oracle12cDialect, DialectHolder.oracle26aiDialect)) {
			String sql = sql(table(PartitioningType.List, null), dialect);
			assertTrue(sql.contains("VALUES (1, 2)"), sql); assertFalse(sql.contains("VALUES IN"), sql);
		}
	}
	@Test void explicitListChildrenUseValuesAndKeepNamesAndStorage() {
		Table table = table(PartitioningType.Range, PartitioningType.List);
		table.getPartitioning().getPartitions().get(0).getSubPartitions().add(new SubPartition("child part").setHighValue("1, 2").setTableSpaceName("Data Space"));
		String sql = sql(table, DialectHolder.oracle10gDialect);
		assertTrue(sql.contains("SUBPARTITION \"child part\" VALUES (1, 2) TABLESPACE \"Data Space\""), sql);
		assertFalse(sql.contains("VALUES IN"), sql);
	}
	@Test void missingChildrenUseOracleDefaultsWithoutEmptyParentheses() {
		for (PartitioningType sub : List.of(PartitioningType.Hash, PartitioningType.List, PartitioningType.Range)) {
			String sql = sql(table(PartitioningType.Range, sub), DialectHolder.oracle12cDialect);
			assertFalse(sql.matches(".*\\(\\s*\\).*"), sql);
			assertTrue(sql.contains("SUBPARTITION BY"), sql);
		}
	}
	@Test void hashChildCountIsPreserved() {
		Table table = table(PartitioningType.Range, PartitioningType.Hash); table.getPartitioning().setSubPartitionSize(4);
		String sql = sql(table, DialectHolder.oracle10gDialect);
		assertTrue(sql.contains("SUBPARTITIONS 4"), sql); assertFalse(sql.matches(".*\\(\\s*\\).*"), sql);
	}
	@Test void explicitHashChildrenOverrideTheDefaultCount() {
		Table table = table(PartitioningType.Range, PartitioningType.Hash); table.getPartitioning().setSubPartitionSize(4);
		table.getPartitioning().getPartitions().get(0).getSubPartitions().add(new SubPartition("H1"));
		table.getPartitioning().getPartitions().get(0).getSubPartitions().add(new SubPartition("H2"));
		String sql = sql(table, DialectHolder.oracle10gDialect);
		assertTrue(sql.contains("SUBPARTITIONS 4"), sql); assertTrue(sql.contains("SUBPARTITION H1"), sql); assertTrue(sql.contains("SUBPARTITION H2"), sql);
	}
	@Test void invalidCountsAndNonHashCountsAreRejected() {
		for (int count : new int[] {0, -1}) {
			Table table = table(PartitioningType.Range, PartitioningType.Hash); table.getPartitioning().setSubPartitionSize(count);
			assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle26aiDialect));
		}
		Table list = table(PartitioningType.Range, PartitioningType.List); list.getPartitioning().setSubPartitionSize(4);
		assertThrows(IllegalArgumentException.class, () -> sql(list, DialectHolder.oracle26aiDialect));
	}
	@Test void simpleHashPartitionCountReachesCreateTable() {
		Table table = table(PartitioningType.Hash, null); table.getPartitioning().getPartitions().clear(); table.getPartitioning().setPartitionSize(4);
		assertTrue(sql(table, DialectHolder.oracle10gDialect).contains("PARTITIONS 4"));
		table.getPartitioning().setPartitionSize(0);
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle10gDialect));
	}
	@Test void temporaryPartitioningIsRejectedInsteadOfSilentlyOmitted() {
		Table table = table(PartitioningType.List, null); table.setTableType(Table.TableType.Temporary);
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle26aiDialect));
	}
	@Test void missingKeysPartitionsAndBoundsFailClearly() {
		Table missingKey = table(PartitioningType.Range, null); missingKey.getPartitioning().getPartitioningColumns().clear();
		assertThrows(IllegalArgumentException.class, () -> sql(missingKey, DialectHolder.oracle26aiDialect));
		Table empty = table(PartitioningType.List, null); empty.getPartitioning().getPartitions().clear();
		assertThrows(IllegalArgumentException.class, () -> sql(empty, DialectHolder.oracle26aiDialect));
		Table boundary = table(PartitioningType.Range, null); boundary.getPartitioning().getPartitions().get(0).setHighValue((String) null);
		assertThrows(IllegalArgumentException.class, () -> sql(boundary, DialectHolder.oracle26aiDialect));
	}
	@Test void unsupportedPartitionCompressionCannotDisappear() {
		Table table = table(PartitioningType.Range, null); table.getPartitioning().getPartitions().get(0).setCompression(true);
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle26aiDialect));
	}
	@Test void compositeHashRequiresADialectThatCannotRepresent121() {
		Table table = table(PartitioningType.Hash, PartitioningType.List); table.getPartitioning().getPartitions().get(0).setHighValue((String) null);
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle12cDialect));
		assertTrue(sql(table, DialectHolder.oracle18cDialect).contains("SUBPARTITION BY LIST"));
	}
	@Test void rangeRangeAndListCompositeBoundariesAreElevenG() {
		for (PartitioningType root : List.of(PartitioningType.Range, PartitioningType.List)) {
			Table table = table(root, PartitioningType.Range);
			assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle10gDialect));
			assertTrue(sql(table, DialectHolder.oracle11gDialect).contains("SUBPARTITION BY RANGE"));
		}
	}
	@Test void rangePhysicalAndExtentAttributesArePreserved() {
		Table table = table(PartitioningType.Range, null); var attributes = table.getPartitioning().getPartitions().get(0).getSpecifics();
		attributes.put("PCT_FREE", "15"); attributes.put("INI_TRANS", "3"); attributes.put("LOGGING", "NO");
		attributes.put("INITIAL_EXTENT", "65536"); attributes.put("PCT_INCREASE", "0"); attributes.put("BUFFER_POOL", "KEEP");
		String ddl = sql(table, DialectHolder.oracle10gDialect);
		for (String clause : List.of("PCTFREE 15", "INITRANS 3", "NOLOGGING", "STORAGE (", "INITIAL 65536", "PCTINCREASE 0", "BUFFER_POOL KEEP")) assertTrue(ddl.contains(clause), ddl);
	}
	@Test void invalidAndUnknownPartitionAttributesCannotDisappear() {
		for (String key : List.of("PCT_FREE", "LOGGING", "BUFFER_POOL", "UNKNOWN")) {
			Table table = table(PartitioningType.Range, null); table.getPartitioning().getPartitions().get(0).getSpecifics().put(key, "invalid");
			assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle26aiDialect));
		}
	}
	@Test void subpartitionPhysicalAttributesAndInlineCommentsAreRejected() {
		Table table = table(PartitioningType.Range, PartitioningType.List); SubPartition child = new SubPartition("C").setHighValue("1"); child.getSpecifics().put("PCT_FREE", "10");
		table.getPartitioning().getPartitions().get(0).getSubPartitions().add(child);
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle26aiDialect));
		Table commented = table(PartitioningType.Range, null); commented.getPartitioning().getPartitions().get(0).setRemarks("comment");
		assertThrows(IllegalArgumentException.class, () -> sql(commented, DialectHolder.oracle26aiDialect));
	}
	@Test void rangeListDoesNotLeakIntoTheSharedEightNineDialect() {
		Table table = table(PartitioningType.Range, PartitioningType.List);
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.defaultDialect));
		assertTrue(sql(table, DialectHolder.oracle10gDialect).contains("SUBPARTITION BY LIST"));
	}
	@Test void hashPhysicalValuesMayInheritFromTheTable() {
		Table table = table(PartitioningType.Hash, null); var part = table.getPartitioning().getPartitions().get(0); part.setHighValue((String) null);
		table.getSpecifics().put("PCT_FREE", "10"); table.getSpecifics().put("LOGGING", "TRUE");
		part.getSpecifics().put("PCT_FREE", "010"); part.getSpecifics().put("LOGGING", "YES");
		String ddl = sql(table, DialectHolder.oracle10gDialect);
		assertEquals(1, ddl.split("PCTFREE", -1).length - 1, ddl);
		assertFalse(ddl.substring(ddl.indexOf("PARTITION BY")).contains("PCTFREE"), ddl);
		part.getSpecifics().put("PCT_FREE", "20");
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle26aiDialect));
	}
	@Test void subpartitionUsesParentOverrideBeforeTableDefault() {
		Table table = table(PartitioningType.Range, PartitioningType.List); table.getSpecifics().put("PCT_FREE", "10");
		var parent = table.getPartitioning().getPartitions().get(0); parent.getSpecifics().put("PCT_FREE", "20");
		SubPartition child = new SubPartition("C").setHighValue("1"); child.getSpecifics().put("PCT_FREE", "20"); parent.getSubPartitions().add(child);
		String ddl = sql(table, DialectHolder.oracle10gDialect);
		assertFalse(ddl.substring(ddl.indexOf("SUBPARTITION C")).contains("PCTFREE"), ddl);
		child.getSpecifics().put("PCT_FREE", "10");
		assertThrows(IllegalArgumentException.class, () -> sql(table, DialectHolder.oracle26aiDialect));
	}
	@Test void subpartitionMayInheritStorageFromTheTableWithoutDuplicatingIt() {
		Table table = table(PartitioningType.Range, PartitioningType.Hash); table.getSpecifics().put("INITIAL_EXTENT", "65536");
		SubPartition child = new SubPartition("C"); child.getSpecifics().put("INITIAL_EXTENT", "65536");
		table.getPartitioning().getPartitions().get(0).getSubPartitions().add(child);
		String ddl = sql(table, DialectHolder.oracle10gDialect);
		assertEquals(1, ddl.split("INITIAL 65536", -1).length - 1, ddl);
	}
}
