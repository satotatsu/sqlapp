/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleIndexPlacementRegressionTest {
	@Test
	void noCacheAndOrderedSequenceUseOracleSyntax() {
		var registry = com.sqlapp.data.db.dialect.oracle.DialectHolder.oracle10gDialect.createSqlFactoryRegistry();
		var sequence = new Sequence("SEQ").setCache(false).setCacheSize(0).setOrder(true);
		String sql = registry.createSql(sequence, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("NOCACHE"));
		assertFalse(sql.contains("CACHE 0"));
		assertTrue(sql.endsWith("ORDER"));
	}

	@Test
	void localPartitionNamesAndStorageSurviveSqlGeneration() {
		Table table = new Table("AUDIT");
		table.getColumns().add("ID");
		Index index = new Index("IX_AUDIT");
		table.getIndexes().add(index);
		index.getColumns().add("ID");
		index.getSpecifics().put("oracle.index.locality", "LOCAL");
		Partitioning partitioning = new Partitioning();
		partitioning.setPartitioningType(PartitioningType.Range);
		Partition partition = new Partition("IP1");
		partition.setTableSpaceName("USERS");
		partition.getSubPartitions().add(new SubPartition("ISP1").setTableSpaceName("USERS"));
		partitioning.getPartitions().add(partition);
		index.setPartitioning(partitioning);
		var registry = new com.sqlapp.data.db.dialect.oracle.resolver.OracleDialectResolver().getDialect(10, 0)
				.createSqlFactoryRegistry();
		String sql = registry.createSql(index, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("LOCAL"));
		assertTrue(sql.contains("PARTITION IP1 TABLESPACE USERS"));
		assertTrue(sql.contains("SUBPARTITION ISP1 TABLESPACE USERS"));
		assertFalse(sql.contains("PARTITION BY"));
	}
}
