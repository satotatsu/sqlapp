/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class InformixIndexStorageTest {
	private Table table() {
		final var table = new Table("items");
		table.getColumns().add("id", c -> c.setDataType(DataType.INT));
		table.getIndexes().add("ix", table.getColumns().get(0));
		return table;
	}

	private String sql(Table table) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(table.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText();
	}

	@Test
	void recreatesExpressionPartitionsThroughXmlBeforeTheIndexMode() throws Exception {
		final var table = table();
		final var index = table.getIndexes().get(0);
		index.setEnable(false);
		index.setPartitioning(new Partitioning());
		index.getPartitioning().getSpecifics().put(INFORMIX_FRAGMENT_STRATEGY, "E");
		for (String name : java.util.List.of("quoted\"part", "\"rest\"")) {
			var partition = new Partition(name).setTableSpaceName("rootdbs");
			partition.getSpecifics().put(INFORMIX_FRAGMENT_EXPRESSION,
					name.equals("\"rest\"") ? "REMAINDER" : "(id<10)");
			index.getPartitioning().getPartitions().add(partition);
		}
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		final String sql = sql(restored);
		assertTrue(sql.contains("FRAGMENT BY EXPRESSION PARTITION \"quoted\"\"part\" (id<10) IN \"rootdbs\""), sql);
		assertTrue(sql.contains("PARTITION \"\"\"rest\"\"\" REMAINDER IN \"rootdbs\""), sql);
		assertTrue(sql.endsWith("DISABLED"));
	}

	@Test
	void distinguishesAttachedAndSingleDbspaceStorageAndRejectsConflicts() {
		final var table = table();
		final var index = table.getIndexes().get(0);
		index.getSpecifics().put(InformixIndexReader.INFORMIX_INDEX_IN_TABLE, "true");
		assertTrue(sql(table).endsWith("IN TABLE"));
		index.setTableSpaceName("rootdbs");
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		index.getSpecifics().put(InformixIndexReader.INFORMIX_INDEX_IN_TABLE, "false");
		assertTrue(sql(table).endsWith("IN \"rootdbs\""));
		index.setPartitioning(new Partitioning());
		assertThrows(IllegalArgumentException.class, () -> sql(table));
		final var inherited = table();
		inherited.getIndexes().get(0).getSpecifics().put(InformixIndexReader.INFORMIX_INDEX_FOLLOWS_TABLE, "true");
		assertFalse(sql(inherited).contains("FRAGMENT BY"));
		assertFalse(sql(inherited).contains("IN TABLE"));
		inherited.getIndexes().get(0).getSpecifics().put(InformixIndexReader.INFORMIX_INDEX_IN_TABLE, "invalid");
		assertThrows(IllegalArgumentException.class, () -> sql(inherited));
		inherited.getIndexes().get(0).getSpecifics().remove(InformixIndexReader.INFORMIX_INDEX_IN_TABLE);
		inherited.getIndexes().get(0).setTableSpaceName("rootdbs");
		assertThrows(IllegalArgumentException.class, () -> sql(inherited));
	}
}
