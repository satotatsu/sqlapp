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
	private static void verifyConstraintRoundTrip(Connection connection) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		String prefix = "CON_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String parent = prefix + "_P", child = prefix + "_C";
		try (var sql = connection.createStatement()) {
			try {
				sql.execute("CREATE TABLE dbo." + parent + " (A int NOT NULL, B int NOT NULL, CONSTRAINT " + prefix
						+ "_U UNIQUE(B,A))");
				sql.execute("CREATE TABLE dbo." + child + " (A int, B int, X int CONSTRAINT " + prefix
						+ "_COL CHECK NOT FOR REPLICATION(X > 0), Y int, CONSTRAINT " + prefix + "_TR CHECK (A >= 0))");
				sql.execute("ALTER TABLE dbo." + child + " NOCHECK CONSTRAINT " + prefix + "_COL");
				sql.execute("ALTER TABLE dbo." + child + " WITH NOCHECK ADD CONSTRAINT " + prefix + "_UN CHECK(Y > 0)");
				sql.execute("ALTER TABLE dbo." + child + " WITH NOCHECK ADD CONSTRAINT " + prefix
						+ "_FK FOREIGN KEY(B,A) REFERENCES dbo." + parent + "(B,A) NOT FOR REPLICATION");
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("dbo");
				reader.setCatalogName(connection.getCatalog());
				reader.setObjectName(child);
				AtomicInteger count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection,
						text -> text.contains("sys.foreign_keys") || text.contains("sys.check_constraints"), count);
				Table before = reader.getAllFull(counted).get(0);
				int single = count.get();
				assertTrue(single > 0);
				count.set(0);
				reader.setObjectName(null);
				reader.getAllFull(counted);
				assertEquals(single, count.get(), "Constraint query count must not grow with the table count");
				var fk = before.getConstraints().getForeignKeyConstraints().get(0);
				assertEquals("B", fk.getColumns().get(0).getName());
				assertEquals("A", fk.getColumns().get(1).getName());
				assertEquals("B", fk.getRelatedColumns().get(0).getName());
				Table restored = SchemaUtils.readXml(new java.io.StringReader(before.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				sql.execute("DROP TABLE dbo." + child);
				for (var operation : registry.createSql(restored, SqlType.CREATE)) {
					try {
						sql.execute(operation.getSqlText());
					} catch (java.sql.SQLException e) {
						throw new AssertionError(operation.getSqlText(), e);
					}
				}
				reader.setObjectName(child);
				Table after = reader.getAllFull(connection).get(0);
				for (var constraint : before.getConstraints()) {
					var actual = after.getConstraints().get(constraint.getName());
					assertNotNull(actual);
					assertEquals(constraint.isEnable(), actual.isEnable());
					assertEquals(constraint.getSpecifics().get("is_not_trusted"),
							actual.getSpecifics().get("is_not_trusted"));
					assertEquals(constraint.getSpecifics().get("is_not_for_replication"),
							actual.getSpecifics().get("is_not_for_replication"));
				}
				sql.execute("INSERT INTO dbo." + parent + " VALUES (1,2)");
				sql.execute("INSERT INTO dbo." + child + " VALUES (1,2,-1,1)");
				assertThrows(java.sql.SQLException.class,
						() -> sql.execute("INSERT INTO dbo." + child + " VALUES (1,2,-1,-1)"));
				assertThrows(java.sql.SQLException.class,
						() -> sql.execute("INSERT INTO dbo." + child + " VALUES (3,4,-1,1)"));
				Table trusted = after.clone();
				trusted.getConstraints().get(prefix + "_UN").getSpecifics().put("is_not_trusted", "false");
				com.sqlapp.data.db.sql.SqlFactory<Table> alter = registry.getSqlFactory(trusted, SqlType.ALTER);
				for (var operation : alter.createDiffSql(after.diff(trusted)))
					sql.execute(operation.getSqlText());
				Table verified = reader.getAllFull(connection).get(0);
				assertEquals("false",
						verified.getConstraints().get(prefix + "_UN").getSpecifics().get("is_not_trusted"));
				Table disabled = verified.clone();
				disabled.getConstraints().get(prefix + "_UN").setEnable(false);
				disabled.getConstraints().get(prefix + "_UN").getSpecifics().put("is_not_trusted", "true");
				for (var operation : alter.createDiffSql(verified.diff(disabled)))
					sql.execute(operation.getSqlText());
				assertFalse(reader.getAllFull(connection).get(0).getConstraints().get(prefix + "_UN").isEnable());
				sql.execute("INSERT INTO dbo." + child + " VALUES (1,2,-1,-1)");
				Table invalidTrusted = disabled.clone();
				invalidTrusted.getConstraints().get(prefix + "_UN").setEnable(true);
				invalidTrusted.getConstraints().get(prefix + "_UN").getSpecifics().put("is_not_trusted", "false");
				var validate = alter.createDiffSql(disabled.diff(invalidTrusted));
				assertThrows(java.sql.SQLException.class, () -> {
					for (var operation : validate)
						sql.execute(operation.getSqlText());
				});
			} finally {
				sql.execute("DROP TABLE IF EXISTS dbo." + child);
				sql.execute("DROP TABLE IF EXISTS dbo." + parent);
			}
		}
	}

	static void verify(Connection connection) throws Exception {
		verifyTemporal(connection);
		verifyPartitionFunctionChanges(connection);
		verifyPartitionBoundaryRoundTrip(connection);
		verifyConstraintRoundTrip(connection);
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
		boolean retentionSupported = dialect instanceof com.sqlapp.data.db.dialect.sqlserver.SqlServer2017;
		try (var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE dbo." + name + " (ID int NOT NULL PRIMARY KEY, VALUE int NOT NULL, "
					+ "VALID_FROM datetime2 GENERATED ALWAYS AS ROW START HIDDEN NOT NULL, "
					+ "VALID_TO datetime2 GENERATED ALWAYS AS ROW END HIDDEN NOT NULL, "
					+ "PERIOD FOR SYSTEM_TIME(VALID_FROM, VALID_TO)) "
					+ "WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = dbo." + history
					+ (retentionSupported ? ", HISTORY_RETENTION_PERIOD = 6 MONTHS" : "") + "))");
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
				if (retentionSupported)
					assertEquals("6 MONTHS", com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
							.getHistoryRetentionPeriod(original.getSystemVersioning()));
				Table restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(original, SqlType.DROP))
					sql.execute(operation.getSqlText());
				// Keep the existing history table and reconnect to it during recreation.
				for (var operation : registry.createSql(restored, SqlType.CREATE))
					sql.execute(operation.getSqlText());
				Table recreated = reader.getAllFull(connection).get(0);
				assertEquals(restored.getTemporalPeriods(), recreated.getTemporalPeriods());
				assertEquals(restored.getSystemVersioning(), recreated.getSystemVersioning());
				var altered = recreated.clone();
				altered.getColumns().get("VALID_FROM").setHidden(false);
				com.sqlapp.data.db.sql.SqlFactory<Table> alterFactory = registry.getSqlFactory(altered, SqlType.ALTER);
				for (var operation : alterFactory.createDiffSql(recreated.diff(altered)))
					sql.execute(operation.getSqlText());
				assertFalse(reader.getAllFull(connection).get(0).getColumns().get("VALID_FROM").isHidden());
				Table disabled = altered.clone();
				disabled.toSystemVersioning().setEnable(false);
				for (var operation : alterFactory.createDiffSql(altered.diff(disabled)))
					sql.execute(operation.getSqlText());
				Table readDisabled = reader.getAllFull(connection).get(0);
				assertFalse(readDisabled.getSystemVersioning().isEnable());
				Table reenabled = readDisabled.clone();
				reenabled.toSystemVersioning().setEnable(true).setHistoryTableSchemaName("dbo")
						.setHistoryTableName(history);
				if (retentionSupported)
					com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
							.historyRetentionPeriod(reenabled.toSystemVersioning(), "6 MONTHS");
				for (var operation : alterFactory.createDiffSql(readDisabled.diff(reenabled)))
					sql.execute(operation.getSqlText());
				assertEquals(history, reader.getAllFull(connection).get(0).getSystemVersioning().getHistoryTableName());
				if (retentionSupported) {
					Table beforeRetention = reader.getAllFull(connection).get(0);
					Table nextRetention = beforeRetention.clone();
					com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
							.historyRetentionPeriod(nextRetention.toSystemVersioning(), "9 MONTHS");
					for (var operation : alterFactory.createDiffSql(beforeRetention.diff(nextRetention)))
						sql.execute(operation.getSqlText());
					assertEquals("9 MONTHS", com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
							.getHistoryRetentionPeriod(reader.getAllFull(connection).get(0).getSystemVersioning()));
				}
				sql.execute("INSERT INTO dbo." + name + "(ID, VALUE) VALUES(1, 10)");
				sql.execute("UPDATE dbo." + name + " SET VALUE = 20 WHERE ID = 1");
				try (var rows = sql.executeQuery("SELECT COUNT(*) FROM dbo." + history)) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				Table beforeAdd = reader.getAllFull(connection).get(0);
				Table withColumn = beforeAdd.clone();
				if (retentionSupported)
					com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
							.historyRetentionPeriod(withColumn.toSystemVersioning(), "INFINITE");
				withColumn.getColumns().get("VALUE").setNotNull(false);
				withColumn.getColumns().get("VALID_FROM").setHidden(true);
				withColumn.getColumns().add(new Column("EXTRA_VALUE")
						.setDataType(com.sqlapp.data.db.datatype.DataType.NVARCHAR).setLength(80));
				for (var operation : alterFactory.createDiffSql(beforeAdd.diff(withColumn)))
					sql.execute(operation.getSqlText());
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
				try (var rows = sql.executeQuery("SELECT is_nullable FROM sys.columns WHERE object_id=OBJECT_ID('dbo."
						+ history + "') AND name='VALUE'")) {
					assertTrue(rows.next());
					assertTrue(rows.getBoolean(1));
				}
				assertTrue(reader.getAllFull(connection).get(0).getColumns().get("VALID_FROM").isHidden());
				Table beforeWiden = reader.getAllFull(connection).get(0);
				Table widened = beforeWiden.clone();
				widened.getColumns().get("EXTRA_VALUE").setLength(160);
				widened.getColumns().get("VALID_TO").setHidden(false);
				widened.getColumns()
						.add(new Column("EXTRA_SECOND").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
				for (var operation : alterFactory.createDiffSql(beforeWiden.diff(widened)))
					sql.execute(operation.getSqlText());
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
				for (var operation : alterFactory.createDiffSql(beforeRemoval.diff(ordinary)))
					sql.execute(operation.getSqlText());
				Table readOrdinary = reader.getAllFull(connection).get(0);
				assertTrue(readOrdinary.getTemporalPeriods().isEmpty());
				assertNull(readOrdinary.getSystemVersioning());
				Table temporalAgain = readOrdinary.clone();
				temporalAgain.setTemporalPeriods(beforeRemoval.getTemporalPeriods().clone());
				temporalAgain.setSystemVersioning(beforeRemoval.getSystemVersioning().clone());
				temporalAgain.getSpecifics().put("temporal_type", "SYSTEM_VERSIONED_TEMPORAL_TABLE");
				for (var operation : alterFactory.createDiffSql(readOrdinary.diff(temporalAgain)))
					sql.execute(operation.getSqlText());
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

	private static void verifyPartitionFunctionChanges(Connection connection) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		var registry = dialect.createSqlFactoryRegistry();
		String name = "PF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String scheme = name + "_PS";
		PartitionFunction original = new PartitionFunction(name).setDataType(com.sqlapp.data.db.datatype.DataType.INT);
		original.getValues().addAll("100", "200");
		com.sqlapp.data.db.sql.SqlFactory<PartitionFunction> alter = registry.getSqlFactory(original, SqlType.ALTER);
		try (var sql = connection.createStatement()) {
			for (var operation : registry.createSql(original, SqlType.CREATE))
				sql.execute(operation.getSqlText());
			try {
				sql.execute("CREATE PARTITION SCHEME " + scheme + " AS PARTITION " + name + " ALL TO ([PRIMARY])");
				try {
					var schemeReader = dialect.getCatalogReader().getPartitionSchemeReader();
					schemeReader.setCatalogName(connection.getCatalog());
					schemeReader.setObjectName(scheme);
					PartitionScheme beforeNext = schemeReader.getAll(connection).get(0);
					PartitionScheme clearNext = beforeNext.clone();
					com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions.clearNextUsed(clearNext);
					com.sqlapp.data.db.sql.SqlFactory<PartitionScheme> schemeAlter = registry.getSqlFactory(clearNext,
							SqlType.ALTER);
					for (var operation : schemeAlter.createDiffSql(beforeNext.diff(clearNext)))
						sql.execute(operation.getSqlText());
					PartitionScheme cleared = schemeReader.getAll(connection).get(0);
					assertEquals("",
							com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions.getNextUsed(cleared));
					PartitionScheme next = cleared.clone();
					com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions.nextUsed(next, "PRIMARY");
					for (var operation : schemeAlter.createDiffSql(cleared.diff(next)))
						sql.execute(operation.getSqlText());
					assertEquals("PRIMARY", com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions
							.getNextUsed(schemeReader.getAll(connection).get(0)));
					PartitionFunction split = original.clone();
					split.getValues().add("150");
					for (var operation : alter.createDiffSql(original.diff(split)))
						sql.execute(operation.getSqlText());
					for (var operation : alter.createDiffSql(split.diff(original)))
						sql.execute(operation.getSqlText());
					var count = new AtomicInteger();
					var counted = CountingJdbc.wrap(connection, text -> text.contains("sys.partition_schemes"), count);
					schemeReader.getAll(counted);
					int single = count.get();
					count.set(0);
					schemeReader.setObjectName(null);
					schemeReader.getAll(counted);
					assertEquals(single, count.get());
					assertEquals(1, single);
					var drop = alter.createDiffSql(original.diff(null));
					assertEquals(1, drop.size());
					assertThrows(java.sql.SQLException.class, () -> sql.execute(drop.get(0).getSqlText()),
							"The function DROP must not implicitly delete a dependent scheme");
				} finally {
					for (var operation : registry.createSql(new PartitionScheme(scheme), SqlType.DROP))
						sql.execute(operation.getSqlText());
				}
				PartitionFunction target = original.clone();
				target.getValues().remove("100");
				target.getValues().add("300");
				for (var operation : alter.createDiffSql(original.diff(target)))
					sql.execute(operation.getSqlText());
				try (var rows = sql.executeQuery(
						"SELECT COUNT(*) FROM sys.partition_range_values v JOIN sys.partition_functions f "
								+ "ON f.function_id=v.function_id WHERE f.name='" + name
								+ "' AND CONVERT(int,v.value) IN (200,300)")) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
				}
				for (var operation : alter.createDiffSql(target.diff(null)))
					sql.execute(operation.getSqlText());
				registry.getOptions().setDropIfExists(true);
				for (var operation : registry.createSql(target, SqlType.DROP))
					sql.execute(operation.getSqlText());
			} finally {
				registry.getOptions().setDropIfExists(true);
				for (var operation : registry.createSql(new PartitionScheme(scheme), SqlType.DROP))
					sql.execute(operation.getSqlText());
				for (var operation : registry.createSql(original, SqlType.DROP))
					sql.execute(operation.getSqlText());
			}
		}
	}

	private static void verifyPartitionBoundaryRoundTrip(Connection connection) throws Exception {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDropIfExists(true);
		String prefix = "BND_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String[] names = { prefix + "_EMPTY", prefix + "_NULL", prefix + "_TEXT", prefix + "_BINARY",
				prefix + "_DECIMAL" };
		String[] definitions = { "(int) AS RANGE RIGHT FOR VALUES ()", "(int) AS RANGE RIGHT FOR VALUES (NULL,10)",
				"(nvarchar(80)) AS RANGE RIGHT FOR VALUES (N'日本 O''Brien  ')",
				"(varbinary(4)) AS RANGE RIGHT FOR VALUES (0x00ff27)",
				"(decimal(30,8)) AS RANGE RIGHT FOR VALUES (123.45678901)" };
		try (var sql = connection.createStatement()) {
			try {
				for (int i = 0; i < names.length; i++)
					sql.execute("CREATE PARTITION FUNCTION " + names[i] + definitions[i]);
				var reader = dialect.getCatalogReader().getPartitionFunctionReader();
				reader.setCatalogName(connection.getCatalog());
				reader.setObjectName(names[0]);
				var count = new AtomicInteger();
				var counted = CountingJdbc.wrap(connection, text -> text.contains("sys.partition_functions"), count);
				assertTrue(reader.getAll(counted).get(0).getValues().isEmpty());
				int single = count.get();
				count.set(0);
				reader.setObjectName(null);
				var functions = reader.getAll(counted);
				assertEquals(single, count.get());
				assertEquals(1, single);
				for (String name : names) {
					PartitionFunction original = functions.stream().filter(f -> f.getName().equals(name)).findFirst()
							.orElseThrow();
					if (name.endsWith("_NULL"))
						assertTrue(original.getValues().contains("NULL"));
					if (name.endsWith("_TEXT"))
						assertEquals("N'日本 O''Brien  '", original.getValues().get(0));
					if (name.endsWith("_DECIMAL"))
						assertEquals(30L, original.getLength());
					PartitionFunction restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
					for (var operation : registry.createSql(original, SqlType.DROP))
						sql.execute(operation.getSqlText());
					for (var operation : registry.createSql(restored, SqlType.CREATE))
						sql.execute(operation.getSqlText());
					reader.setObjectName(name);
					PartitionFunction reread = reader.getAll(connection).get(0);
					assertEquals(new java.util.HashSet<>(original.getValues()),
							new java.util.HashSet<>(reread.getValues()));
					assertEquals(original.getLength(), reread.getLength());
					assertEquals(original.getScale(), reread.getScale());
				}
			} finally {
				for (String name : names)
					for (var operation : registry.createSql(new PartitionFunction(name), SqlType.DROP))
						sql.execute(operation.getSqlText());
			}
		}
	}
}
