/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.CountingJdbc;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

final class SqlServerMetadataAuditAssertions {
	static void verify(Connection connection) throws Exception {
		verifyTemporal(connection);
		var dialect = DialectResolver.getInstance().getDialect(connection);
		String prefix = "AUD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		try (var sql = connection.createStatement()) {
			sql.execute("CREATE PARTITION FUNCTION " + prefix + "_PF(int) AS RANGE RIGHT FOR VALUES(100,200)");
			sql.execute("CREATE PARTITION SCHEME " + prefix + "_PS AS PARTITION " + prefix + "_PF ALL TO ([PRIMARY])");
			try {
				for (int i = 0; i < 2; i++) {
					sql.execute("CREATE TABLE dbo." + prefix + i + "(ID int NOT NULL, NOTE nvarchar(max)) ON " + prefix
							+ "_PS(ID)");
					sql.execute("CREATE INDEX IX_SHARED ON dbo." + prefix + i + "(ID)");
					sql.execute("CREATE INDEX IX_FILTERED ON dbo." + prefix + i
							+ "(ID DESC) INCLUDE(NOTE) WHERE ID > 0 ON [PRIMARY]");
					sql.execute("INSERT INTO dbo." + prefix + i
							+ " VALUES (1,N'a'),(100,N'b'),(200,REPLICATE(CAST(N'x' AS nvarchar(max)),6000))");
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("dbo");
				reader.setCatalogName(connection.getCatalog());
				AtomicInteger count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection,
						text -> text.contains("sys.partition_schemes") || text.contains("sys.allocation_units"), count);
				reader.setObjectName(prefix + "0");
				assertEquals(1, reader.getAllFull(counted).size());
				int single = count.get();
				count.set(0);
				reader.setObjectName(null);
				var tables = reader.getAllFull(counted);
				assertEquals(single, count.get(), "Partition query count must not grow with table count");
				assertTrue(single > 0);
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (int i = 0; i < 2; i++) {
					String name = prefix + i;
					Table table = tables.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
					Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
					assertEquals(1, restored.getPartitioning().getPartitioningColumns().size());
					assertEquals("ID", restored.getPartitioning().getPartitioningColumns().get(0).getName());
					assertEquals(3, restored.getPartitioning().getPartitions().size());
					assertEquals(1, restored.getIndexes().get("IX_SHARED").getColumns().size());
					assertEquals(Order.Desc, restored.getIndexes().get("IX_FILTERED").getColumns().get(0).getOrder());
					assertEquals("NOTE", restored.getIndexes().get("IX_FILTERED").getIncludes().get(0).getName());
					assertNotNull(restored.getIndexes().get("IX_FILTERED").getWhere());
					assertEquals(prefix + "_PS",
							restored.getIndexes().get("IX_SHARED").getPartitioning().getPartitionSchemeName());
					for (var part : restored.getPartitioning().getPartitions()) {
						assertEquals(1L, ((Number) Statistics.ROWS.getValue(part)).longValue());
						assertTrue(Long.parseLong(part.getStatistics().get("total_space")) > 0);
					}
					sql.execute("DROP TABLE dbo." + name);
					for (var operation : registry.createSql(restored, SqlType.CREATE)) {
						try {
							sql.execute(operation.getSqlText());
						} catch (java.sql.SQLException e) {
							throw new AssertionError(operation.getSqlText(), e);
						}
					}
					reader.setObjectName(name);
					var after = reader.getAllFull(connection).get(0);
					assertEquals(1, after.getPartitioning().getPartitioningColumns().size());
					assertEquals(3, after.getPartitioning().getPartitions().size());
					assertEquals(restored.getIndexes().get("IX_FILTERED").getWhere(),
							after.getIndexes().get("IX_FILTERED").getWhere());
					assertEquals(Order.Desc, after.getIndexes().get("IX_FILTERED").getColumns().get(0).getOrder());
					assertEquals("PRIMARY",
							after.getIndexes().get("IX_FILTERED").getSpecifics().get("FILE_GROUP_NAME"));
				}
			} finally {
				for (int i = 0; i < 2; i++)
					sql.execute("DROP TABLE IF EXISTS dbo." + prefix + i);
				sql.execute("DROP PARTITION SCHEME " + prefix + "_PS");
				sql.execute("DROP PARTITION FUNCTION " + prefix + "_PF");
			}
		}
		verifyChangeTracking(connection, prefix + "_CT");
	}

	private static void verifyChangeTracking(Connection connection, String name) throws Exception {
		try (var sql = connection.createStatement()) {
			sql.execute(
					"ALTER DATABASE CURRENT SET CHANGE_TRACKING = ON (CHANGE_RETENTION = 2 DAYS, AUTO_CLEANUP = ON)");
			sql.execute("CREATE TABLE dbo." + name + " (ID int NOT NULL PRIMARY KEY, VALUE int)");
			try {
				sql.execute("ALTER TABLE dbo." + name + " ENABLE CHANGE_TRACKING WITH (TRACK_COLUMNS_UPDATED = ON)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("dbo");
				reader.setObjectName(name);
				var original = reader.getAllFull(connection).get(0);
				assertEquals(Boolean.TRUE, original.getSpecifics().get("has_change_tracking", Boolean.class));
				Table restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				sql.execute("DROP TABLE dbo." + name);
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(restored, SqlType.CREATE)) {
					try {
						sql.execute(operation.getSqlText());
					} catch (java.sql.SQLException e) {
						throw new AssertionError(operation.getSqlText(), e);
					}
				}
				var after = reader.getAllFull(connection).get(0);
				assertEquals(Boolean.TRUE, after.getSpecifics().get("has_change_tracking", Boolean.class));
				assertEquals(Boolean.TRUE, after.getSpecifics().get("is_track_columns_updated_on", Boolean.class));
			} finally {
				sql.execute("DROP TABLE IF EXISTS dbo." + name);
			}
		}
	}
	private static void verifyTemporal(Connection connection) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		String name = "TEMP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String history = name + "_HISTORY";
		try (var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE dbo." + name + " (ID int NOT NULL PRIMARY KEY, VALUE int, "
					+ "VALID_FROM datetime2 GENERATED ALWAYS AS ROW START HIDDEN NOT NULL, "
					+ "VALID_TO datetime2 GENERATED ALWAYS AS ROW END HIDDEN NOT NULL, "
					+ "PERIOD FOR SYSTEM_TIME(VALID_FROM, VALID_TO)) "
					+ "WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = dbo." + history + "))");
			try {
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setCatalogName(connection.getCatalog());
				reader.setSchemaName("dbo");
				reader.setObjectName(name);
				Table original = reader.getAllFull(connection).get(0);
				assertTrue(original.getSystemVersioning().isEnable());
				assertEquals(history, original.getSystemVersioning().getHistoryTableName());
				assertTrue(original.getColumns().get("VALID_FROM").isHidden());
				assertEquals("VALID_TO", original.getTemporalPeriods().get(0).getEndColumnName());
				Table restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(original, SqlType.DROP)) sql.execute(operation.getSqlText());
				// Keep the existing history table and reconnect to it during recreation.
				for (var operation : registry.createSql(restored, SqlType.CREATE)) sql.execute(operation.getSqlText());
				Table recreated = reader.getAllFull(connection).get(0);
				assertEquals(restored.getTemporalPeriods(), recreated.getTemporalPeriods());
				assertEquals(restored.getSystemVersioning(), recreated.getSystemVersioning());
				var altered = recreated.clone();
				altered.getColumns().get("VALID_FROM").setHidden(false);
				com.sqlapp.data.db.sql.SqlFactory<Table> alterFactory = registry.getSqlFactory(altered, SqlType.ALTER);
				for (var operation : alterFactory.createDiffSql(recreated.diff(altered))) sql.execute(operation.getSqlText());
				assertFalse(reader.getAllFull(connection).get(0).getColumns().get("VALID_FROM").isHidden());
				Table disabled = altered.clone();
				disabled.toSystemVersioning().setEnable(false);
				for (var operation : alterFactory.createDiffSql(altered.diff(disabled))) sql.execute(operation.getSqlText());
				Table readDisabled = reader.getAllFull(connection).get(0);
				assertFalse(readDisabled.getSystemVersioning().isEnable());
				Table reenabled = readDisabled.clone();
				reenabled.toSystemVersioning().setEnable(true).setHistoryTableSchemaName("dbo").setHistoryTableName(history);
				for (var operation : alterFactory.createDiffSql(readDisabled.diff(reenabled))) sql.execute(operation.getSqlText());
				assertEquals(history, reader.getAllFull(connection).get(0).getSystemVersioning().getHistoryTableName());
				sql.execute("INSERT INTO dbo." + name + "(ID, VALUE) VALUES(1, 10)");
				sql.execute("UPDATE dbo." + name + " SET VALUE = 20 WHERE ID = 1");
				try (var rows = sql.executeQuery("SELECT COUNT(*) FROM dbo." + history)) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				Table beforeAdd = reader.getAllFull(connection).get(0);
				Table withColumn = beforeAdd.clone();
				withColumn.getColumns().add(new Column("EXTRA_VALUE").setDataType(com.sqlapp.data.db.datatype.DataType.NVARCHAR).setLength(80));
				for (var operation : alterFactory.createDiffSql(beforeAdd.diff(withColumn))) sql.execute(operation.getSqlText());
				assertTrue(reader.getAllFull(connection).get(0).getSystemVersioning().isEnable());
				try (var rows = sql.executeQuery("SELECT EXTRA_VALUE FROM dbo." + history)) {
					assertTrue(rows.next());
					assertNull(rows.getString(1));
				}
				try (var rows = sql.executeQuery("SELECT VALUE, EXTRA_VALUE FROM dbo." + name + " WHERE ID=1")) {
					assertTrue(rows.next());
					assertEquals(20, rows.getInt(1));
					assertNull(rows.getString(2));
				}
				Table beforeWiden = reader.getAllFull(connection).get(0);
				Table widened = beforeWiden.clone();
				widened.getColumns().get("EXTRA_VALUE").setLength(160);
				for (var operation : alterFactory.createDiffSql(beforeWiden.diff(widened))) sql.execute(operation.getSqlText());
				assertEquals(160L, reader.getAllFull(connection).get(0).getColumns().get("EXTRA_VALUE").getLength());
				try (var rows = sql.executeQuery("SELECT max_length FROM sys.columns WHERE object_id = OBJECT_ID('dbo."
						+ history + "') AND name = 'EXTRA_VALUE'")) {
					assertTrue(rows.next());
					assertEquals(320, rows.getInt(1));
				}
				Table beforeRemoval = reader.getAllFull(connection).get(0);
				Table ordinary = beforeRemoval.clone();
				ordinary.getTemporalPeriods().clear();
				ordinary.setSystemVersioning(null);
				ordinary.getSpecifics().put("temporal_type", "NON_TEMPORAL_TABLE");
				ordinary.getColumns().get("VALID_FROM").setHidden(false);
				ordinary.getColumns().get("VALID_TO").setHidden(false);
				for (var operation : alterFactory.createDiffSql(beforeRemoval.diff(ordinary))) sql.execute(operation.getSqlText());
				Table readOrdinary = reader.getAllFull(connection).get(0);
				assertTrue(readOrdinary.getTemporalPeriods().isEmpty());
				assertNull(readOrdinary.getSystemVersioning());
				Table temporalAgain = readOrdinary.clone();
				temporalAgain.setTemporalPeriods(beforeRemoval.getTemporalPeriods().clone());
				temporalAgain.setSystemVersioning(beforeRemoval.getSystemVersioning().clone());
				temporalAgain.getSpecifics().put("temporal_type", "SYSTEM_VERSIONED_TEMPORAL_TABLE");
				for (var operation : alterFactory.createDiffSql(readOrdinary.diff(temporalAgain))) sql.execute(operation.getSqlText());
				assertTrue(reader.getAllFull(connection).get(0).getSystemVersioning().isEnable());
				try (var rows = sql.executeQuery("SELECT VALUE FROM dbo." + name + " WHERE ID=1")) {
					assertTrue(rows.next());
					assertEquals(20, rows.getInt(1));
				}
				try (var rows = sql.executeQuery("SELECT COUNT(*) FROM dbo." + history)) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
			} finally {
				sql.execute("ALTER TABLE dbo." + name + " SET (SYSTEM_VERSIONING = OFF)");
				sql.execute("DROP TABLE dbo." + name);
				sql.execute("DROP TABLE dbo." + history);
			}
		}
	}
}
