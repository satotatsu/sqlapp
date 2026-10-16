/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleListPartitionSplitTest {
	private Table source(String values) {
		Table table = new Table("T").setSchemaName("APP"); table.getColumns().add("ID");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.List);
		info.getPartitioningColumns().add("ID"); info.getPartitions().add(new Partition("P").setHighValue(values));
		table.setPartitioning(info);
		Index index = new Index("IX"); table.getIndexes().add(index); index.getColumns().add("ID");
		index.getSpecifics().put("oracle.index.locality", "LOCAL"); index.setPartitioning(info.clone());
		index.getPartitioning().getPartitions().get(0).setName("IP");
		index.getPartitioning().getPartitions().get(0).getSpecifics().put("oracle.index.status", "USABLE");
		return table.clone();
	}
	private Table split(Table source, String left, String right) {
		Table table = source.clone();
		for (Partitioning info : java.util.List.of(table.getPartitioning(), table.getIndexes().get(0).getPartitioning())) {
			Partition original = info.getPartitions().get(0).clone(); info.getPartitions().clear();
			info.getPartitions().add(original.clone().setName(original.getName() + "_L").setHighValue(left));
			info.getPartitions().add(original.clone().setName(original.getName() + "_R").setHighValue(right));
		}
		return table;
	}
	private String sql(Table before, Table after) {
		var factory = DialectHolder.oracle10gDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		var operations = factory.createDiffSql(before.diff(after)); assertEquals(1, operations.size());
		return operations.get(0).getSqlText().replaceAll("\\s+", " ").replace("( ", "(").trim();
	}
	@Test void splitAndMergeMaintainValueUnionAndLocalIndexes() {
		Table before = source("'JP', 'US', NULL"), after = split(before, "'US'", "NULL, 'JP'");
		assertEquals("ALTER TABLE APP.T SPLIT PARTITION P VALUES ('US') INTO (PARTITION P_L, PARTITION P_R) UPDATE INDEXES (IX (PARTITION IP_L, PARTITION IP_R))", sql(before, after));
		assertEquals("ALTER TABLE APP.T MERGE PARTITIONS P_L, P_R INTO PARTITION P UPDATE INDEXES (IX (PARTITION IP))", sql(after, before));
	}
	@Test void defaultSplitAndMergeKeepTheDefaultPartition() {
		Table before = source("DEFAULT"), after = split(before, "'DEFAULT', 'O''Brien,JP'", "DEFAULT");
		assertTrue(sql(before, after).contains("VALUES ('DEFAULT', 'O''Brien,JP')"));
		assertTrue(sql(after, before).contains("MERGE PARTITIONS P_L, P_R"));
		assertThrows(IllegalArgumentException.class, () -> sql(before, split(before, "'JP'", "'US'")));
	}
	@Test void missingExtraAndOverlappingValuesAreRejected() {
		Table before = source("1, 2, 3");
		for (String[] values : new String[][] {{"1", "2"}, {"1", "2, 3, 4"}, {"1, 2", "2, 3"}, {"1, 1", "2, 3"}})
			assertThrows(IllegalArgumentException.class, () -> sql(before, split(before, values[0], values[1])));
	}
	@Test void expressionsTuplesAndMalformedListsAreRejected() {
		for (String value : new String[] {"TO_DATE('2026-01-01','YYYY-MM-DD')", "(1, 2)", "1,", "'unterminated", "q'[JP]'"}) {
			Table before = source("DEFAULT");
			assertThrows(IllegalArgumentException.class, () -> sql(before, split(before, value, "DEFAULT")));
		}
	}
	@Test void indexValuesMustMatchTableValues() {
		Table before = source("1,2"), after = split(before, "1", "2");
		after.getIndexes().get(0).getPartitioning().getPartitions().get(1).setHighValue("3");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}
	@Test void multipleKeysAndOtherTableChangesAreRejected() {
		Table before = source("1,2"); before.getPartitioning().getPartitioningColumns().add("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(before, split(before, "1", "2")));
		Table single = source("1,2"), after = split(single, "1", "2"); after.getColumns().add("OTHER");
		assertThrows(IllegalArgumentException.class, () -> sql(single, after));
	}
	@Test void splitPreservesIdentifierQuotingOptions() {
		Table before = source("1,2"), after = split(before, "1", "2");
		after.getPartitioning().getPartitions().get(0).setName("lower");
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setQuateObjectName(false);
		assertTrue(factory.createDiffSql(before.diff(after)).get(0).getSqlText().contains("PARTITION lower"));
	}
	@Test void mergeCannotLoseValues() {
		Table merged = source("1,2,3"), before = split(merged, "1", "2,3");
		merged.getPartitioning().getPartitions().get(0).setHighValue("1,2");
		assertThrows(IllegalArgumentException.class, () -> sql(before, merged));
	}
	@Test void unaffectedPartitionsRemainUnchanged() {
		Table before = source("1,2"), after = split(before, "1", "2");
		for (Table table : java.util.List.of(before, after)) {
			table.getPartitioning().getPartitions().add(new Partition("LAST").setHighValue("3"));
			Partition segment = new Partition("ILAST").setHighValue("3");
			segment.getSpecifics().put("oracle.index.status", "USABLE");
			table.getIndexes().get(0).getPartitioning().getPartitions().add(segment);
		}
		assertTrue(sql(before, after).contains("VALUES (1)"));
		after.getPartitioning().getPartitions().get(2).setHighValue("4");
		assertThrows(IllegalArgumentException.class, () -> sql(before, after));
	}
}
