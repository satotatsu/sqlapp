/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobExecutorTest {
	@Test
	void reportsFinalJobLifecycleWithoutRestoringAfterCompletionNotification() throws Exception {
		final List<String> events = new ArrayList<>();
		final var listener = new BulkMigrationJobListener() {
			@Override
			public void onJobStarted(String planFingerprint, int taskCount) {
				events.add("started:" + taskCount);
			}

			@Override
			public void onJobCompleted(BulkMigrationJobResult result) {
				events.add("completed:" + result.getTasks().size());
			}

			@Override
			public void onJobFailed(String planFingerprint, Throwable cause) {
				events.add("failed:" + cause.getMessage());
			}
		};
		final var plan = BulkMigrationJobPlanner.plan(List.of());
		BulkMigrationJobExecutor.executePlan(connection(), plan, listener);
		assertEquals(List.of("started:0", "completed:0"), events);

		events.clear();
		final var failing = BulkMigrationJobPlanner.plan(List.of(),
				new BulkMigrationJobLifecycle() {
					@Override
					public void before(Connection connection, BulkMigrationJobPlan plan)
							throws SQLException {
						throw new SQLException("prepare failed");
					}
				});
		assertThrows(SQLException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection(), failing, listener));
		assertEquals(List.of("started:0", "failed:prepare failed"), events);
	}

	@Test
	void plansAndExecutesLifecycleAndRestoresAfterFailure() throws Exception {
		final List<String> events = new ArrayList<>();
		final BulkMigrationJobLifecycle lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "lifecycle-v1";
			}

			@Override
			public List<BulkMigrationJobOperation> plan(List<BulkMigrationJobTask> tasks) {
				return List.of(new BulkMigrationJobOperation("constraints", 
						BulkMigrationJobOperationPhase.BEFORE,
						"Disable constraints", true));
			}

			@Override
			public void before(Connection connection, BulkMigrationJobPlan plan) {
				events.add("before");
			}

			@Override
			public void after(Connection connection, BulkMigrationJobPlan plan,
					BulkMigrationJobResult result) {
				events.add("after");
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) {
				events.add("restore");
			}
		};
		final BulkMigrationJobPlan plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle);
		assertEquals("constraints", plan.getOperations().get(0).id());
		assertThrows(UnsupportedOperationException.class,
				() -> plan.getOperations().clear());
		final Connection connection = connection();

		BulkMigrationJobExecutor.executePlan(connection, plan);
		assertEquals(List.of("before", "after"), events);

		events.clear();
		final BulkMigrationJobLifecycle failing = new BulkMigrationJobLifecycle() {
			@Override
			public void before(Connection connection, BulkMigrationJobPlan plan)
					throws SQLException {
				throw new SQLException("before failed");
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) throws SQLException {
				events.add("restore");
				throw new SQLException("restore failed");
			}
		};
		final SQLException failure = assertThrows(SQLException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection,
						BulkMigrationJobPlanner.plan(List.of(), failing)));
		assertEquals(List.of("restore"), events);
		assertEquals("restore failed", failure.getSuppressed()[0].getMessage());
	}

	@Test
	void ordersKeysetTasksUsingTheirSchemaTables() {
		final Table parent = table("PARENT");
		final Table child = table("CHILD");
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("ID"), parent.getColumns().get("ID"));
		final var parentTask = keysetTask("parent", "migration-parent", parent);
		final var childTask = keysetTask("child", "migration-child", child);

		final var ordered = BulkMigrationJobExecutor.order(List.of(childTask, parentTask));

		assertEquals(List.of("parent", "child"), ordered.stream()
				.map(BulkMigrationJobTask::getTaskId).toList());
	}

	@Test
	void publicPlannerReturnsAnImmutableValidatedDryRunOrder() {
		final Table parent = table("PARENT");
		final Table child = table("CHILD");
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("ID"), parent.getColumns().get("ID"));
		final var parentTask = tableTask("parent", "plan-parent", parent);
		final var childTask = tableTask("child", "plan-child", child);

		final var plan = BulkMigrationJobPlanner.plan(List.of(childTask, parentTask));

		assertEquals(List.of("parent", "child"), plan.getTaskIds());
		assertThrows(UnsupportedOperationException.class,
				() -> plan.getTasks().add(parentTask));
		assertEquals(plan.getFingerprint(), BulkMigrationJobPlanner
				.plan(List.of(parentTask, childTask)).getFingerprint());
		final var changedChild = tableTask("child", child,
				ChunkedBulkMigrationOption.builder().migrationId("plan-child")
						.sourceFingerprint("source-v1").targetFingerprint("target-v1")
						.chunkSize(123).build());
		assertNotEquals(plan.getFingerprint(),
				BulkMigrationJobPlanner.plan(List.of(changedChild, parentTask)).getFingerprint());
	}

	@Test
	void detectsSchemaMutationAfterPlanning() {
		final Table table = table("BEFORE");
		final var plan = BulkMigrationJobPlanner.plan(List.of(
				tableTask("task", "mutation-plan", table)));
		assertTrue(plan.isUnchanged());

		table.setName("AFTER");

		assertFalse(plan.isUnchanged());
		assertThrows(IllegalStateException.class, plan::validateUnchanged);
	}

	@Test
	void detectsColumnKeyAndDependencyMutationAfterPlanning() {
		final Table columns = table("COLUMN_MUTATION");
		final var columnPlan = BulkMigrationJobPlanner.plan(List.of(
				tableTask("columns", "column-mutation", columns)));
		columns.getColumns().add(new Column("ADDED"));
		assertFalse(columnPlan.isUnchanged());

		final Table keys = table("KEY_MUTATION");
		final var keyPlan = BulkMigrationJobPlanner.plan(List.of(
				tableTask("keys", "key-mutation", keys)));
		keys.getPrimaryKeyConstraint().setName("PK_KEY_MUTATION_CHANGED");
		assertFalse(keyPlan.isUnchanged());

		final Table parent = table("DEPENDENCY_PARENT");
		final Table child = table("DEPENDENCY_CHILD");
		final var dependencyPlan = BulkMigrationJobPlanner.plan(List.of(
				tableTask("parent", "dependency-parent", parent),
				tableTask("child", "dependency-child", child)));
		child.getConstraints().addForeignKeyConstraint("FK_DEPENDENCY",
				child.getColumns().get("ID"), parent.getColumns().get("ID"));
		assertFalse(dependencyPlan.isUnchanged());
	}

	@Test
	void fingerprintDistinguishesStructuredColumnListsAndSourceStyles() {
		final Table table = table("STRUCTURED");
		table.getColumns().add(new Column("A, B"));
		table.getColumns().add(new Column("A"));
		table.getColumns().add(new Column("B"));
		final var commaName = ChunkedBulkMigrationOption.builder().migrationId("structured")
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.bulkUpsertOption(BulkUpsertOption.builder().keyColumn("A, B").build()).build();
		final var twoNames = ChunkedBulkMigrationOption.builder().migrationId("structured")
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.bulkUpsertOption(BulkUpsertOption.builder().keyColumn("A").keyColumn("B").build())
				.build();
		final var tablePlan = BulkMigrationJobPlanner.plan(List.of(
				tableTask("task", table, commaName)));
		final var otherColumnsPlan = BulkMigrationJobPlanner.plan(List.of(
				tableTask("task", table, twoNames)));
		final var keysetPlan = BulkMigrationJobPlanner.plan(List.of(
				keysetTask("task", table, commaName)));

		assertNotEquals(tablePlan.getFingerprint(), otherColumnsPlan.getFingerprint());
		assertNotEquals(tablePlan.getFingerprint(), keysetPlan.getFingerprint());
	}

	@Test
	void fingerprintIncludesKeysetResumeConfiguration() {
		final Table table = table("KEYSET_CONFIG");
		final var first = BulkMigrationJobTask.builder().taskId("task")
				.keysetSource(keyset(table, "keys=[A,B]"))
				.options(options("keyset-config")).build();
		final var reordered = BulkMigrationJobTask.builder().taskId("task")
				.keysetSource(keyset(table, "keys=[B,A]"))
				.options(options("keyset-config")).build();

		assertNotEquals(BulkMigrationJobPlanner.plan(List.of(first)).getFingerprint(),
				BulkMigrationJobPlanner.plan(List.of(reordered)).getFingerprint());
		final var missing = BulkMigrationJobTask.builder().taskId("missing-config")
				.keysetSource(keyset(table, " ")).options(options("missing-config")).build();
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobPlanner.plan(List.of(missing)));
	}

	@Test
	void fingerprintIncludesCustomDuplicateSelectorIdentity() {
		final Table table = table("CUSTOM_SELECTOR");
		final BulkUpsertDuplicateRowSelector selector = (retained, candidate) -> retained;
		final var first = ChunkedBulkMigrationOption.builder().migrationId("custom-selector")
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.bulkUpsertOption(BulkUpsertOption.builder()
						.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.CUSTOM)
						.duplicateRowSelector(selector)
						.duplicateRowSelectorFingerprint("selector-v1").build()).build();
		final var changed = ChunkedBulkMigrationOption.builder().migrationId("custom-selector")
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.bulkUpsertOption(BulkUpsertOption.builder()
						.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.CUSTOM)
						.duplicateRowSelector(selector)
						.duplicateRowSelectorFingerprint("selector-v2").build()).build();

		assertNotEquals(BulkMigrationJobPlanner.plan(List.of(
				tableTask("task", table, first))).getFingerprint(),
				BulkMigrationJobPlanner.plan(List.of(
						tableTask("task", table, changed))).getFingerprint());

		final var missingFingerprint = ChunkedBulkMigrationOption.builder()
				.migrationId("missing-selector-fingerprint")
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.bulkUpsertOption(BulkUpsertOption.builder()
						.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.CUSTOM)
						.duplicateRowSelector(selector).build()).build();
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(List.of(
				tableTask("missing", table, missingFingerprint))));
	}

	@Test
	void rejectsDuplicateTaskIds() {
		final var first = tableTask("duplicate", "migration-1", table("A"));
		final var second = tableTask("duplicate", "migration-2", table("B"));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(first, second)));
	}

	@Test
	void rejectsDuplicateCheckpointMigrationIds() {
		final var first = tableTask("first", "duplicate", table("A"));
		final var second = tableTask("second", "duplicate", table("B"));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(first, second)));
	}

	@Test
	void requiresExactlyOneSourceKind() {
		final Table table = table("TARGET");
		final var missing = BulkMigrationJobTask.builder().taskId("missing")
				.options(options("missing-source")).build();
		final var both = BulkMigrationJobTask.builder().taskId("both")
				.sourceTable(table).keysetSource(keyset(table))
				.options(options("both-sources")).build();

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(missing)));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(both)));
	}

	@Test
	void requiresNonBlankTaskAndMigrationIds() {
		final var missingTaskId = tableTask(" ", "migration", table("A"));
		final var missingMigrationId = tableTask("task", " ", table("B"));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(missingTaskId)));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(missingMigrationId)));
	}

	@Test
	void validatesAllStructuralMigrationOptionsWhilePlanning() {
		final Table table = table("INVALID_OPTIONS");
		final var invalidChunk = ChunkedBulkMigrationOption.builder()
				.migrationId("invalid-chunk").chunkSize(0).build();
		final var missingMode = ChunkedBulkMigrationOption.builder()
				.migrationId("missing-mode").mode(null).build();
		final var missingCheckpointMode = ChunkedBulkMigrationOption.builder()
				.migrationId("missing-checkpoint-mode").checkpointMode(null).build();
		final var missingCheckpointTable = ChunkedBulkMigrationOption.builder()
				.migrationId("missing-checkpoint-table")
				.checkpointMode(BulkMigrationCheckpointMode.DATABASE)
				.checkpointTableName(" ").build();

		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("chunk", table, invalidChunk))));
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("mode", table, missingMode))));
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("checkpoint-mode", table, missingCheckpointMode))));
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("checkpoint-table", table, missingCheckpointTable))));
	}

	@Test
	void requiresFingerprintsOnlyForResumableMigrations() {
		final Table table = table("RESUME_FINGERPRINTS");
		final var missingSource = ChunkedBulkMigrationOption.builder()
				.migrationId("missing-source-fingerprint")
				.targetFingerprint("target-v1").build();
		final var missingTarget = ChunkedBulkMigrationOption.builder()
				.migrationId("missing-target-fingerprint")
				.sourceFingerprint("source-v1").build();
		final var noResume = ChunkedBulkMigrationOption.builder()
				.migrationId("no-resume").resume(false).build();

		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("missing-source", table, missingSource))));
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("missing-target", table, missingTarget))));
		assertEquals(List.of("no-resume"), BulkMigrationJobPlanner.plan(
				List.of(tableTask("no-resume", table, noResume))).getTaskIds());
	}

	@Test
	void resolvesAndValidatesUpsertBeforeExecution() {
		final Table table = table("INVALID_UPSERT");
		final var unknownKey = ChunkedBulkMigrationOption.builder()
				.migrationId("unknown-key")
				.bulkUpsertOption(BulkUpsertOption.builder().keyColumn("MISSING").build())
				.build();
		final var noActions = ChunkedBulkMigrationOption.builder()
				.migrationId("no-actions")
				.bulkUpsertOption(BulkUpsertOption.builder().keyColumn("ID")
						.updateWhenMatched(false).insertWhenNotMatched(false).build())
				.build();

		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("unknown-key", table, unknownKey))));
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("no-actions", table, noActions))));
	}

	@Test
	void rejectsCyclicForeignKeyDependencies() {
		final Table firstTable = table("FIRST");
		final Table secondTable = table("SECOND");
		firstTable.getConstraints().addForeignKeyConstraint("FK_FIRST_SECOND",
				firstTable.getColumns().get("ID"), secondTable.getColumns().get("ID"));
		secondTable.getConstraints().addForeignKeyConstraint("FK_SECOND_FIRST",
				secondTable.getColumns().get("ID"), firstTable.getColumns().get("ID"));
		final var first = tableTask("first", "migration-first", firstTable);
		final var second = tableTask("second", "migration-second", secondTable);

		final var failure = assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobExecutor.order(List.of(first, second)));

		assertEquals("Migration job contains cyclic or cycle-dependent tasks: [first, second]",
				failure.getMessage());
	}

	@Test
	void allowsSelfReferencingTable() {
		final Table table = table("TREE");
		table.getConstraints().addForeignKeyConstraint("FK_TREE_PARENT",
				table.getColumns().get("ID"), table.getColumns().get("ID"));
		final var task = tableTask("tree", "migration-tree", table);

		assertEquals(List.of(task), BulkMigrationJobExecutor.order(List.of(task)));
	}

	private static BulkMigrationJobTask tableTask(final String taskId,
			final String migrationId, final Table table) {
		return tableTask(taskId, table, options(migrationId));
	}

	private static BulkMigrationJobTask tableTask(final String taskId,
			final Table table,
			final ChunkedBulkMigrationOption options) {
		return BulkMigrationJobTask.builder().taskId(taskId).sourceTable(table)
				.options(options).build();
	}

	private static BulkMigrationJobTask keysetTask(final String taskId,
			final String migrationId, final Table table) {
		return keysetTask(taskId, table, options(migrationId));
	}

	private static BulkMigrationJobTask keysetTask(final String taskId,
			final Table table,
			final ChunkedBulkMigrationOption options) {
		return BulkMigrationJobTask.builder().taskId(taskId).keysetSource(keyset(table))
				.options(options).build();
	}

	private static ChunkedBulkMigrationOption options(final String migrationId) {
		return ChunkedBulkMigrationOption.builder().migrationId(migrationId)
				.sourceFingerprint("source-v1").targetFingerprint("target-v1").build();
	}

	private static BulkMigrationKeysetSource keyset(final Table table) {
		return keyset(table, "test-keyset-v1");
	}

	private static BulkMigrationKeysetSource keyset(final Table table,
			final String configurationFingerprint) {
		return new BulkMigrationKeysetSource() {
			@Override
			public Table getTable() {
				return table;
			}

			@Override
			public String getConfigurationFingerprint() {
				return configurationFingerprint;
			}

			@Override
			public Iterator<Row> iterator(String resumeToken) {
				return table.getRows().iterator();
			}

			@Override
			public String resumeToken(Row row) {
				return String.valueOf(row.get("ID"));
			}
		};
	}

	private static Table table(final String name) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		table.setPrimaryKey("PK_" + name, table.getColumns().get("ID"));
		return table;
	}

	private static Connection connection() {
		return (Connection) Proxy.newProxyInstance(
				BulkMigrationJobExecutorTest.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					throw new UnsupportedOperationException(method.getName());
				});
	}
}
