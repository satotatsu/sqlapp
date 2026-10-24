/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.saphana.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SapHanaAdvancedOptionsTest {
	@Test
	void createsRangeHashWithDifferentBucketCountsAndLeafParents() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var table = new Table("T");
		table.getColumns().add("A", c -> c.setDataType(DataType.INT));
		table.getColumns().add("B", c -> c.setDataType(DataType.INT));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Range)
				.setSubPartitioningType(PartitioningType.Hash);
		table.getPartitioning().getPartitioningColumns().add("A");
		table.getPartitioning().getSubPartitioningColumns().add("B");
		var first = new Partition("P1").setLowValue("0").setHighValue("10");
		first.getSubPartitions().add(new SubPartition("H1"));
		first.getSubPartitions().add(new SubPartition("H2"));
		var second = new Partition("P2").setLowValue("10").setHighValue("20");
		second.getSubPartitions().add(new SubPartition("H3"));
		table.getPartitioning().getPartitions().add(first);
		table.getPartitioning().getPartitions().add(second);
		table.getPartitioning().getPartitions().add(new Partition("OTHER"));
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("PARTITIONS 2"), sql);
		assertTrue(sql.contains("PARTITIONS 1"), sql);
		assertTrue(sql.endsWith("(PARTITION OTHERS))"), sql);
		first.getSubPartitions().getFirst().getSpecifics().put("LOAD_UNIT", "PAGE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		first.getSpecifics().put("LOAD_UNIT", "PAGE");
		assertDoesNotThrow(() -> registry.createSql(table, SqlType.CREATE));
		first.getSubPartitions().getFirst().setLowValue("1");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void createsUniformRangeHashFromAnExplicitPositiveCount() {
		var table = new Table("T");
		table.getColumns().add("A", c -> c.setDataType(DataType.INT));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Range)
				.setSubPartitioningType(PartitioningType.Hash).setSubPartitionSize(2);
		table.getPartitioning().getPartitioningColumns().add("A");
		table.getPartitioning().getSubPartitioningColumns().add("A");
		table.getPartitioning().getPartitions().add(new Partition("OTHER"));
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(table, SqlType.CREATE).getFirst().getSqlText().contains("PARTITIONS 2"));
		table.getPartitioning().setSubPartitionSize(0);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void createsHeterogeneousRangesWithPartitionLoadUnits() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var table = new Table("T");
		table.getColumns().add("A", c -> c.setDataType(DataType.INT));
		table.getColumns().add("B", c -> c.setDataType(DataType.INT));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Range)
				.setSubPartitioningType(PartitioningType.Range);
		table.getPartitioning().getPartitioningColumns().add("A");
		table.getPartitioning().getSubPartitioningColumns().add("B");
		var first = new Partition("P1").setLowValue("0").setHighValue("10");
		first.getSubPartitions().add(new SubPartition("S1").setLowValue("0").setHighValue("100"));
		first.getSubPartitions().getFirst().getSpecifics().put("LOAD_UNIT", "PAGE");
		var other = new Partition("P2");
		other.getSubPartitions().add(new SubPartition("S2").setLowValue("0").setHighValue("200"));
		table.getPartitioning().getPartitions().add(first);
		table.getPartitioning().getPartitions().add(other);
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("SUBPARTITION BY RANGE"), sql);
		assertTrue(sql.contains("100 PAGE LOADABLE"), sql);
		assertTrue(sql.contains("VALUES < 200"), sql);
		first.getSubPartitions().getFirst().getSpecifics().put("LOAD_UNIT", "INVALID");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void rejectsMixedLoadUnitsOnUnsupportedHashPartitions() {
		var table = new Table("T");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(2);
		table.getPartitioning().getPartitioningColumns().add("ID");
		var partition = new Partition("P");
		partition.getSpecifics().put("LOAD_UNIT", "PAGE");
		table.getPartitioning().getPartitions().add(partition);
		assertThrows(IllegalArgumentException.class,
				() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
	}

	@Test
	void preservesColumnLoadUnitsAndRejectsUnsupportedAlter() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var table = new Table("NSE");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getSpecifics().put("LOAD_UNIT", "PAGE");
		table.getColumns().getFirst().getSpecifics().put("LOAD_UNIT", "COLUMN");
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("COLUMN LOADABLE"), sql);
		assertTrue(sql.endsWith("PAGE LOADABLE"), sql);
		var target = table.clone();
		target.getColumns().getFirst().getSpecifics().put("LOAD_UNIT", "PAGE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table.diff(target), SqlType.ALTER));
		table.getColumns().getFirst().getSpecifics().put("LOAD_UNIT", "INVALID");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		table.getColumns().getFirst().getSpecifics().put("LOAD_UNIT", "PAGE");
		table.setTableDataStoreType(Table.TableDataStoreType.Row);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void rejectsStorageAndPartitionAlterInsteadOfReturningAnEmptyPlan() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		Table original = new Table("T");
		original.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		var target = original.clone();
		target.getSpecifics().put("LOAD_UNIT", "PAGE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		target.getSpecifics().clear();
		target.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(2);
		target.getPartitioning().getPartitioningColumns().add("ID");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void createsUniformAndHeterogeneousHashRangePartitions() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		Table table = new Table("T");
		table.getColumns().add("A", c -> c.setDataType(DataType.INT));
		table.getColumns().add("B", c -> c.setDataType(DataType.INT));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(2)
				.setSubPartitioningType(PartitioningType.Range);
		table.getPartitioning().getPartitioningColumns().add("A");
		table.getPartitioning().getSubPartitioningColumns().add("B");
		Partition parent = new Partition("P1");
		parent.getSubPartitions().add(new SubPartition("S1").setLowValue("0").setHighValue("10"));
		parent.getSubPartitions().add(new SubPartition("OTHER"));
		table.getPartitioning().getPartitions().add(parent);
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains(", RANGE ( B)"), sql);
		assertTrue(sql.contains("PARTITION 0 <= VALUES < 10"), sql);
		var different = parent.clone().setName("P2");
		different.getSubPartitions().getFirst().setHighValue("20");
		table.getPartitioning().getPartitions().add(different);
		String heterogeneous = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(heterogeneous.contains("(PARTITION 1) SUBPARTITION BY RANGE"), heterogeneous);
		assertTrue(heterogeneous.contains("VALUES < 20"), heterogeneous);
		different.setLowValue("1");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		different.setLowValue("");
		for (var part : table.getPartitioning().getPartitions())
			part.getSubPartitions().clear();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void rejectsUnbalancedHashHashWhilePreservingUniformHashHash() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var table = new Table("T");
		table.getColumns().add("A", c -> c.setDataType(DataType.INT));
		table.getColumns().add("B", c -> c.setDataType(DataType.INT));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(2)
				.setSubPartitioningType(PartitioningType.Hash).setSubPartitionSize(5);
		table.getPartitioning().getPartitioningColumns().add("A");
		table.getPartitioning().getSubPartitioningColumns().add("B");
		for (int i = 1; i <= 2; i++) {
			var parent = new Partition("P" + i);
			for (int j = 0; j <= i; j++)
				parent.getSubPartitions().add(new SubPartition("H" + j));
			table.getPartitioning().getPartitions().add(parent);
		}
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		table.getPartitioning().getPartitions().get(1).getSubPartitions().remove(2);
		table.getPartitioning().setSubPartitionSize(2);
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains(", HASH ( B) PARTITIONS 2"), sql);
		table.getPartitioning().getPartitions().getFirst().getSpecifics().put("LOAD_UNIT", "PAGE");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void preservesTablePageLoadableStorage() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		Table table = new Table("NSE");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		table.getSpecifics().put("LOAD_UNIT", "PAGE");
		assertTrue(registry.createSql(table, SqlType.CREATE).getFirst().getSqlText().contains("PAGE LOADABLE"));
		table.setTableDataStoreType(Table.TableDataStoreType.Row);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void createsThreePartRemoteSynonymsWithoutLosingTargetNamespace() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var synonym = new Synonym("ALIAS").setDbLinkName("Remote DB").setObjectSchemaName("Odd Schema")
				.setObjectName("A\"B");
		assertEquals("CREATE SYNONYM ALIAS FOR \"Remote DB\".\"Odd Schema\".\"A\"\"B\"",
				registry.createSql(synonym, SqlType.CREATE).getFirst().getSqlText());
		synonym.setObjectSchemaName(null);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(synonym, SqlType.CREATE));
	}

	@Test
	void replaysRoleAndObjectPrivilegesWithQuotedNames() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var member = new RoleMember().setMemberRoleName("Base Role").setGranteeName("Child Role").setAdmin(true);
		assertEquals("GRANT \"Base Role\" TO \"Child Role\" WITH ADMIN OPTION",
				registry.createSql(member, SqlType.GRANT).getFirst().getSqlText());
		assertEquals("REVOKE \"Base Role\" FROM \"Child Role\"",
				registry.createSql(member, SqlType.REVOKE).getFirst().getSqlText());
		var grant = new ObjectPrivilege().setSchemaName("Odd Schema").setObjectName("A\"B").setGranteeName("Child Role")
				.setPrivilege("SELECT").setGrantable(true);
		assertEquals("GRANT SELECT ON \"Odd Schema\".\"A\"\"B\" TO \"Child Role\" WITH GRANT OPTION",
				registry.createSql(grant, SqlType.GRANT).getFirst().getSqlText());
		grant.setPrivilege("SELECT; DROP TABLE T");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grant, SqlType.GRANT));
		grant.setPrivilege("SELECT").setObjectName("Odd Schema");
		grant.getSpecifics().put("OBJECT_TYPE", "SCHEMA");
		assertEquals("GRANT SELECT ON SCHEMA \"Odd Schema\" TO \"Child Role\" WITH GRANT OPTION",
				registry.createSql(grant, SqlType.GRANT).getFirst().getSqlText());
		grant.setObjectName(null);
		assertEquals("GRANT SELECT ON SCHEMA \"Odd Schema\" TO \"Child Role\" WITH GRANT OPTION",
				registry.createSql(grant, SqlType.GRANT).getFirst().getSqlText());
	}

	@Test
	void altersSequenceWithoutImplicitRestartOnDiff() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var original = new Sequence("S").setStartValue(10).setIncrementBy(2).setCacheSize(5);
		var target = original.clone().setCache(false).setCycle(false);
		String sql = registry.createSql(original.diff(target), SqlType.ALTER).getFirst().getSqlText();
		assertTrue(sql.contains("NO CACHE"), sql);
		assertFalse(sql.contains("RESTART"), sql);
		target.setStartValue(20);
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).getFirst().getSqlText()
				.contains("RESTART WITH 20"));
		target.setIncrementBy(0);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(target, SqlType.ALTER));
	}

	@Test
	void generatesPartitionsAndRejectsUnrepresentableOptions() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		Table table = new Table("T");
		table.getColumns().add("A B", c -> c.setDataType(DataType.INT));
		table.toPartitioning().getPartitioning().setPartitioningType(PartitioningType.Hash).setPartitionSize(3);
		table.getPartitioning().getPartitioningColumns().add("A B");
		assertTrue(registry.createSql(table, SqlType.CREATE).getFirst().getSqlText()
				.contains("PARTITION BY HASH ( \"A B\") PARTITIONS 3"));
		table.getPartitioning().setPartitioningType(PartitioningType.Range);
		table.getPartitioning().getPartitions().add(new Partition("P1").setLowValue("0").setHighValue("10"));
		table.getPartitioning().getPartitions().add(new Partition("OTHERS"));
		String sql = registry.createSql(table, SqlType.CREATE).getFirst().getSqlText();
		assertTrue(sql.contains("PARTITION 0 <= VALUES < 10"), sql);
		assertTrue(sql.contains("PARTITION OTHERS"), sql);
		table.getPartitioning().getPartitions().getFirst().setLowValueInclusive(false);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		table.getPartitioning().getPartitions().getFirst().setLowValueInclusive(true);
		table.getPartitioning().setSubPartitioningType(PartitioningType.Hash);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void decoratesSpecialIndexesAndRejectsInvalidFulltextCombinations() {
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.cloudDialect)) {
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			Table table = new Table("TEXTS").setSchemaName("Odd Schema");
			table.getColumns().add("BODY", c -> c.setDataType(DataType.NCLOB));
			Index index = new Index("FT", table.getColumns().getFirst()).setIndexType(IndexType.FullText);
			table.getIndexes().add(index);
			assertTrue(registry.createSql(index, SqlType.CREATE).getFirst().getSqlText()
					.contains("ON \"Odd Schema\".TEXTS"));
			assertTrue(registry.createSql(index, SqlType.DROP).getFirst().getSqlText()
					.startsWith(dialect == DialectHolder.defaultDialect ? "DROP FULLTEXT INDEX" : "DROP INDEX"));
			if (dialect == DialectHolder.defaultDialect) {
				index.getSpecifics().put("LANGUAGE_COLUMN", "LANG");
				assertTrue(registry.createSql(index, SqlType.CREATE).getFirst().getSqlText()
						.contains("FAST PREPROCESS OFF"));
				index.getSpecifics().put("FAST_PREPROCESS", true);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
				index.getSpecifics().remove("LANGUAGE_COLUMN");
				index.getSpecifics().put("FLUSH_AFTER_DOCUMENTS", 0);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(index, SqlType.CREATE));
			}
		}
	}
}
