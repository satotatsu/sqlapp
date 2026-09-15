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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobExecutorTest {
	@Test
	void migrationResultsRejectInvalidAggregateAndTaskState() {
		final var migration = new ChunkedBulkMigrationResult(0, 1, 1, false);
		final var task = new BulkMigrationJobTaskResult("task", migration);

		assertThrows(IllegalArgumentException.class,
				() -> new ChunkedBulkMigrationResult(-1, 0, 0, false));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobTaskResult(" ", migration));
		assertThrows(NullPointerException.class,
				() -> new BulkMigrationJobTaskResult("task", null));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobResult(" ", List.of(task)));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobResult("plan", List.of(task, task)));
		final var max = new BulkMigrationJobTaskResult("max",
				new ChunkedBulkMigrationResult(1, Long.MAX_VALUE, 0, false));
		final var one = new BulkMigrationJobTaskResult("one",
				new ChunkedBulkMigrationResult(1, 1, 0, false));
		assertThrows(ArithmeticException.class,
				() -> new BulkMigrationJobResult("plan", List.of(max, one))
						.getProcessedRows());
	}

	@Test
	void completedMigrationResultValidatesAgainstItsPlan() {
		final var firstPlanTask = tableTask("first", "first-copy", table("FIRST"));
		final var secondPlanTask = tableTask("second", "second-copy", table("SECOND"));
		final var plan = BulkMigrationJobPlanner.plan(List.of(firstPlanTask, secondPlanTask));
		final var taskResult = new BulkMigrationJobTaskResult("task",
				new ChunkedBulkMigrationResult(0, 1, 1, false));
		final var firstResult = new BulkMigrationJobTaskResult("first",
				new ChunkedBulkMigrationResult(0, 1, 1, false));
		final var secondResult = new BulkMigrationJobTaskResult("second",
				new ChunkedBulkMigrationResult(0, 1, 1, false));
		final var result = new BulkMigrationJobResult(plan.getFingerprint(),
				List.of(firstResult, secondResult));

		assertEquals(result, result.validateAgainst(plan));
		assertEquals(List.of(firstResult), new BulkMigrationJobResult(plan.getFingerprint(),
				List.of(firstResult)).validateCompletedPrefixAgainst(plan, "second").getTasks());
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobResult("other", List.of(taskResult))
						.validateAgainst(plan));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobResult(plan.getFingerprint(), List.of())
						.validateAgainst(plan));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobResult(plan.getFingerprint(), List.of(secondResult))
						.validateCompletedPrefixAgainst(plan, "second"));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobResult(plan.getFingerprint(), List.of(firstResult))
						.validateCompletedPrefixAgainst(plan, "missing"));
	}

	@Test
	void jobFailureAndPauseRequireCompleteContext() {
		final var task = tableTask("task", "migration", table("TARGET"));
		final var plan = BulkMigrationJobPlanner.plan(List.of(task));
		final var completed = new BulkMigrationJobResult(plan.getFingerprint(), List.of());
		final var cause = new SQLException("failed");
		final var progress = new ChunkedBulkMigrationProgress("migration", 0, 1, 0, 1);
		final var paused = new ChunkedBulkMigrationPausedException(progress);

		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobException(plan, " ", completed, cause));
		assertThrows(NullPointerException.class,
				() -> new BulkMigrationJobException(plan, "task", null, cause));
		assertThrows(NullPointerException.class,
				() -> new BulkMigrationJobException(plan, "task", completed, null));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobPausedException(plan, " ", completed, paused));
		assertThrows(NullPointerException.class,
				() -> new BulkMigrationJobPausedException(plan, "task", null, paused));
		assertThrows(NullPointerException.class,
				() -> new BulkMigrationJobPausedException(plan, "task", completed, null));
		assertThrows(NullPointerException.class,
				() -> new ChunkedBulkMigrationPausedException(null));
		final var foreignResult = new BulkMigrationJobResult("foreign", List.of());
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobException(plan, "task", foreignResult, cause));
		final var foreignProgress = new ChunkedBulkMigrationPausedException(
				new ChunkedBulkMigrationProgress("other", 0, 1, 0, 1));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobPausedException(plan, "task", completed,
						foreignProgress));
	}

	@Test
	void separatesStableJobIdentityFromTheExactPlanFingerprint() {
		final Table table = new Table("CUSTOMERS");
		table.getColumns().add(new Column("ID"));
		final var first = BulkMigrationJobTask.builder().taskId("customers")
				.sourceTable(table).options(ChunkedBulkMigrationOption.builder()
						.migrationId("customer-load").chunkSize(100)
						.mode(BulkMigrationMode.INSERT).resume(false)
						.build()).build();
		final var changed = BulkMigrationJobTask.builder().taskId("customers")
				.sourceTable(table).options(ChunkedBulkMigrationOption.builder()
						.migrationId("customer-load").chunkSize(200)
						.mode(BulkMigrationMode.INSERT).resume(false)
						.build()).build();

		final var firstPlan = BulkMigrationJobPlanner.plan(List.of(first));
		final var changedPlan = BulkMigrationJobPlanner.plan(List.of(changed));

		assertEquals(firstPlan.getJobId(), changedPlan.getJobId());
		assertNotEquals(firstPlan.getFingerprint(), changedPlan.getFingerprint());
		final var named = BulkMigrationJobPlanner.plan("nightly-customers",
				List.of(first), BulkMigrationJobLifecycle.NO_OP);
		assertEquals("nightly-customers", named.getJobId());
		assertNotEquals(firstPlan.getFingerprint(), named.getFingerprint());
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner
				.plan(" ", List.of(first), BulkMigrationJobLifecycle.NO_OP));
	}

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
	void revalidatesThePlanAcrossAfterAndCompletionCallbacks() throws Exception {
		final var lifecycleVersion = new AtomicInteger(1);
		final var restored = new java.util.concurrent.atomic.AtomicBoolean();
		final BulkMigrationJobLifecycle mutatingLifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "lifecycle-" + lifecycleVersion.get();
			}

			@Override
			public void after(Connection connection, BulkMigrationJobPlan plan,
					BulkMigrationJobResult result) {
				lifecycleVersion.incrementAndGet();
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) {
				restored.set(true);
			}
		};
		final var afterPlan = BulkMigrationJobPlanner.plan(List.of(), mutatingLifecycle);
		assertThrows(IllegalStateException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection(), afterPlan));
		assertTrue(restored.get());

		final var listenerVersion = new AtomicInteger(1);
		final BulkMigrationJobLifecycle stableUntilNotification =
				new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "listener-" + listenerVersion.get();
			}
		};
		final var listenerPlan = BulkMigrationJobPlanner.plan(List.of(),
				stableUntilNotification);
		final var listener = new BulkMigrationJobListener() {
			@Override
			public void onJobCompleted(BulkMigrationJobResult result) {
				listenerVersion.incrementAndGet();
			}
		};
		assertThrows(IllegalStateException.class, () -> BulkMigrationJobExecutor
				.executePlan(connection(), listenerPlan, listener));
	}

	@Test
	void reportsPreflightRejectionWithoutStartingOrRestoringTheJob() {
		final List<String> events = new ArrayList<>();
		final var lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public void validateBeforeExecution(BulkMigrationJobPlan plan) {
				throw new IllegalStateException("recovery required");
			}

			@Override
			public void before(Connection connection, BulkMigrationJobPlan plan) {
				events.add("before");
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) {
				events.add("restore");
			}
		};
		final var listener = new BulkMigrationJobListener() {
			@Override
			public void onJobStarted(String fingerprint, int taskCount) {
				events.add("started");
			}

			@Override
			public void onJobRejected(String fingerprint, Throwable cause) {
				events.add("rejected:" + cause.getMessage());
			}
		};
		final var plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle);

		assertThrows(IllegalStateException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection(), plan, listener));

		assertEquals(List.of("rejected:recovery required"), events);
	}

	@Test
	void revalidatesThePlanAcrossPreflightAndStartCallbacks() {
		final var preflightVersion = new AtomicInteger(1);
		final var rejected = new java.util.concurrent.atomic.AtomicBoolean();
		final BulkMigrationJobLifecycle preflightMutation = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "preflight-" + preflightVersion.get();
			}

			@Override
			public void validateBeforeExecution(BulkMigrationJobPlan plan) {
				preflightVersion.incrementAndGet();
			}
		};
		final var preflightPlan = BulkMigrationJobPlanner.plan(List.of(),
				preflightMutation);
		assertThrows(IllegalStateException.class, () -> BulkMigrationJobExecutor
				.executePlan(connection(), preflightPlan, new BulkMigrationJobListener() {
					@Override
					public void onJobRejected(String fingerprint, Throwable cause) {
						rejected.set(true);
					}
				}));
		assertTrue(rejected.get());

		final var startVersion = new AtomicInteger(1);
		final var restored = new java.util.concurrent.atomic.AtomicBoolean();
		final BulkMigrationJobLifecycle startMutation = mutableLifecycle(startVersion,
				restored, false);
		final var startPlan = BulkMigrationJobPlanner.plan(List.of(), startMutation);
		assertThrows(IllegalStateException.class, () -> BulkMigrationJobExecutor
				.executePlan(connection(), startPlan, new BulkMigrationJobListener() {
					@Override
					public void onJobStarted(String fingerprint, int taskCount) {
						startVersion.incrementAndGet();
					}
				}));
		assertTrue(restored.get());

		final var beforeVersion = new AtomicInteger(1);
		final var beforeRestored = new java.util.concurrent.atomic.AtomicBoolean();
		final var beforePlan = BulkMigrationJobPlanner.plan(List.of(),
				mutableLifecycle(beforeVersion, beforeRestored, true));
		assertThrows(IllegalStateException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection(), beforePlan));
		assertTrue(beforeRestored.get());
	}

	private static BulkMigrationJobLifecycle mutableLifecycle(
			final AtomicInteger version,
			final java.util.concurrent.atomic.AtomicBoolean restored,
			final boolean mutateBefore) {
		return new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "start-" + version.get();
			}

			@Override
			public void before(Connection connection, BulkMigrationJobPlan plan) {
				if (mutateBefore) {
					version.incrementAndGet();
				}
			}

			@Override
			public void restore(Connection connection, BulkMigrationJobPlan plan,
					Throwable failure) {
				restored.set(true);
			}
		};
	}

	@Test
	void leaseIsHeldBeforeNotificationAndReleasedAfterExecution() throws Exception {
		final var store = new InMemoryBulkMigrationJobLeaseStore();
		final var manager = new BulkMigrationJobLeaseManager(store, "owner-1",
				Duration.ofMinutes(1));
		final var competitor = new BulkMigrationJobLeaseManager(store, "owner-2",
				Duration.ofMinutes(1));
		final var plan = BulkMigrationJobPlanner.plan(List.of());
		final var listener = new BulkMigrationJobListener() {
			@Override
			public void onJobStarted(String planFingerprint, int taskCount) {
				assertThrows(BulkMigrationJobLeaseUnavailableException.class,
						() -> competitor.acquire(plan.getJobId(), planFingerprint));
			}
		};

		BulkMigrationJobExecutor.executePlan(connection(), plan, listener,
				ChunkedBulkMigrationListener.NO_OP, manager);

		assertTrue(store.load(plan.getJobId()).isEmpty());
		try (var lease = competitor.acquire(plan.getJobId(), plan.getFingerprint())) {
			assertEquals("owner-2", lease.getLease().ownerId());
		}
	}

	@Test
	void heartbeatLossDuringLifecyclePreventsJobCompletionNotification()
			throws Exception {
		final var delegateStore = new InMemoryBulkMigrationJobLeaseStore();
		final var renewalAttempted = new CountDownLatch(1);
		final BulkMigrationJobLeaseStore failingStore =
				new BulkMigrationJobLeaseStore() {
			@Override
			public Optional<BulkMigrationJobLease> load(String planFingerprint) {
				return delegateStore.load(planFingerprint);
			}

			@Override
			public boolean tryAcquire(BulkMigrationJobLease lease, Instant now) {
				return delegateStore.tryAcquire(lease, now);
			}

			@Override
			public boolean renew(BulkMigrationJobLease lease, Instant now) {
				renewalAttempted.countDown();
				return false;
			}

			@Override
			public void release(String planFingerprint, String ownerId) {
				delegateStore.release(planFingerprint, ownerId);
			}
		};
		final var lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public void after(Connection connection, BulkMigrationJobPlan plan,
					BulkMigrationJobResult result) throws SQLException {
				try {
					assertTrue(renewalAttempted.await(1, TimeUnit.SECONDS));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new SQLException(e);
				}
			}
		};
		final var plan = BulkMigrationJobPlanner.plan(List.of(), lifecycle);
		final var manager = new BulkMigrationJobLeaseManager(failingStore, "owner",
				Duration.ofMillis(30));
		final var completed = new java.util.concurrent.atomic.AtomicBoolean();
		final var listener = new BulkMigrationJobListener() {
			@Override
			public void onJobCompleted(BulkMigrationJobResult result) {
				completed.set(true);
			}
		};

		assertThrows(BulkMigrationJobLeaseLostException.class,
				() -> BulkMigrationJobExecutor.executePlan(connection(), plan, listener,
						ChunkedBulkMigrationListener.NO_OP, manager));
		assertFalse(completed.get());
		assertTrue(delegateStore.load(plan.getJobId()).isEmpty());
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
	void ordersTasksWhenTheForeignKeyUsesAnEquivalentParentInstance() {
		final Table parent = table("PARENT");
		final Table equivalentParent = table("parent");
		final Table child = table("CHILD");
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("ID"), equivalentParent.getColumns().get("ID"));

		final var ordered = BulkMigrationJobExecutor.order(List.of(
				tableTask("child", "equivalent-child", child),
				tableTask("parent", "equivalent-parent", parent)));

		assertEquals(List.of("parent", "child"), ordered.stream()
				.map(BulkMigrationJobTask::getTaskId).toList());
	}

	@Test
	void ignoresAnUnresolvedForeignKeyWhenOrderingTasks() {
		final Table table = table("STANDALONE");
		final ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("FK_EXTERNAL");
		foreignKey.getColumns().add(table.getColumns().get("ID"));
		foreignKey.setRelatedTableName("MISSING_PARENT");
		foreignKey.getRelatedColumns().add("ID");
		table.getConstraints().add(foreignKey);
		final var task = tableTask("standalone", "unresolved-foreign-key", table);

		assertEquals(List.of(task), BulkMigrationJobExecutor.order(List.of(task)));
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
		final var directPlan = new BulkMigrationJobPlan(List.of(childTask, parentTask));
		final var namedPlan = BulkMigrationJobPlanner.plan("nightly-copy",
				List.of(childTask, parentTask));

		assertEquals(List.of("parent", "child"), plan.getTaskIds());
		assertEquals(plan.getTaskIds(), directPlan.getTaskIds());
		assertEquals(plan.getFingerprint(), directPlan.getFingerprint());
		assertEquals("nightly-copy", namedPlan.getJobId());
		assertEquals(plan.getTaskIds(), namedPlan.getTaskIds());
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
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobPlan(List.of(parentTask, parentTask)));
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
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobPlanner.plan(List.of(BulkMigrationJobTask.builder()
						.taskId("missing-config").keysetSource(keyset(table, " "))
						.options(options("missing-config")).build())));
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

		assertThrows(IllegalArgumentException.class, () -> BulkUpsertOption.builder()
				.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.CUSTOM)
				.duplicateRowSelector(selector).build());
	}

	@Test
	void fingerprintDistinguishesForeignKeyParentCatalogs() {
		final Table firstParent = tableIn("CATALOG1", "PUBLIC", "PARENT");
		final Table secondParent = tableIn("CATALOG2", "PUBLIC", "PARENT");
		final Table firstChild = table("CHILD");
		firstChild.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				firstChild.getColumns().get("ID"), firstParent.getColumns().get("ID"));
		final Table secondChild = table("CHILD");
		secondChild.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				secondChild.getColumns().get("ID"), secondParent.getColumns().get("ID"));

		final String firstFingerprint = BulkMigrationJobPlanner.plan(List.of(
				tableTask("child", "catalog-parent", firstChild))).getFingerprint();
		final String secondFingerprint = BulkMigrationJobPlanner.plan(List.of(
				tableTask("child", "catalog-parent", secondChild))).getFingerprint();

		assertNotEquals(firstFingerprint, secondFingerprint);
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
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobTask.builder().taskId("missing")
						.options(options("missing-source")).build());
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobTask.builder().taskId("both")
						.sourceTable(table).keysetSource(keyset(table))
						.options(options("both-sources")).build());
		assertThrows(NullPointerException.class,
				() -> BulkMigrationJobTask.builder().taskId("missing-options")
						.sourceTable(table).build());
	}

	@Test
	void requiresNonBlankTaskAndMigrationIds() {
		assertThrows(IllegalArgumentException.class,
				() -> tableTask(" ", "migration", table("A")));
		assertThrows(IllegalArgumentException.class,
				() -> ChunkedBulkMigrationOption.builder().migrationId(" ").build());
	}

	@Test
	void validatesAllStructuralMigrationOptionsWhilePlanning() {
		assertThrows(IllegalArgumentException.class, () -> ChunkedBulkMigrationOption.builder()
				.migrationId("invalid-chunk").chunkSize(0).build());
		assertThrows(IllegalArgumentException.class, () -> ChunkedBulkMigrationOption.builder()
				.migrationId("missing-mode").mode(null).build());
		assertThrows(IllegalArgumentException.class, () -> ChunkedBulkMigrationOption.builder()
				.migrationId("missing-checkpoint-mode").checkpointMode(null).build());
		assertThrows(IllegalArgumentException.class, () -> ChunkedBulkMigrationOption.builder()
				.migrationId("missing-checkpoint-table")
				.checkpointMode(BulkMigrationCheckpointMode.DATABASE)
				.checkpointTableName(" ").build());
		assertThrows(NullPointerException.class, () -> ChunkedBulkMigrationOption.builder()
				.migrationId("missing-retry").retryOption(null).build());
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
		assertThrows(IllegalArgumentException.class, () -> BulkMigrationJobPlanner.plan(
				List.of(tableTask("unknown-key", table, unknownKey))));
		assertThrows(IllegalArgumentException.class, () -> BulkUpsertOption.builder()
				.keyColumn("ID").updateWhenMatched(false).insertWhenNotMatched(false)
				.build());
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

	@Test
	void allowsSelfReferenceThroughAnEquivalentTableInstance() {
		final Table table = table("TREE");
		final Table equivalentTable = table("tree");
		table.getConstraints().addForeignKeyConstraint("FK_TREE_PARENT",
				table.getColumns().get("ID"), equivalentTable.getColumns().get("ID"));
		final var task = tableTask("tree", "equivalent-self-reference", table);

		assertEquals(List.of(task), BulkMigrationJobExecutor.order(List.of(task)));
	}

	private static BulkMigrationJobTask tableTask(final String taskId,
			final String migrationId, final Table table) {
		return tableTask(taskId, table, options(migrationId));
	}

	private static Table tableIn(final String catalogName, final String schemaName,
			final String tableName) {
		final Catalog catalog = new Catalog(catalogName);
		final Schema schema = new Schema(schemaName);
		catalog.getSchemas().add(schema);
		final Table table = table(tableName);
		schema.getTables().add(table);
		return table;
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
