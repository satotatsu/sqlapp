/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.CountingJdbc;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

final class OracleMetadataAuditAssertions {
	static void verify(Connection connection) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		String owner = connection.getMetaData().getUserName().toUpperCase(Locale.ROOT);
		String prefix = "AUD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
		try (var sql = connection.createStatement()) {
			try {
				for (int i = 0; i < 2; i++) {
					String table = prefix + i;
					sql.execute("CREATE TABLE " + table + " (ID NUMBER, REGION NUMBER, PARENT_KEY NUMBER UNIQUE, CONSTRAINT " + table + "_UK UNIQUE(ID,REGION) DEFERRABLE INITIALLY DEFERRED, CONSTRAINT " + table + "_CK CHECK (ID>=0) DISABLE, CONSTRAINT " + table + "_FK FOREIGN KEY(REGION) REFERENCES " + table + "(PARENT_KEY) DISABLE) PARTITION BY RANGE(ID) SUBPARTITION BY HASH(REGION) SUBPARTITIONS 2 (PARTITION P1 VALUES LESS THAN(100), PARTITION P2 VALUES LESS THAN(MAXVALUE))");
					sql.execute("CREATE INDEX " + table + "_IX ON " + table + "(REGION) LOCAL");
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName(owner);
				AtomicInteger count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection, text -> {
					String upper = text.toUpperCase(Locale.ROOT);
					return upper.contains("ALL_PART_TABLES") || upper.contains("ALL_PART_INDEXES")
						|| upper.contains("ALL_PART_KEY_COLUMNS") || upper.contains("ALL_SUBPART_KEY_COLUMNS")
						|| upper.contains("ALL_TAB_SUBPARTITIONS") || upper.contains("ALL_IND_SUBPARTITIONS");
				}, count);
				reader.setObjectName(prefix + "0");
				assertEquals(1, reader.getAllFull(counted).size());
				int single = count.get();
				count.set(0);
				reader.setObjectName(null);
				var tables = reader.getAllFull(counted);
				assertEquals(8, single);
				assertEquals(single, count.get(), "Partition query count must stay constant for multiple tables and indexes");
				for (int i = 0; i < 2; i++) {
					String name = prefix + i;
					Table table = tables.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
					Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
					assertTrue(restored.getConstraints().get(name + "_UK").isEnable());
					assertEquals(Deferrability.InitiallyDeferred, ((UniqueConstraint)restored.getConstraints().get(name + "_UK")).getDeferrability());
					assertFalse(restored.getConstraints().get(name + "_CK").isEnable());
					assertFalse(restored.getConstraints().get(name + "_FK").isEnable());
					assertEquals("ID", restored.getPartitioning().getPartitioningColumns().get(0).getName());
					assertEquals("REGION", restored.getPartitioning().getSubPartitioningColumns().get(0).getName());
					assertEquals(2, restored.getPartitioning().getPartitions().size());
					for (var part : restored.getPartitioning().getPartitions()) assertEquals(2, part.getSubPartitions().size());
					var index = restored.getIndexes().get(name + "_IX");
					assertNotNull(index);
					assertEquals("LOCAL", index.getSpecifics().get("oracle.index.locality"));
					assertEquals(2, index.getPartitioning().getPartitions().size());
					for (var part : index.getPartitioning().getPartitions()) {
						assertEquals(2, part.getSubPartitions().size());
						for (var sub : part.getSubPartitions()) assertNotNull(sub.getTableSpaceName());
					}
					var registry = dialect.createSqlFactoryRegistry();
					registry.getOptions().setDecorateSchemaName(true);
					sql.execute("DROP INDEX " + name + "_IX");
					for (var operation : registry.createSql(index, SqlType.CREATE)) sql.execute(operation.getSqlText());
					reader.setObjectName(name);
					var after = reader.getAllFull(connection).get(0).getIndexes().get(name + "_IX");
					assertEquals("LOCAL", after.getSpecifics().get("oracle.index.locality"));
					assertEquals(2, after.getPartitioning().getPartitions().size());
					for (int part = 0; part < 2; part++) {
						var beforePart = index.getPartitioning().getPartitions().get(part);
						var afterPart = after.getPartitioning().getPartitions().get(part);
						assertEquals(beforePart.getName(), afterPart.getName());
						for (int sub = 0; sub < 2; sub++) {
							assertEquals(beforePart.getSubPartitions().get(sub).getName(), afterPart.getSubPartitions().get(sub).getName());
							assertEquals(beforePart.getSubPartitions().get(sub).getTableSpaceName(), afterPart.getSubPartitions().get(sub).getTableSpaceName());
						}
					}
				}
			} finally {
				for (int i = 0; i < 2; i++) sql.execute("DROP TABLE " + prefix + i + " PURGE");
			}
		}
	}
}
