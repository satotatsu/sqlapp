/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class OracleIndexStructureChangeTest {
	private Table table(PartitioningType type) {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("ID");
		Index index = new Index("IX");
		table.getIndexes().add(index);
		index.getColumns().add("ID");
		index.getSpecifics().put("oracle.index.locality", "GLOBAL");
		index.getSpecifics().put("oracle.index.status", "N/A");
		Partitioning info = new Partitioning().setPartitioningType(type);
		info.getPartitioningColumns().add("ID");
		info.setPartitionSize(2);
		info.getPartitions().add(new Partition("P1").setHighValue("100").setTableSpaceName("USERS"));
		info.getPartitions().add(new Partition("P2").setHighValue("MAXVALUE").setTableSpaceName("USERS"));
		index.setPartitioning(info);
		return table;
	}

	private List<String> diff(Table before, Table after) {
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText().replaceAll("\\s+", " "))
				.toList();
	}

	@Test
	void rangeBoundaryChangesRecreateOnlyTheIndex() {
		Table before = table(PartitioningType.Range), after = before.clone();
		after.getIndexes().get(0).getPartitioning().getPartitions().get(0).setHighValue("200");
		var sql = diff(before, after);
		assertEquals(2, sql.size());
		assertEquals("DROP INDEX APP.IX", sql.get(0));
		assertTrue(sql.get(1).contains("VALUES LESS THAN (200)"), sql.toString());
		assertFalse(sql.toString().contains("DROP TABLE"));
	}

	@Test
	void hashPartitionCountUsesExplicitNamesWithoutDuplicateCountClause() {
		Table before = table(PartitioningType.Hash), after = before.clone();
		Partitioning info = after.getIndexes().get(0).getPartitioning();
		info.setPartitionSize(3);
		info.getPartitions().add(new Partition("P3").setTableSpaceName("USERS"));
		var sql = diff(before, after);
		assertEquals(2, sql.size());
		assertTrue(sql.get(1).contains("PARTITION P3 TABLESPACE USERS"), sql.toString());
		assertFalse(sql.get(1).contains("PARTITIONS 3"), sql.toString());
	}

	@Test
	void localStructureAndSimultaneousStateChangesAreRejected() {
		Table before = table(PartitioningType.Range);
		before.getIndexes().get(0).getSpecifics().put("oracle.index.locality", "LOCAL");
		Table after = before.clone();
		after.getIndexes().get(0).getPartitioning().getPartitions().get(0).setHighValue("200");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		Table global = table(PartitioningType.Range), target = global.clone();
		var partition = target.getIndexes().get(0).getPartitioning().getPartitions().get(0);
		partition.setHighValue("200");
		partition.getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> diff(global, target));
	}

	@Test
	void constraintIndexesAndOnlineReplacementAreProtected() {
		Table before = table(PartitioningType.Range);
		UniqueConstraint unique = new UniqueConstraint("UK", false);
		unique.setIndexName("IX");
		before.getConstraints().add(unique);
		Table after = before.clone();
		after.getIndexes().get(0).getPartitioning().getPartitions().get(0).setHighValue("200");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		Table plain = table(PartitioningType.Range), target = plain.clone();
		target.getIndexes().get(0).getPartitioning().getPartitions().get(0).setHighValue("200");
		var registry = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry();
		registry.getTableOptions().setOnlineIndex(true);
		assertThrows(IllegalArgumentException.class,
				() -> registry.getSqlFactory(target, SqlType.ALTER).createDiffSql(plain.diff(target)));
	}

	@Test
	void unsupportedGlobalTypesAndMissingHashCountsFail() {
		Table list = table(PartitioningType.List);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(list.getIndexes().get(0), SqlType.CREATE));
		Table hash = table(PartitioningType.Hash);
		hash.getIndexes().get(0).getPartitioning().getPartitions().clear();
		hash.getIndexes().get(0).getPartitioning().setPartitionSize(0);
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(hash.getIndexes().get(0), SqlType.CREATE));
	}

	@Test
	void createIfNotExistsIsLimitedToKnownSupportedDialects() {
		for (var dialect : List.of(DialectHolder.oracle10gDialect, DialectHolder.oracle11gDialect,
				DialectHolder.oracle19cDialect, DialectHolder.oracle23aiDialect, DialectHolder.oracle26aiDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			var factory = registry.getSqlFactory(table(PartitioningType.Range).getIndexes().get(0), SqlType.CREATE);
			factory.getOptions().setCreateIfNotExists(true);
			String sql = registry.createSql(table(PartitioningType.Range).getIndexes().get(0), SqlType.CREATE).get(0)
					.getSqlText();
			assertEquals(dialect instanceof com.sqlapp.data.db.dialect.oracle.Oracle23ai, sql.contains("IF NOT EXISTS"),
					sql);
		}
	}
}
