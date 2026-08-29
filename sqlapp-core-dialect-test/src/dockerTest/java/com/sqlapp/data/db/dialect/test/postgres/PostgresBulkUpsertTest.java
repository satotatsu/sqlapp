/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.postgres;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.dialect.test.FailingTransactionalCheckpointStore;
import com.sqlapp.data.db.dialect.test.BulkMigrationKeysetAssertions;
import com.sqlapp.data.db.dialect.test.BulkMigrationJobAssertions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;
import com.sqlapp.jdbc.bulk.BulkMigrationJobCheckpointManager;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;

/** Exercises COPY staging and ON CONFLICT against PostgreSQL 18. */
class PostgresBulkUpsertTest {
	private static final PostgreSQLContainer POSTGRES =
			ReusableTestcontainers.configure(new PostgreSQLContainer("postgres:18.4"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(POSTGRES);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(POSTGRES);
	}

	@Test
	void upgradesLegacyJdbcCheckpointTableThroughDialectAlterFactory() throws Exception {
		try (Connection connection = POSTGRES.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS \"SQLAPP_BMC_LEGACY_PG\"");
			statement.execute("CREATE TABLE \"SQLAPP_BMC_LEGACY_PG\" ("
					+ "\"MIGRATION_ID\" VARCHAR(255) NOT NULL PRIMARY KEY, "
					+ "\"SOURCE_FINGERPRINT\" VARCHAR(255), \"TARGET_FINGERPRINT\" VARCHAR(255), "
					+ "\"PROCESSED_ROWS\" NUMERIC(19,0) NOT NULL, "
					+ "\"COMPLETED_CHUNKS\" NUMERIC(19,0) NOT NULL, "
					+ "\"LAST_CHUNK_HASH\" VARCHAR(64), \"COMPLETE_FLAG\" CHAR(1) NOT NULL)");
			final var store = new JdbcBulkMigrationCheckpointStore(connection,
					"SQLAPP_BMC_LEGACY_PG");
			store.save(com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint.builder()
					.migrationId("legacy-pg").processedRows(1).completedChunks(1)
					.resumeToken("token-pg").complete(false).build());
			assertEquals("token-pg", store.load("legacy-pg").orElseThrow().getResumeToken());
		}
	}

	@Test
	void migratesParentBeforeChildAndAggregatesJdbcCheckpointStatus()
			throws Exception {
		try (Connection connection = POSTGRES.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS public.bulk_job_child");
			statement.execute("DROP TABLE IF EXISTS public.bulk_job_parent");
			statement.execute("CREATE TABLE public.bulk_job_parent "
					+ "(id INTEGER PRIMARY KEY, txt TEXT)");
			statement.execute("CREATE TABLE public.bulk_job_child "
					+ "(id INTEGER PRIMARY KEY, parent_id INTEGER NOT NULL "
					+ "REFERENCES public.bulk_job_parent(id), txt TEXT)");
			final Table parent = jobTable("bulk_job_parent", false);
			parent.getRows().add(row -> { row.put("id", 1); row.put("txt", "parent"); });
			final Table child = jobTable("bulk_job_child", true);
			child.getConstraints().addForeignKeyConstraint("bulk_job_child_parent_fk",
					new Column[] { child.getColumns().get("parent_id") },
					new Column[] { parent.getColumns().get("id") });
			child.getRows().add(row -> {
				row.put("id", 10); row.put("parent_id", 1); row.put("txt", "child");
			});

			BulkMigrationJobAssertions.assertDependencyOrderAndAggregatedStatus(
					connection, parent, child);
			assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM public.bulk_job_child c "
					+ "JOIN public.bulk_job_parent p ON p.id = c.parent_id"));
		}
	}

	@Test
	void inspectsAndResetsJdbcJobCheckpointWithoutRemovingUpsertedRows()
			throws Exception {
		try (Connection connection = POSTGRES.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS public.bulk_job_reset_target");
			statement.execute("CREATE TABLE public.bulk_job_reset_target "
					+ "(id INTEGER PRIMARY KEY, txt TEXT)");
			final Table source = repairTable("bulk_job_reset_target");
			source.getRows().add(row -> { row.put("id", 1); row.put("txt", "one"); });
			source.getRows().add(row -> { row.put("id", 2); row.put("txt", "two"); });
			final var options = ChunkedBulkMigrationOption.builder()
					.migrationId("postgres-job-reset-" + java.util.UUID.randomUUID())
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
					"SELECT COUNT(*) FROM public.bulk_job_reset_target"));

			assertEquals(2, BulkMigrationJobExecutor.executePlan(connection, plan)
					.getProcessedRows());
			assertEquals(2, scalar(statement,
					"SELECT COUNT(*) FROM public.bulk_job_reset_target"));
		}
	}

	@Test
	void updatesMatchesAndInsertsMissingRowsThroughCopyStaging()
			throws Exception {
		try (Connection connection = POSTGRES.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA bulk_upsert_test");
			statement.execute("""
					CREATE TABLE bulk_upsert_test.target (
					  id BIGINT GENERATED BY DEFAULT AS IDENTITY UNIQUE,
					  code TEXT PRIMARY KEY,
					  name TEXT,
					  amount NUMERIC(12,2),
					  payload BYTEA,
					  tags TEXT[]
					)
					""");
			statement.execute("""
					INSERT INTO bulk_upsert_test.target
					  (code, name, amount, payload, tags)
					VALUES ('A', 'old', 1.00, '\\x01', ARRAY['old'])
					""");

			final Table table = createTable();
			table.getRows().add(row -> {
				row.put("code", "A");
				row.put("name", "更新後\nline");
				row.put("amount", new BigDecimal("12.34"));
				row.put("payload", new byte[] { 0, (byte) 0xff });
				row.put("tags", new String[] { "one", "two,three" });
			});
			table.getRows().add(row -> {
				row.put("code", "B");
				row.put("name", null);
				row.put("amount", null);
			});
			table.getRows().add(row -> {
				row.put("code", "C");
				row.put("name", "");
				row.put("amount", new BigDecimal("0.00"));
				row.put("payload", new byte[] { 2 });
			});

			assertEquals(3, BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.defaults()));

			try (var resultSet = statement.executeQuery("SELECT id, code, name, "
					+ "amount, payload, tags FROM bulk_upsert_test.target "
					+ "ORDER BY code")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("id"));
				assertEquals("A", resultSet.getString("code"));
				assertEquals("更新後\nline", resultSet.getString("name"));
				assertEquals(new BigDecimal("12.34"),
						resultSet.getBigDecimal("amount"));
				assertArrayEquals(new byte[] { 0, (byte) 0xff },
						resultSet.getBytes("payload"));
				assertArrayEquals(new String[] { "one", "two,three" },
						(String[]) resultSet.getArray("tags").getArray());
				resultSet.next();
				assertEquals("B", resultSet.getString("code"));
				assertNull(resultSet.getString("name"));
				assertNull(resultSet.getBigDecimal("amount"));
				resultSet.next();
				assertEquals("C", resultSet.getString("code"));
				assertEquals("", resultSet.getString("name"));
				assertArrayEquals(new byte[] { 2 }, resultSet.getBytes("payload"));
			}
		}
	}

	@Test
	void resumesWithDatabaseCheckpointInTheTargetTransaction() throws Exception {
		try (Connection connection = POSTGRES.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS public.chunk_migration_target");
			statement.execute("CREATE TABLE public.chunk_migration_target "
					+ "(code TEXT PRIMARY KEY, name TEXT)");
			final Table table = new Table("chunk_migration_target").setSchemaName("public");
			final Column code = new Column("code").setDataType(DataType.LONGVARCHAR)
					.setNotNull(true);
			table.getColumns().add(code);
			table.getColumns().add(new Column("name").setDataType(DataType.LONGVARCHAR));
			table.setPrimaryKey("chunk_migration_target_pkey", code);
			for (int i = 1; i <= 3; i++) {
				final int value = i;
				table.getRows().add(row -> {
					row.put("code", "C" + value);
					row.put("name", "name-" + value);
				});
			}
			final String migrationId = "postgres-" + java.util.UUID.randomUUID();
			final var option = ChunkedBulkMigrationOption.builder()
					.migrationId(migrationId).chunkSize(2).build();
			final var checkpointStore = new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName());
			assertThrows(java.sql.SQLException.class,
					() -> ChunkedBulkMigrationExecutor.execute(connection, table, option,
							new FailingTransactionalCheckpointStore(connection, checkpointStore)));
			assertEquals(0, scalar(statement,
					"SELECT COUNT(*) FROM public.chunk_migration_target"));
			assertTrue(checkpointStore.load(migrationId).isEmpty());

			final var result = ChunkedBulkMigrationExecutor.execute(connection, table, option);
			assertEquals(3, result.getProcessedRows());
			assertEquals(2, result.getCompletedChunks());
			assertEquals(3, scalar(statement,
					"SELECT COUNT(*) FROM public.chunk_migration_target"));
			assertEquals(3, new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName()).load(migrationId).orElseThrow().getProcessedRows());
		}
	}

	@Test
	void readsAfterACompositeJdbcKeyset() throws Exception {
		try (Connection connection = POSTGRES.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS public.keyset_source");
			statement.execute("CREATE TABLE public.keyset_source ("
					+ "\"KEY1\" INTEGER NOT NULL, \"KEY2\" INTEGER NOT NULL, "
					+ "\"TXT\" TEXT, PRIMARY KEY (\"KEY1\", \"KEY2\"))");
			statement.executeUpdate("INSERT INTO public.keyset_source VALUES "
					+ "(1,1,'a'),(1,2,'b'),(2,1,'c'),(2,2,'d')");
			BulkMigrationKeysetAssertions.assertCompositeResume(connection,
					compositeKeysetTable("keyset_source", "public"));
		}
	}

	@Test
	void repairsOnlyMismatchedChunksAndReverifies() throws Exception {
		try (Connection connection = POSTGRES.createConnection("");
				var statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS public.bulk_repair_target");
			statement.execute("CREATE TABLE public.bulk_repair_target "
					+ "(id INTEGER PRIMARY KEY, txt TEXT)");
			statement.executeUpdate("INSERT INTO public.bulk_repair_target VALUES "
					+ "(1,'value-1'),(2,'value-2'),(3,'wrong'),(4,'value-4')");
			final Table expected = repairTable();
			final Table before = repairTable();
			for (int id = 1; id <= 4; id++) {
				final int value = id;
				expected.getRows().add(row -> {
					row.put("id", value);
					row.put("txt", "value-" + value);
				});
				before.getRows().add(row -> {
					row.put("id", value);
					row.put("txt", value == 3 ? "wrong" : "value-" + value);
				});
			}
			final var verification = BulkMigrationVerifier.verify(expected, before, 2);
			assertEquals(1, verification.getMismatches().size());
			final var repair = BulkMigrationRepairExecutor.execute(connection, expected,
					verification, BulkMigrationRepairOption.builder().build());
			assertEquals(1, repair.getReplayedChunks());
			assertEquals(2, repair.getReplayedRows());

			final Table after = repairTable();
			try (var resultSet = statement.executeQuery(
					"SELECT id, txt FROM public.bulk_repair_target ORDER BY id")) {
				while (resultSet.next()) {
					after.getRows().add(row -> {
						try {
							row.put("id", resultSet.getInt(1));
							row.put("txt", resultSet.getString(2));
						} catch (java.sql.SQLException e) {
							throw new IllegalStateException(e);
						}
					});
				}
			}
			assertTrue(BulkMigrationVerifier.verify(expected, after, 2).isMatch());
		}
	}

	private static int scalar(final java.sql.Statement statement, final String sql)
			throws java.sql.SQLException {
		try (var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	private static Table createTable() {
		final Table table = new Table("target").setSchemaName("bulk_upsert_test");
		final Column id = new Column("id").setDataType(DataType.BIGINT)
				.setIdentity(true);
		final Column code = new Column("code").setDataType(DataType.LONGVARCHAR)
				.setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(code);
		table.getColumns().add(new Column("name").setDataType(DataType.LONGVARCHAR));
		table.getColumns().add(new Column("amount").setDataType(DataType.DECIMAL)
				.setLength(12).setScale(2));
		table.getColumns().add(new Column("payload").setDataType(DataType.VARBINARY));
		table.getColumns().add(new Column("tags").setDataType(DataType.ARRAY));
		table.setPrimaryKey("target_pkey", code);
		return table;
	}

	private static Table compositeKeysetTable(final String name, final String schema) {
		final Table table = new Table(name).setSchemaName(schema);
		final Column key1 = new Column("KEY1").setDataType(DataType.INT).setNotNull(true);
		final Column key2 = new Column("KEY2").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(key1);
		table.getColumns().add(key2);
		table.getColumns().add(new Column("TXT").setDataType(DataType.LONGVARCHAR));
		table.setPrimaryKey("keyset_source_pkey", key1, key2);
		return table;
	}

	private static Table repairTable() {
		return repairTable("bulk_repair_target");
	}

	private static Table repairTable(final String name) {
		final Table table = new Table(name).setSchemaName("public");
		final Column id = new Column("id").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("txt").setDataType(DataType.LONGVARCHAR));
		table.setPrimaryKey(name + "_pkey", id);
		return table;
	}

	private static Table jobTable(final String name, final boolean child) {
		final Table table = repairTable(name);
		if (child) {
			table.getColumns().add(1,
					new Column("parent_id").setDataType(DataType.INT).setNotNull(true));
		}
		return table;
	}
}
