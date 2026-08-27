/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.BulkMigrationJobException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairExecutor;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairOption;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.TransactionalBulkMigrationCheckpointStore;

class ChunkedBulkMigrationTest {
	@Test
	void reportsChunkProgressOnlyAfterDurableCheckpoint() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CHUNK_PROGRESS (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table source = table("CHUNK_PROGRESS");
			for (int i = 1; i <= 3; i++) {
				final int id = i;
				source.getRows().add(row -> { row.put("ID", id); row.put("TXT", "row" + id); });
			}
			final List<String> events = new ArrayList<>();
			final var listener = new ChunkedBulkMigrationListener() {
				@Override
				public void onChunkStarted(ChunkedBulkMigrationProgress progress) {
					events.add("start:" + progress.getChunkIndex() + ":" + progress.getChunkRows()
							+ ":" + progress.getProcessedRowsBefore() + ":"
							+ progress.getProcessedRowsAfter());
				}

				@Override
				public void onChunkCompleted(ChunkedBulkMigrationProgress progress) {
					events.add("complete:" + progress.getChunkIndex());
				}
			};

			ChunkedBulkMigrationExecutor.executeWithListener(connection, source,
					ChunkedBulkMigrationOption.builder().migrationId("chunk-progress")
							.chunkSize(2).build(), listener);

			assertEquals(List.of("start:0:2:0:2", "complete:0",
					"start:1:1:2:3", "complete:1"), events);
		}
	}

	@Test
	void databaseCheckpointIsDefaultAndRollsBackDataWithCheckpoint() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ATOMIC_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table source = table("ATOMIC_TARGET");
			source.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "one"); });
			source.getRows().add(row -> { row.put("ID", 2); row.put("TXT", "two"); });
			final var option = ChunkedBulkMigrationOption.builder()
					.migrationId("atomic-migration").chunkSize(2).build();
			final var jdbcStore = new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName());
			final TransactionalBulkMigrationCheckpointStore failing =
					new FailSaveJdbcStore(jdbcStore, connection);

			assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(
					connection, source, option, failing));
			try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM ATOMIC_TARGET")) {
				resultSet.next();
				assertEquals(0, resultSet.getInt(1));
			}
			assertTrue(jdbcStore.load("atomic-migration").isEmpty());

			final var result = ChunkedBulkMigrationExecutor.execute(connection, source, option);
			assertEquals(2, result.getProcessedRows());
			assertTrue(new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName()).load("atomic-migration").orElseThrow().isComplete());
		}
	}

	@Test
	void resumesAfterCheckpointFailureAndVerifiesChunks() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CHUNK_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table source = table("CHUNK_TARGET");
			for (int i = 1; i <= 5; i++) {
				final int id = i;
				source.getRows().add(row -> {
					row.put("ID", id);
					row.put("TXT", "value-" + id);
				});
			}
			final InMemoryBulkMigrationCheckpointStore delegate =
					new InMemoryBulkMigrationCheckpointStore();
			final BulkMigrationCheckpointStore failing = new FailSecondSaveStore(delegate);
			final ChunkedBulkMigrationOption option = ChunkedBulkMigrationOption.builder()
					.migrationId("test-migration").chunkSize(2)
					.checkpointMode(BulkMigrationCheckpointMode.FILE)
					.sourceFingerprint("source-v1").targetFingerprint("target-v1").build();

			assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(
					connection, source, option, failing));
			assertEquals(2, delegate.load("test-migration").orElseThrow().getProcessedRows());

			final var resumed = ChunkedBulkMigrationExecutor.execute(connection, source,
					option, delegate);
			assertEquals(2, resumed.getPreviouslyProcessedRows());
			assertEquals(3, resumed.getProcessedRows());
			assertEquals(3, resumed.getCompletedChunks());
			assertTrue(delegate.load("test-migration").orElseThrow().isComplete());
			final var completed = ChunkedBulkMigrationExecutor.execute(connection, source,
					option, delegate);
			assertTrue(completed.isAlreadyComplete());
			assertEquals(0, completed.getProcessedRows());
			try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM CHUNK_TARGET")) {
				resultSet.next();
				assertEquals(5, resultSet.getInt(1));
			}

			final Table actual = table("CHUNK_TARGET");
			try (var resultSet = statement.executeQuery("SELECT ID, TXT FROM CHUNK_TARGET ORDER BY ID")) {
				while (resultSet.next()) {
					actual.getRows().add(row -> {
						try {
							row.put("ID", resultSet.getInt(1));
							row.put("TXT", resultSet.getString(2));
						} catch (SQLException e) {
							throw new IllegalStateException(e);
						}
					});
				}
			}
			final var verified = BulkMigrationVerifier.verify(source, actual, 2);
			assertTrue(verified.isMatch());
			assertEquals(5, verified.getExpectedRows());
			assertEquals(3, verified.getChunks().size());

			actual.getRows().get(3).put("TXT", "different");
			final var mismatch = BulkMigrationVerifier.verify(source, actual, 2);
			assertFalse(mismatch.isMatch());
			assertEquals(1, mismatch.getMismatches().size());
			assertEquals(1, mismatch.getMismatches().get(0).getIndex());
		}
	}

	@Test
	void rejectsCheckpointForDifferentSource() throws Exception {
		final var store = new InMemoryBulkMigrationCheckpointStore();
		store.save(BulkMigrationCheckpoint.builder().migrationId("migration")
				.sourceFingerprint("old").targetFingerprint("target").build());
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			assertThrows(IllegalArgumentException.class,
					() -> ChunkedBulkMigrationExecutor.execute(connection, table("missing"),
							ChunkedBulkMigrationOption.builder().migrationId("migration")
									.checkpointMode(BulkMigrationCheckpointMode.CUSTOM)
									.sourceFingerprint("new").targetFingerprint("target").build(),
							store));
		}
	}

	@Test
	void keysetResumeIsNotShiftedByRowsInsertedBeforeTheCursor() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE KEYSET_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final var source = new IntegerKeysetSource("KEYSET_TARGET", 1, 2, 3, 4, 5);
			final var delegate = new InMemoryBulkMigrationCheckpointStore();
			final var option = ChunkedBulkMigrationOption.builder()
					.migrationId("keyset-migration").chunkSize(2)
					.checkpointMode(BulkMigrationCheckpointMode.FILE).build();

			assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(
					connection, source, option, new FailSecondSaveStore(delegate)));
			assertEquals("2", delegate.load("keyset-migration").orElseThrow().getResumeToken());

			// Count resume would shift when ID 0 is inserted. Keyset resumes after ID 2.
			source.setIds(0, 1, 2, 3, 4, 5);
			final var resumed = ChunkedBulkMigrationExecutor.execute(connection, source, option, delegate);
			assertEquals(3, resumed.getProcessedRows());
			assertEquals("5", delegate.load("keyset-migration").orElseThrow().getResumeToken());
			try (var resultSet = statement.executeQuery("SELECT GROUP_CONCAT(ID, ',') FROM "
					+ "(SELECT ID FROM KEYSET_TARGET ORDER BY ID)")) {
				resultSet.next();
				assertEquals("1,2,3,4,5", resultSet.getString(1));
			}
		}
	}

	@Test
	void upgradesAnExistingCountCheckpointTableForKeysetTokens() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE OLD_CHECKPOINT ("
					+ "migration_id VARCHAR(255) NOT NULL PRIMARY KEY, "
					+ "source_fingerprint VARCHAR(255), target_fingerprint VARCHAR(255), "
					+ "processed_rows DECIMAL(19, 0) NOT NULL, "
					+ "completed_chunks DECIMAL(19, 0) NOT NULL, "
					+ "last_chunk_hash VARCHAR(64), complete_flag CHAR(1) NOT NULL)");
			final var store = new JdbcBulkMigrationCheckpointStore(connection, "OLD_CHECKPOINT");
			final var checkpoint = BulkMigrationCheckpoint.builder().migrationId("keyset")
					.processedRows(2).completedChunks(1).resumeToken("2").build();
			store.save(checkpoint);
			assertEquals(checkpoint, store.load("keyset").orElseThrow());
		}
	}

	@Test
	void jdbcKeysetSourceResumesAfterACompositeKey() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE COMPOSITE_SOURCE ("
					+ "KEY1 INTEGER NOT NULL, KEY2 INTEGER NOT NULL, TXT TEXT, "
					+ "PRIMARY KEY (KEY1, KEY2))");
			statement.executeUpdate("INSERT INTO COMPOSITE_SOURCE VALUES "
					+ "(1, 1, 'a'), (1, 2, 'b'), (2, 1, 'c'), (2, 2, 'd')");
			final Table table = new Table("COMPOSITE_SOURCE");
			final Column key1 = new Column("KEY1").setDataType(DataType.INT).setNotNull(true);
			final Column key2 = new Column("KEY2").setDataType(DataType.INT).setNotNull(true);
			table.getColumns().add(key1);
			table.getColumns().add(key2);
			table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR));
			table.setPrimaryKey("PK_COMPOSITE_SOURCE", key1, key2);
			final var source = new JdbcBulkMigrationKeysetSource(connection, table);

			final Iterator<Row> all = source.iterator(null);
			assertEquals(Integer.valueOf(1), all.next().get("KEY1"));
			final Row cursorRow = all.next();
			assertEquals(Integer.valueOf(2), cursorRow.get("KEY2"));
			if (all instanceof AutoCloseable closeable) {
				closeable.close();
			}

			final Iterator<Row> resumed = source.iterator(source.resumeToken(cursorRow));
			final Row first = resumed.next();
			assertEquals(Integer.valueOf(2), first.get("KEY1"));
			assertEquals(Integer.valueOf(1), first.get("KEY2"));
			assertEquals("d", resumed.next().get("TXT"));
			assertFalse(resumed.hasNext());
		}
	}

	@Test
	void repairsOnlyExpectedRowsFromMismatchedChunksAndReverifies() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE REPAIR_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table expected = table("REPAIR_TARGET");
			final Table actual = table("REPAIR_TARGET");
			for (int id = 1; id <= 4; id++) {
				final int value = id;
				expected.getRows().add(row -> {
					row.put("ID", value);
					row.put("TXT", "value-" + value);
				});
				actual.getRows().add(row -> {
					row.put("ID", value);
					row.put("TXT", value == 3 ? "wrong" : "value-" + value);
				});
			}
			statement.executeUpdate("INSERT INTO REPAIR_TARGET VALUES "
					+ "(1,'value-1'),(2,'value-2'),(3,'wrong'),(4,'value-4')");
			final var verification = BulkMigrationVerifier.verify(expected, actual, 2);
			assertEquals(1, verification.getMismatches().size());
			assertEquals(1, verification.getMismatches().get(0).getIndex());

			final var repaired = BulkMigrationRepairExecutor.execute(connection, expected,
					verification, BulkMigrationRepairOption.builder().chunkSize(2).build());
			assertEquals(1, repaired.getReplayedChunks());
			assertEquals(2, repaired.getReplayedRows());
			assertFalse(repaired.requiresManualReconciliation());

			final Table after = table("REPAIR_TARGET");
			try (var resultSet = statement.executeQuery(
					"SELECT ID, TXT FROM REPAIR_TARGET ORDER BY ID")) {
				while (resultSet.next()) {
					after.getRows().add(row -> {
						try {
							row.put("ID", resultSet.getInt(1));
							row.put("TXT", resultSet.getString(2));
						} catch (SQLException e) {
							throw new IllegalStateException(e);
						}
					});
				}
			}
			assertTrue(BulkMigrationVerifier.verify(expected, after, 2).isMatch());
		}
	}

	@Test
	void rejectsRepairWhenExpectedRowsChangedAfterVerification() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CHANGED_REPAIR_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table expected = table("CHANGED_REPAIR_TARGET");
			final Table actual = table("CHANGED_REPAIR_TARGET");
			expected.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "expected"); });
			actual.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "wrong"); });
			statement.executeUpdate("INSERT INTO CHANGED_REPAIR_TARGET VALUES (1,'wrong')");
			final var verification = BulkMigrationVerifier.verify(expected, actual, 1);
			expected.getRows().get(0).put("TXT", "changed-after-verification");

			assertThrows(IllegalStateException.class, () -> BulkMigrationRepairExecutor.execute(
					connection, expected, verification,
					BulkMigrationRepairOption.builder().chunkSize(1).build()));
			try (var resultSet = statement.executeQuery(
					"SELECT TXT FROM CHANGED_REPAIR_TARGET WHERE ID = 1")) {
				resultSet.next();
				assertEquals("wrong", resultSet.getString(1));
			}
		}
	}

	@Test
	void repairReportsTargetOnlyRowsWithoutDeletingThem() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE EXTRA_REPAIR_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table expected = table("EXTRA_REPAIR_TARGET");
			final Table actual = table("EXTRA_REPAIR_TARGET");
			expected.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "one"); });
			actual.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "wrong"); });
			actual.getRows().add(row -> { row.put("ID", 99); row.put("TXT", "target-only"); });
			statement.executeUpdate("INSERT INTO EXTRA_REPAIR_TARGET VALUES "
					+ "(1,'wrong'),(99,'target-only')");

			final var verification = BulkMigrationVerifier.verify(expected, actual, 10);
			final var result = BulkMigrationRepairExecutor.execute(connection, expected,
					verification, BulkMigrationRepairOption.builder().chunkSize(10).build());
			assertTrue(result.requiresManualReconciliation());
			assertEquals(List.of(0L), result.getChunksWithExtraActualRows());
			try (var resultSet = statement.executeQuery(
					"SELECT COUNT(*) FROM EXTRA_REPAIR_TARGET WHERE ID = 99")) {
				resultSet.next();
				assertEquals(1, resultSet.getInt(1));
			}
		}
	}

	@Test
	void multiTableJobOrdersDependenciesAndResumesCompletedTasks() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("CREATE TABLE JOB_PARENT (ID INTEGER PRIMARY KEY, TXT TEXT)");
			statement.execute("CREATE TABLE JOB_CHILD (ID INTEGER PRIMARY KEY, PARENT_ID INTEGER, "
					+ "TXT TEXT, FOREIGN KEY (PARENT_ID) REFERENCES JOB_PARENT(ID))");
			final Table parent = jobTable("JOB_PARENT", false);
			parent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "parent"); });
			final Table child = jobTable("JOB_CHILD", true);
			child.getConstraints().addForeignKeyConstraint("FK_JOB_CHILD_PARENT",
					new Column[] { child.getColumns().get("PARENT_ID") },
					new Column[] { parent.getColumns().get("ID") });
			child.getRows().add(row -> {
				row.put("ID", 10);
				row.put("PARENT_ID", 1);
				row.put("TXT", "child");
			});
			final String suffix = java.util.UUID.randomUUID().toString();
			final var parentTask = BulkMigrationJobTask.builder().taskId("parent")
					.sourceTable(parent).options(ChunkedBulkMigrationOption.builder()
							.migrationId("job-parent-" + suffix).chunkSize(1).build()).build();
			final var childTask = BulkMigrationJobTask.builder().taskId("child")
					.sourceTable(child)
					.options(ChunkedBulkMigrationOption.builder()
							.migrationId("job-child-" + suffix).chunkSize(1).build()).build();

			final List<String> events = new ArrayList<>();
			final var first = BulkMigrationJobExecutor.execute(connection,
					List.of(childTask, parentTask), new BulkMigrationJobListener() {
						@Override
						public void onTaskStarted(String taskId, int taskIndex, int taskCount) {
							events.add("start:" + taskIndex + ":" + taskCount + ":" + taskId);
						}

						@Override
						public void onTaskCompleted(String taskId,
								ChunkedBulkMigrationResult result,
								int taskIndex, int taskCount) {
							events.add("complete:" + taskIndex + ":" + taskCount + ":" + taskId);
						}
					});
			assertEquals(List.of("parent", "child"), first.getTasks().stream()
					.map(result -> result.getTaskId()).toList());
			assertEquals(List.of("start:0:2:parent", "complete:0:2:parent",
					"start:1:2:child", "complete:1:2:child"), events);
			assertEquals(2, first.getProcessedRows());
			assertEquals(0, first.getAlreadyCompleteTasks());
			final var resumed = BulkMigrationJobExecutor.execute(connection,
					List.of(childTask, parentTask));
			assertEquals(0, resumed.getProcessedRows());
			assertEquals(2, resumed.getAlreadyCompleteTasks());
			try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM JOB_CHILD c "
					+ "JOIN JOB_PARENT p ON p.ID = c.PARENT_ID")) {
				resultSet.next();
				assertEquals(1, resultSet.getInt(1));
			}
		}
	}

	@Test
	void multiTableJobKeepsCompletedParentCheckpointAfterChildFailure() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("CREATE TABLE FAIL_JOB_PARENT (ID INTEGER PRIMARY KEY, TXT TEXT)");
			statement.execute("CREATE TABLE FAIL_JOB_CHILD (ID INTEGER PRIMARY KEY, PARENT_ID INTEGER, "
					+ "TXT TEXT, FOREIGN KEY (PARENT_ID) REFERENCES FAIL_JOB_PARENT(ID))");
			final Table parent = jobTable("FAIL_JOB_PARENT", false);
			parent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "parent"); });
			final Table child = jobTable("FAIL_JOB_CHILD", true);
			child.getConstraints().addForeignKeyConstraint("FK_FAIL_JOB_CHILD_PARENT",
					new Column[] { child.getColumns().get("PARENT_ID") },
					new Column[] { parent.getColumns().get("ID") });
			child.getRows().add(row -> {
				row.put("ID", 10);
				row.put("PARENT_ID", 999);
				row.put("TXT", "child");
			});
			final List<String> events = new ArrayList<>();
			final String suffix = java.util.UUID.randomUUID().toString();
			final var parentTask = BulkMigrationJobTask.builder().taskId("parent")
					.sourceTable(parent).options(ChunkedBulkMigrationOption.builder()
							.migrationId("fail-job-parent-" + suffix).chunkSize(1).build()).build();
			final var childTask = BulkMigrationJobTask.builder().taskId("child")
					.sourceTable(child).options(ChunkedBulkMigrationOption.builder()
							.migrationId("fail-job-child-" + suffix).chunkSize(1).build())
					.chunkListener(new ChunkedBulkMigrationListener() {
						@Override
						public void onChunkFailed(ChunkedBulkMigrationProgress progress, Throwable cause) {
							events.add("chunk-failed:" + progress.getChunkIndex());
						}
					}).build();

			final var failure = assertThrows(BulkMigrationJobException.class,
					() -> BulkMigrationJobExecutor.execute(connection,
							List.of(childTask, parentTask), new BulkMigrationJobListener() {
								@Override
								public void onTaskFailed(String taskId, SQLException cause,
										int taskIndex, int taskCount) {
									events.add(taskIndex + ":" + taskCount + ":" + taskId);
								}
							}));
			assertEquals("child", failure.getFailedTaskId());
			assertEquals(List.of("chunk-failed:0", "1:2:child"), events);
			assertEquals(List.of("parent"), failure.getCompletedResult().getTasks().stream()
					.map(result -> result.getTaskId()).toList());
			assertEquals(1, failure.getCompletedResult().getProcessedRows());
			assertTrue(failure.getCause() instanceof SQLException);
			child.getRows().get(0).put("PARENT_ID", 1);
			final var resumed = BulkMigrationJobExecutor.execute(connection,
					List.of(childTask, parentTask));
			assertTrue(resumed.getTasks().get(0).getMigrationResult().isAlreadyComplete());
			assertEquals(1, resumed.getProcessedRows());
			try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM FAIL_JOB_CHILD")) {
				resultSet.next();
				assertEquals(1, resultSet.getInt(1));
			}
		}
	}

	@Test
	void multiTableJobRepairsMismatchesInDependencyOrder() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("CREATE TABLE REPAIR_JOB_PARENT (ID INTEGER PRIMARY KEY, TXT TEXT)");
			statement.execute("CREATE TABLE REPAIR_JOB_CHILD (ID INTEGER PRIMARY KEY, PARENT_ID INTEGER, "
					+ "TXT TEXT, FOREIGN KEY (PARENT_ID) REFERENCES REPAIR_JOB_PARENT(ID))");
			statement.execute("INSERT INTO REPAIR_JOB_PARENT VALUES (1, 'old parent')");
			statement.execute("INSERT INTO REPAIR_JOB_CHILD VALUES (10, 1, 'old child')");
			final Table expectedParent = jobTable("REPAIR_JOB_PARENT", false);
			expectedParent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "parent"); });
			final Table expectedChild = jobTable("REPAIR_JOB_CHILD", true);
			expectedChild.getConstraints().addForeignKeyConstraint("FK_REPAIR_JOB_CHILD_PARENT",
					new Column[] { expectedChild.getColumns().get("PARENT_ID") },
					new Column[] { expectedParent.getColumns().get("ID") });
			expectedChild.getRows().add(row -> {
				row.put("ID", 10); row.put("PARENT_ID", 1); row.put("TXT", "child");
			});
			final Table actualParent = jobTable("REPAIR_JOB_PARENT", false);
			actualParent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "old parent"); });
			final Table actualChild = jobTable("REPAIR_JOB_CHILD", true);
			actualChild.getRows().add(row -> {
				row.put("ID", 10); row.put("PARENT_ID", 1); row.put("TXT", "old child");
			});
			final var option = BulkMigrationRepairOption.builder().chunkSize(1).build();
			final var parentTask = BulkMigrationJobRepairTask.builder().taskId("parent")
					.expected(expectedParent)
					.verificationResult(BulkMigrationVerifier.verify(expectedParent, actualParent, 1))
					.options(option).build();
			final var childTask = BulkMigrationJobRepairTask.builder().taskId("child")
					.expected(expectedChild)
					.verificationResult(BulkMigrationVerifier.verify(expectedChild, actualChild, 1))
					.options(option).build();

			final var repaired = BulkMigrationJobRepairExecutor.execute(connection,
					List.of(childTask, parentTask));

			assertEquals(List.of("parent", "child"), repaired.getTasks().stream()
					.map(result -> result.getTaskId()).toList());
			assertEquals(2, repaired.getMismatchChunks());
			assertEquals(2, repaired.getReplayedChunks());
			assertEquals(2, repaired.getReplayedRows());
			assertFalse(repaired.requiresManualReconciliation());
			try (var resultSet = statement.executeQuery("SELECT p.TXT, c.TXT FROM REPAIR_JOB_PARENT p "
					+ "JOIN REPAIR_JOB_CHILD c ON c.PARENT_ID = p.ID")) {
				resultSet.next();
				assertEquals("parent", resultSet.getString(1));
				assertEquals("child", resultSet.getString(2));
			}
		}
	}

	@Test
	void multiTableJobRepairReportsFailedTaskAndCompletedResults() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE REPAIR_FAIL_PARENT (ID INTEGER PRIMARY KEY, TXT TEXT)");
			statement.execute("INSERT INTO REPAIR_FAIL_PARENT VALUES (1, 'old')");
			final Table expectedParent = jobTable("REPAIR_FAIL_PARENT", false);
			expectedParent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "new"); });
			final Table actualParent = jobTable("REPAIR_FAIL_PARENT", false);
			actualParent.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "old"); });
			final Table expectedChild = jobTable("REPAIR_FAIL_MISSING_CHILD", true);
			expectedChild.getConstraints().addForeignKeyConstraint("FK_REPAIR_FAIL_CHILD_PARENT",
					new Column[] { expectedChild.getColumns().get("PARENT_ID") },
					new Column[] { expectedParent.getColumns().get("ID") });
			expectedChild.getRows().add(row -> {
				row.put("ID", 10); row.put("PARENT_ID", 1); row.put("TXT", "new");
			});
			final Table actualChild = jobTable("REPAIR_FAIL_MISSING_CHILD", true);
			actualChild.getRows().add(row -> {
				row.put("ID", 10); row.put("PARENT_ID", 1); row.put("TXT", "old");
			});
			final var option = BulkMigrationRepairOption.builder().chunkSize(1).build();
			final var parentTask = BulkMigrationJobRepairTask.builder().taskId("parent")
					.expected(expectedParent)
					.verificationResult(BulkMigrationVerifier.verify(expectedParent, actualParent, 1))
					.options(option).build();
			final var childTask = BulkMigrationJobRepairTask.builder().taskId("child")
					.expected(expectedChild)
					.verificationResult(BulkMigrationVerifier.verify(expectedChild, actualChild, 1))
					.options(option).build();

			final var failure = assertThrows(BulkMigrationJobRepairException.class,
					() -> BulkMigrationJobRepairExecutor.execute(connection,
							List.of(childTask, parentTask)));

			assertEquals("child", failure.getFailedTaskId());
			assertEquals(List.of("parent"), failure.getCompletedResult().getTasks().stream()
					.map(result -> result.getTaskId()).toList());
			assertEquals(1, failure.getCompletedResult().getReplayedRows());
			assertTrue(failure.getCause() instanceof SQLException);
			try (var resultSet = statement.executeQuery("SELECT TXT FROM REPAIR_FAIL_PARENT")) {
				resultSet.next();
				assertEquals("new", resultSet.getString(1));
			}
		}
	}

	private static Table table(final String name) {
		final Table table = new Table(name);
		final Column id = new Column("ID").setDataType(DataType.INT);
		table.getColumns().add(id);
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("PK_" + name, id);
		return table;
	}

	private static Table jobTable(final String name, final boolean child) {
		final Table table = new Table(name);
		final Column id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		if (child) {
			table.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT));
		}
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("PK_" + name, id);
		return table;
	}

	private static final class FailSecondSaveStore implements BulkMigrationCheckpointStore {
		private final BulkMigrationCheckpointStore delegate;
		private int saves;

		private FailSecondSaveStore(final BulkMigrationCheckpointStore delegate) {
			this.delegate = delegate;
		}

		@Override
		public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
			return delegate.load(migrationId);
		}

		@Override
		public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
			if (++saves == 2) {
				throw new SQLException("simulated checkpoint failure");
			}
			delegate.save(checkpoint);
		}

		@Override
		public void delete(final String migrationId) throws SQLException {
			delegate.delete(migrationId);
		}
	}

	private static final class FailSaveJdbcStore
			implements TransactionalBulkMigrationCheckpointStore {
		private final BulkMigrationCheckpointStore delegate;
		private final java.sql.Connection connection;

		private FailSaveJdbcStore(final BulkMigrationCheckpointStore delegate,
				final java.sql.Connection connection) {
			this.delegate = delegate;
			this.connection = connection;
		}

		@Override
		public boolean participatesIn(final java.sql.Connection candidate) {
			return connection == candidate;
		}

		@Override
		public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
			return delegate.load(migrationId);
		}

		@Override
		public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
			throw new SQLException("simulated database checkpoint failure");
		}

		@Override
		public void delete(final String migrationId) throws SQLException {
			delegate.delete(migrationId);
		}
	}

	private static final class IntegerKeysetSource implements BulkMigrationKeysetSource {
		private final Table table;
		private List<Integer> ids;

		private IntegerKeysetSource(final String tableName, final Integer... ids) {
			this.table = table(tableName);
			this.ids = List.of(ids);
		}

		private void setIds(final Integer... ids) {
			this.ids = List.of(ids);
		}

		@Override
		public Table getTable() {
			return table;
		}

		@Override
		public Iterator<Row> iterator(final String resumeToken) {
			final int after = resumeToken == null ? Integer.MIN_VALUE : Integer.parseInt(resumeToken);
			return ids.stream().filter(id -> id > after).map(id -> {
				final Row row = table.newRow();
				row.put("ID", id);
				row.put("TXT", "value-" + id);
				return row;
			}).iterator();
		}

		@Override
		public String resumeToken(final Row row) {
			return java.util.Objects.toString(row.get("ID"));
		}
	}
}
