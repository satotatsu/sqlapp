/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class OracleDomainIndexPartitionTest {
	private Table table() throws Exception {
		Table table = new Table("T").setSchemaName("APP");
		table.getColumns().add("DOC");
		Index index = new Index("IX").setIndexType(IndexType.Domain);
		index.getColumns().add("DOC");
		table.getIndexes().add(index);
		index.getSpecifics().put("ITYP_OWNER", "CTXSYS");
		index.getSpecifics().put("ITYP_NAME", "CONTEXT");
		index.getSpecifics().put("PARAMETERS", "SYNC (MANUAL)");
		index.getSpecifics().put("oracle.index.locality", "LOCAL");
		index.getSpecifics().put("oracle.index.status", "N/A");
		Partitioning info = new Partitioning().setPartitioningType(PartitioningType.Range);
		info.getPartitioningColumns().add("DOC");
		Partition part = new Partition("Mixed Partition").setHighValue("100");
		part.getSpecifics().put("oracle.index.status", "USABLE");
		new OracleIndexReader(DialectHolder.oracle10gDialect).readDomainPartitionDetails(new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return "PARAMETERS".equals(key) ? "storage O'Reilly\nSYNC OFF " : "VALID";
			}

			@Override
			public Object getObject(String key) {
				return getString(key);
			}
		}, part);
		info.getPartitions().add(part);
		index.setPartitioning(info);
		return table;
	}

	private Partition partition(Table table) {
		return table.getIndexes().get(0).getPartitioning().getPartitions().get(0);
	}

	private List<String> diff(Table before, Table after) {
		var factory = DialectHolder.oracle26aiDialect.createSqlFactoryRegistry().getSqlFactory(after, SqlType.ALTER);
		factory.getOptions().setDecorateSchemaName(true);
		return factory.createDiffSql(before.diff(after)).stream().map(op -> op.getSqlText()).toList();
	}

	private String create(Table table) {
		return DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(table.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
	}

	@Test
	void localDomainPartitionsPreserveParametersThroughXml() throws Exception {
		Table before = table();
		Table restored = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
		String sql = create(restored);
		assertTrue(sql.contains(
				"INDEXTYPE IS CTXSYS.CONTEXT LOCAL (PARTITION \"Mixed Partition\" PARAMETERS ('storage O''Reilly\nSYNC OFF ')) PARAMETERS ('SYNC (MANUAL)')"),
				sql);
		assertFalse(sql.contains("VALUES LESS THAN"));
		assertTrue(diff(before, restored).isEmpty());
	}

	@Test
	void partitionParameterChangeUsesModifyWithoutDrop() throws Exception {
		Table before = table(), after = before.clone();
		partition(after).getSpecifics().put("PARAMETERS", "SYNC (EVERY \"FREQ=MINUTELY\")");
		assertEquals(List.of(
				"ALTER INDEX APP.IX MODIFY PARTITION \"Mixed Partition\" PARAMETERS ('SYNC (EVERY \"FREQ=MINUTELY\")')"),
				diff(before, after));
	}

	@Test
	void partitionRecoveryDoesNotReplayCreationParameters() throws Exception {
		Table before = table(), after = before.clone();
		partition(before).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertEquals(List.of("ALTER INDEX APP.IX REBUILD PARTITION \"Mixed Partition\""), diff(before, after));
		assertEquals(List.of("ALTER INDEX APP.IX MODIFY PARTITION \"Mixed Partition\" UNUSABLE"), diff(after, before));
	}

	@Test
	void recoveryAndExplicitParameterUpdateShareOneRebuild() throws Exception {
		Table before = table(), after = before.clone();
		partition(before).getSpecifics().put("oracle.index.status", "UNUSABLE");
		partition(after).getSpecifics().put("PARAMETERS", "REPLACE METADATA");
		assertEquals(
				List.of("ALTER INDEX APP.IX REBUILD PARTITION \"Mixed Partition\" PARAMETERS ('REPLACE METADATA')"),
				diff(before, after));
	}

	@Test
	void physicalChangesAndWholeLocalRebuildAreRejected() throws Exception {
		Table before = table(), after = before.clone();
		partition(after).setHighValue("200");
		assertThrows(IllegalArgumentException.class, () -> diff(before, after));
		Table moved = before.clone();
		partition(moved).setTableSpaceName("OTHER");
		assertThrows(IllegalArgumentException.class, () -> create(moved));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.oracle26aiDialect.createSqlFactoryRegistry()
				.createSql(before.getIndexes().get(0), SqlType.REBUILD));
	}

	@Test
	void unsupportedPartitionStatesAndGlobalLocalityAreRejected() throws Exception {
		Table table = table();
		partition(table).getSpecifics().put("oracle.index.status", "UNUSABLE");
		assertThrows(IllegalArgumentException.class, () -> create(table));
		Table global = table();
		global.getIndexes().get(0).getSpecifics().put("oracle.index.locality", "GLOBAL");
		assertThrows(IllegalArgumentException.class, () -> create(global));
		Table failed = table();
		partition(failed).getSpecifics().put("DOMIDX_OPSTATUS", "FAILED");
		assertThrows(IllegalArgumentException.class, () -> create(failed));
	}
}
