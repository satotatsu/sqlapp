/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.dialect.test.FailingTransactionalCheckpointStore;
import com.sqlapp.data.db.dialect.test.BulkMigrationKeysetAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationJobAssertions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkMigrationJobCheckpointManager;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;

/** Exercises staging-table bulk upsert against SQL Server 2022. */
class SqlServerBulkUpsertTest {
	private static final MSSQLServerContainer SQL_SERVER =
			ReusableTestcontainers.configure(new MSSQLServerContainer(
					"mcr.microsoft.com/mssql/server:2022-latest").acceptLicense());

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(SQL_SERVER);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SQL_SERVER);
	}

	@Test
	void migratesParentBeforeChildAndAggregatesJdbcCheckpointStatus()
			throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("IF OBJECT_ID('dbo.SQLAPP_BULK_JOB_CHILD') IS NOT NULL "
					+ "DROP TABLE dbo.SQLAPP_BULK_JOB_CHILD");
			statement.execute("IF OBJECT_ID('dbo.SQLAPP_BULK_JOB_PARENT') IS NOT NULL "
					+ "DROP TABLE dbo.SQLAPP_BULK_JOB_PARENT");
			statement.execute("CREATE TABLE dbo.SQLAPP_BULK_JOB_PARENT "
					+ "(ID INT NOT NULL PRIMARY KEY, TXT NVARCHAR(100))");
			statement.execute("CREATE TABLE dbo.SQLAPP_BULK_JOB_CHILD "
					+ "(ID INT NOT NULL PRIMARY KEY, PARENT_ID INT NOT NULL "
					+ "REFERENCES dbo.SQLAPP_BULK_JOB_PARENT(ID), TXT NVARCHAR(100))");
			final Table parent = jobTable("SQLAPP_BULK_JOB_PARENT", false);
			parent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "parent"); });
			final Table child = jobTable("SQLAPP_BULK_JOB_CHILD", true);
			child.getConstraints().addForeignKeyConstraint("FK_SQLAPP_BULK_JOB_CHILD_PARENT",
					new Column[] { child.getColumns().get("PARENT_ID") },
					new Column[] { parent.getColumns().get("ID") });
			child.getRows().add(row -> {
				row.put("ID", 10); row.put("PARENT_ID", 1); row.put("TXT", "child");
			});

			BulkMigrationJobAssertions.assertDependencyOrderAndAggregatedStatus(
					connection, parent, child);
			assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM dbo.SQLAPP_BULK_JOB_CHILD c "
					+ "JOIN dbo.SQLAPP_BULK_JOB_PARENT p ON p.ID = c.PARENT_ID"));
		}
	}

	@Test
	void inspectsAndResetsJdbcJobCheckpointWithoutRemovingUpsertedRows()
			throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("IF OBJECT_ID('dbo.SQLAPP_BULK_JOB_RESET_TARGET') IS NOT NULL "
					+ "DROP TABLE dbo.SQLAPP_BULK_JOB_RESET_TARGET");
			statement.execute("CREATE TABLE dbo.SQLAPP_BULK_JOB_RESET_TARGET "
					+ "(ID INT NOT NULL PRIMARY KEY, TXT NVARCHAR(100))");
			final Table source = repairTable("SQLAPP_BULK_JOB_RESET_TARGET");
			source.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "one"); });
			source.getRows().add(row -> { row.put("ID", 2); row.put("TXT", "two"); });
			final var options = ChunkedBulkMigrationOption.builder()
					.migrationId("sqlserver-job-reset-" + java.util.UUID.randomUUID())
					.sourceFingerprint("source-v1").targetFingerprint("target-v1")
					.chunkSize(1).build();
			final var store = new JdbcBulkMigrationCheckpointStore(connection,
					options.getCheckpointTableName());
			final var task = BulkMigrationJobTask.builder().taskId("reset")
					.sourceTable(source).options(options).checkpointStore(store).build();
			final var plan = BulkMigrationJobPlanner.plan(List.of(task));

			assertEquals(2, BulkMigrationJobExecutor.executePlan(connection, plan)
					.getProcessedRows());
			assertEquals(BulkMigrationJobTaskState.COMPLETE,
					BulkMigrationJobStatusInspector.inspect(plan).getTasks().get(0).getState());

			assertEquals(List.of("reset"), BulkMigrationJobCheckpointManager
					.reset(plan, plan.getFingerprint()).getResetTaskIds());
			assertEquals(BulkMigrationJobTaskState.NOT_STARTED,
					BulkMigrationJobStatusInspector.inspect(plan).getTasks().get(0).getState());
			assertEquals(2, scalar(statement,
					"SELECT COUNT(*) FROM dbo.SQLAPP_BULK_JOB_RESET_TARGET"));

			assertEquals(2, BulkMigrationJobExecutor.executePlan(connection, plan)
					.getProcessedRows());
			assertEquals(2, scalar(statement,
					"SELECT COUNT(*) FROM dbo.SQLAPP_BULK_JOB_RESET_TARGET"));
		}
	}

	@Test
	void updatesMatchesAndInsertsMissingRowsThroughBulkStaging()
			throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(),
				SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE dbo.SQLAPP_BULK_UPSERT_TEST (
					  ID BIGINT IDENTITY(1,1) NOT NULL UNIQUE,
					  CODE NVARCHAR(20) NOT NULL PRIMARY KEY,
					  NAME NVARCHAR(100) NULL,
					  AMOUNT DECIMAL(12,2) NULL,
					  PAYLOAD VARBINARY(20) NULL
					)
					""");
			statement.execute("""
					INSERT INTO dbo.SQLAPP_BULK_UPSERT_TEST
					  (CODE, NAME, AMOUNT, PAYLOAD)
					VALUES ('A', 'old', 1.00, 0x01)
					""");

			final Table table = createTable();
			table.getRows().add(row -> {
				row.put("CODE", "A");
				row.put("NAME", "更新後");
				row.put("AMOUNT", new BigDecimal("12.34"));
				row.put("PAYLOAD", new byte[] { 0, (byte) 0xff });
			});
			table.getRows().add(row -> {
				row.put("CODE", "B");
				row.put("NAME", null);
				row.put("AMOUNT", null);
			});
			table.getRows().add(row -> {
				row.put("CODE", "C");
				row.put("NAME", "");
				row.put("AMOUNT", new BigDecimal("0.00"));
				row.put("PAYLOAD", new byte[] { 2 });
			});
			table.getRows().add(row -> { row.put("CODE", "D"); row.put("NAME", "first"); });
			table.getRows().add(row -> { row.put("CODE", "D"); row.put("NAME", "discarded"); });

			assertEquals(4, BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder()
							.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.KEEP_FIRST)
							.bulkOption(BulkOption.builder().batchSize(2).build())
							.build()));

			try (var resultSet = statement.executeQuery("SELECT ID, CODE, NAME, "
					+ "AMOUNT, PAYLOAD FROM dbo.SQLAPP_BULK_UPSERT_TEST "
					+ "ORDER BY CODE")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("ID"));
				assertEquals("A", resultSet.getString("CODE"));
				assertEquals("更新後", resultSet.getString("NAME"));
				assertEquals(new BigDecimal("12.34"),
						resultSet.getBigDecimal("AMOUNT"));
				assertArrayEquals(new byte[] { 0, (byte) 0xff },
						resultSet.getBytes("PAYLOAD"));
				resultSet.next();
				assertEquals("B", resultSet.getString("CODE"));
				assertNull(resultSet.getString("NAME"));
				assertNull(resultSet.getBigDecimal("AMOUNT"));
				resultSet.next();
				assertEquals("C", resultSet.getString("CODE"));
				assertEquals("", resultSet.getString("NAME"));
				assertArrayEquals(new byte[] { 2 }, resultSet.getBytes("PAYLOAD"));
				resultSet.next();
				assertEquals("D", resultSet.getString("CODE"));
				assertEquals("first", resultSet.getString("NAME"));
			}
		}
	}

	@Test
	void resumesWithDatabaseCheckpointInTheTargetTransaction() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("IF OBJECT_ID('dbo.SQLAPP_CHUNK_MIGRATION_TARGET') IS NOT NULL "
					+ "DROP TABLE dbo.SQLAPP_CHUNK_MIGRATION_TARGET");
			statement.execute("CREATE TABLE dbo.SQLAPP_CHUNK_MIGRATION_TARGET "
					+ "(CODE NVARCHAR(20) NOT NULL PRIMARY KEY, NAME NVARCHAR(100))");
			final Table table = new Table("SQLAPP_CHUNK_MIGRATION_TARGET").setSchemaName("dbo");
			final Column code = new Column("CODE").setDataType(DataType.NVARCHAR)
					.setLength(20).setNotNull(true);
			table.getColumns().add(code);
			table.getColumns().add(new Column("NAME").setDataType(DataType.NVARCHAR)
					.setLength(100));
			table.setPrimaryKey("PK_SQLAPP_CHUNK_MIGRATION_TARGET", code);
			for (int i = 1; i <= 3; i++) {
				final int value = i;
				table.getRows().add(row -> {
					row.put("CODE", "C" + value);
					row.put("NAME", "name-" + value);
				});
			}
			final String migrationId = "sqlserver-" + java.util.UUID.randomUUID();
			final var option = ChunkedBulkMigrationOption.builder()
					.migrationId(migrationId).chunkSize(2).build();
			final var checkpointStore = new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName());
			assertThrows(java.sql.SQLException.class,
					() -> ChunkedBulkMigrationExecutor.execute(connection, table, option,
							new FailingTransactionalCheckpointStore(connection, checkpointStore)));
			try (var resultSet = statement.executeQuery(
					"SELECT COUNT(*) FROM dbo.SQLAPP_CHUNK_MIGRATION_TARGET")) {
				resultSet.next();
				assertEquals(0, resultSet.getInt(1));
			}
			assertTrue(checkpointStore.load(migrationId).isEmpty());

			final var result = ChunkedBulkMigrationExecutor.execute(connection, table, option);
			assertEquals(3, result.getProcessedRows());
			assertEquals(2, result.getCompletedChunks());
			try (var resultSet = statement.executeQuery(
					"SELECT COUNT(*) FROM dbo.SQLAPP_CHUNK_MIGRATION_TARGET")) {
				resultSet.next();
				assertEquals(3, resultSet.getInt(1));
			}
			assertEquals(3, new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName()).load(migrationId).orElseThrow().getProcessedRows());
		}
	}

	@Test
	void readsAfterACompositeJdbcKeyset() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("IF OBJECT_ID('dbo.SQLAPP_KEYSET_SOURCE') IS NOT NULL "
					+ "DROP TABLE dbo.SQLAPP_KEYSET_SOURCE");
			statement.execute("CREATE TABLE dbo.SQLAPP_KEYSET_SOURCE ("
					+ "KEY1 INT NOT NULL, KEY2 INT NOT NULL, TXT NVARCHAR(20), "
					+ "PRIMARY KEY (KEY1, KEY2))");
			statement.executeUpdate("INSERT INTO dbo.SQLAPP_KEYSET_SOURCE VALUES "
					+ "(1,1,'a'),(1,2,'b'),(2,1,'c'),(2,2,'d')");
			BulkMigrationKeysetAssertions.assertCompositeResume(connection,
					compositeKeysetTable());
		}
	}

	@Test
	void repairsOnlyMismatchedChunksAndReverifies() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("IF OBJECT_ID('dbo.SQLAPP_BULK_REPAIR_TARGET') IS NOT NULL "
					+ "DROP TABLE dbo.SQLAPP_BULK_REPAIR_TARGET");
			statement.execute("CREATE TABLE dbo.SQLAPP_BULK_REPAIR_TARGET "
					+ "(ID INT NOT NULL PRIMARY KEY, TXT NVARCHAR(100))");
			statement.executeUpdate("INSERT INTO dbo.SQLAPP_BULK_REPAIR_TARGET VALUES "
					+ "(1,'value-1'),(2,'value-2'),(3,'wrong'),(4,'value-4')");
			final Table expected = repairTable();
			final Table before = repairTable();
			for (int id = 1; id <= 4; id++) {
				final int value = id;
				expected.getRows().add(row -> {
					row.put("ID", value);
					row.put("TXT", "value-" + value);
				});
				before.getRows().add(row -> {
					row.put("ID", value);
					row.put("TXT", value == 3 ? "wrong" : "value-" + value);
				});
			}
			final var verification = BulkMigrationVerifier.verify(expected, before, 2);
			assertEquals(1, verification.getMismatches().size());
			final var repair = BulkMigrationRepairExecutor.execute(connection, expected,
					verification, BulkMigrationRepairOption.builder().chunkSize(2).build());
			assertEquals(1, repair.getReplayedChunks());
			assertEquals(2, repair.getReplayedRows());

			final Table after = repairTable();
			try (var resultSet = statement.executeQuery(
					"SELECT ID, TXT FROM dbo.SQLAPP_BULK_REPAIR_TARGET ORDER BY ID")) {
				while (resultSet.next()) {
					after.getRows().add(row -> {
						try {
							row.put("ID", resultSet.getInt(1));
							row.put("TXT", resultSet.getString(2));
						} catch (java.sql.SQLException e) {
							throw new IllegalStateException(e);
						}
					});
				}
			}
			assertTrue(BulkMigrationVerifier.verify(expected, after, 2).isMatch());
		}
	}

	private static Table createTable() {
		final Table table = new Table("SQLAPP_BULK_UPSERT_TEST");
		table.setSchemaName("dbo");
		final Column id = new Column("ID").setDataType(DataType.BIGINT)
				.setIdentity(true);
		final Column code = new Column("CODE").setDataType(DataType.NVARCHAR)
				.setLength(20).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(code);
		table.getColumns().add(new Column("NAME").setDataType(DataType.NVARCHAR)
				.setLength(100));
		table.getColumns().add(new Column("AMOUNT").setDataType(DataType.DECIMAL)
				.setLength(12).setScale(2));
		table.getColumns().add(new Column("PAYLOAD")
				.setDataType(DataType.VARBINARY).setLength(20));
		table.setPrimaryKey("PK_SQLAPP_BULK_UPSERT_TEST", code);
		return table;
	}

	private static Table compositeKeysetTable() {
		final Table table = new Table("SQLAPP_KEYSET_SOURCE").setSchemaName("dbo");
		final Column key1 = new Column("KEY1").setDataType(DataType.INT).setNotNull(true);
		final Column key2 = new Column("KEY2").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(key1);
		table.getColumns().add(key2);
		table.getColumns().add(new Column("TXT").setDataType(DataType.NVARCHAR).setLength(20));
		table.setPrimaryKey("PK_SQLAPP_KEYSET_SOURCE", key1, key2);
		return table;
	}

	private static Table repairTable() {
		return repairTable("SQLAPP_BULK_REPAIR_TARGET");
	}

	private static Table repairTable(final String name) {
		final Table table = new Table(name).setSchemaName("dbo");
		final Column id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("TXT").setDataType(DataType.NVARCHAR).setLength(100));
		table.setPrimaryKey("PK_" + name, id);
		return table;
	}

	private static Table jobTable(final String name, final boolean child) {
		final Table table = repairTable(name);
		if (child) {
			table.getColumns().add(1, new Column("PARENT_ID").setDataType(DataType.INT)
					.setNotNull(true));
		}
		return table;
	}

	private static int scalar(final java.sql.Statement statement, final String sql)
			throws java.sql.SQLException {
		try (var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}
}
