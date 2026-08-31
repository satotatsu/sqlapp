/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLifecycle;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLease;
import com.sqlapp.jdbc.bulk.BulkMigrationJobOperation;
import com.sqlapp.jdbc.bulk.BulkMigrationJobOperationPhase;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationJobLeaseStore;
import com.sqlapp.util.JsonConverter;

class BulkMigrationOperationalReportTest {
	@TempDir
	Path directory;

	@Test
	@SuppressWarnings("unchecked")
	void buildsAndAtomicallyWritesStableJsonSnapshot() {
		final Instant generatedAt = Instant.parse("2026-08-31T10:00:00Z");
		final BulkMigrationJobPlan plan = plan();
		final BulkMigrationCheckpoint checkpoint = BulkMigrationCheckpoint.builder()
				.migrationId("顧客移行").sourceFingerprint("source")
				.targetFingerprint("target").processedRows(25).completedChunks(1)
				.chunkSize(100).lastChunkHash("abc123").resumeToken("[25]").build();
		final BulkMigrationJobStatus status = new BulkMigrationJobStatus(
				plan.getFingerprint(), List.of(new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.IN_PROGRESS, checkpoint)));
		final BulkMigrationMaintenanceState maintenance = new BulkMigrationMaintenanceState(
				plan.getFingerprint(), BulkMigrationMaintenanceStatus.PREPARED,
				generatedAt.minusSeconds(1), null);
		final BulkMigrationProgressSnapshot progress = new BulkMigrationProgressSnapshot(
				"顧客移行", 25, 100L, Duration.ofSeconds(5), 5.0, 0.25,
				Duration.ofSeconds(15));
		final var report = new BulkMigrationOperationalReportBuilder(
				Clock.fixed(generatedAt, ZoneOffset.UTC)).build(plan, status, maintenance, progress);
		final Path output = directory.resolve("nested").resolve("status.json");

		new BulkMigrationOperationalReportIO().write(output, report);

		final Map<String, Object> json = new JsonConverter().fromJsonString(
				output.toFile(), Map.class);
		assertEquals(1, ((Number) json.get("formatVersion")).intValue());
		assertEquals(plan.getFingerprint(), json.get("planFingerprint"));
		assertEquals(25, ((Number) json.get("processedRows")).longValue());
		assertTrue((Boolean) json.get("compatible"));
		final List<Map<String, Object>> jsonTasks =
				(List<Map<String, Object>>) json.get("tasks");
		assertEquals("顧客移行", jsonTasks.get(0).get("migrationId"));
		assertEquals("CUSTOMERS", jsonTasks.get(0).get("tableName"));
		assertEquals("UPSERT", jsonTasks.get(0).get("mode"));
		final Map<String, Object> jsonCheckpoint =
				(Map<String, Object>) jsonTasks.get(0).get("checkpoint");
		assertEquals("source", jsonCheckpoint.get("sourceFingerprint"));
		final Map<String, Object> jsonProgress = (Map<String, Object>) json.get("progress");
		assertEquals("顧客移行", jsonProgress.get("migrationId"));
		assertEquals(15_000,
				((Number) jsonProgress.get("estimatedRemainingMillis")).longValue());
		final List<Map<String, Object>> progressByMigration =
				(List<Map<String, Object>>) json.get("progressByMigration");
		assertEquals(1, progressByMigration.size());
		assertEquals("顧客移行", progressByMigration.get(0).get("migrationId"));
		final List<Map<String, Object>> operations =
				(List<Map<String, Object>>) json.get("operations");
		assertEquals("DISABLE_CONSTRAINTS", operations.get(0).get("id"));
		assertEquals("BEFORE", operations.get(0).get("phase"));
		assertEquals(report, new BulkMigrationOperationalReportIO().read(output));
		assertEquals(report, new BulkMigrationOperationalReportIO().read(
				output, plan.getFingerprint()));
		assertThrows(com.sqlapp.exceptions.CommandException.class,
				() -> new BulkMigrationOperationalReportIO().read(output, "other-plan"));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationOperationalReportIO().read(output, " "));
	}

	@Test
	void reportReaderRejectsMissingUnsupportedAndInconsistentReports() {
		final var io = new BulkMigrationOperationalReportIO();
		assertThrows(com.sqlapp.exceptions.CommandException.class,
				() -> io.read(directory.resolve("missing.json")));

		final BulkMigrationJobPlan plan = plan();
		final var status = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, null)));
		final var report = new BulkMigrationOperationalReportBuilder().build(
				plan, status, null, null);
		final var unsupported = copyWith(report, 2, report.totalTasks());
		final Path unsupportedFile = directory.resolve("unsupported.json");
		io.write(unsupportedFile, unsupported);
		assertThrows(com.sqlapp.exceptions.CommandException.class,
				() -> io.read(unsupportedFile));

		final var inconsistent = copyWith(report, report.formatVersion(), 2);
		final Path inconsistentFile = directory.resolve("inconsistent.json");
		io.write(inconsistentFile, inconsistent);
		assertThrows(com.sqlapp.exceptions.CommandException.class,
				() -> io.read(inconsistentFile));
	}

	@Test
	void reportReaderRejectsDuplicateAndForeignNestedIdentities() {
		final var io = new BulkMigrationOperationalReportIO();
		final BulkMigrationJobPlan plan = plan();
		final var status = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, null)));
		final var report = new BulkMigrationOperationalReportBuilder().build(
				plan, status, null, null);

		final var duplicateTasks = copyWithContent(report,
				List.of(report.tasks().get(0), report.tasks().get(0)), List.of());
		final Path duplicateFile = directory.resolve("duplicate-tasks.json");
		io.write(duplicateFile, duplicateTasks);
		assertThrows(com.sqlapp.exceptions.CommandException.class,
				() -> io.read(duplicateFile));

		final var foreignProgress = new BulkMigrationOperationalReport.Progress(
				"other", 0, null, 0, 0, null, null);
		final var foreignProgressReport = copyWithContent(report, report.tasks(),
				List.of(foreignProgress));
		final Path foreignProgressFile = directory.resolve("foreign-progress.json");
		io.write(foreignProgressFile, foreignProgressReport);
		assertThrows(com.sqlapp.exceptions.CommandException.class,
				() -> io.read(foreignProgressFile));
	}

	@Test
	void conservativelyAssessesResumeReadiness() {
		final BulkMigrationJobPlan plan = plan();
		final var builder = new BulkMigrationOperationalReportBuilder(
				Clock.fixed(Instant.parse("2026-08-31T11:00:00Z"), ZoneOffset.UTC));
		final var notStarted = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, null)));
		final var report = builder.build(plan, notStarted, null, null);
		assertEquals(BulkMigrationResumeReadiness.RESUMABLE,
				BulkMigrationOperationalReportResumeAssessor.assess(report));

		final var started = copyWithExecution(report,
				new BulkMigrationOperationalReport.Execution("JOB_STARTED", null,
						report.generatedAt(), null, null, null));
		assertEquals(BulkMigrationResumeReadiness.POSSIBLY_RUNNING,
				BulkMigrationOperationalReportResumeAssessor.assess(started));

		final var prepared = builder.build(plan, notStarted,
				new BulkMigrationMaintenanceState(plan.getFingerprint(),
						BulkMigrationMaintenanceStatus.PREPARED, report.generatedAt(), null),
				null);
		assertEquals(BulkMigrationResumeReadiness.RECOVERY_REQUIRED,
				BulkMigrationOperationalReportResumeAssessor.assess(prepared));

		final var incompatible = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.INCOMPATIBLE, null)));
		assertEquals(BulkMigrationResumeReadiness.INCOMPATIBLE,
				BulkMigrationOperationalReportResumeAssessor.assess(
						builder.build(plan, incompatible, null, null)));

		final var complete = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.COMPLETE, null)));
		final var completeReport = builder.build(plan, complete, null, null);
		assertEquals(BulkMigrationResumeReadiness.COMPLETE,
				BulkMigrationOperationalReportResumeAssessor.assess(completeReport));
		final var completedTaskOnly = copyWithExecution(completeReport,
				new BulkMigrationOperationalReport.Execution("TASK_COMPLETED", "customers",
						completeReport.generatedAt(), 0L, null, null));
		assertEquals(BulkMigrationResumeReadiness.POSSIBLY_RUNNING,
				BulkMigrationOperationalReportResumeAssessor.assess(completedTaskOnly));
		assertEquals(BulkMigrationResumeReadiness.RESUMABLE,
				BulkMigrationOperationalReportResumeAssessor.assess(completedTaskOnly,
						null, completeReport.generatedAt()));

		final Path file = directory.resolve("resume-assessment.json");
		final var io = new BulkMigrationOperationalReportIO();
		io.write(file, started);
		assertEquals(BulkMigrationResumeReadiness.POSSIBLY_RUNNING,
				io.assessResume(file, plan.getFingerprint()));
	}

	@Test
	void currentLeaseResolvesPossiblyRunningReport() throws Exception {
		final Instant now = Instant.parse("2026-08-31T12:00:00Z");
		final BulkMigrationJobPlan plan = plan();
		final var status = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.IN_PROGRESS, null)));
		final var execution = new BulkMigrationOperationalReport.Execution(
				"TASK_STARTED", "customers", now.minusSeconds(10), 0L,
				null, null);
		final var report = new BulkMigrationOperationalReportBuilder(
				Clock.fixed(now, ZoneOffset.UTC)).build(plan, status, null, null,
				execution);
		final Path file = directory.resolve("lease-aware-report.json");
		final var io = new BulkMigrationOperationalReportIO();
		io.write(file, report);
		final var store = new InMemoryBulkMigrationJobLeaseStore();

		assertEquals(BulkMigrationResumeReadiness.RESUMABLE,
				io.assessResume(file, plan.getFingerprint(), store, now));
		store.tryAcquire(new BulkMigrationJobLease(plan.getFingerprint(), "worker",
				now.plusSeconds(30)), now);
		assertEquals(BulkMigrationResumeReadiness.POSSIBLY_RUNNING,
				io.assessResume(file, plan.getFingerprint(), store, now));
		assertEquals(BulkMigrationResumeReadiness.RESUMABLE,
				io.assessResume(file, plan.getFingerprint(), store,
						now.plusSeconds(30)));
		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationOperationalReportResumeAssessor.assess(report,
						new BulkMigrationJobLease("other", "worker", now.plusSeconds(1)),
						now));
	}

	@Test
	void rejectsStatusAndMaintenanceFromAnotherPlan() {
		final BulkMigrationJobPlan plan = plan();
		final BulkMigrationJobStatus wrongStatus = new BulkMigrationJobStatus("other",
				List.of());
		final var builder = new BulkMigrationOperationalReportBuilder();
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, wrongStatus, null, null));

		final BulkMigrationJobStatus status = new BulkMigrationJobStatus(
				plan.getFingerprint(), List.of());
		final var wrongMaintenance = new BulkMigrationMaintenanceState("other",
				BulkMigrationMaintenanceStatus.PREPARED, Instant.now(), null);
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, status, wrongMaintenance, null));
	}

	@Test
	void rejectsForeignTasksCheckpointsAndProgress() {
		final BulkMigrationJobPlan plan = plan();
		final var builder = new BulkMigrationOperationalReportBuilder();
		assertThrows(IllegalArgumentException.class, () -> builder.build(plan,
				new BulkMigrationJobStatus(plan.getFingerprint(), List.of()), null, null));

		final var foreignCheckpoint = BulkMigrationCheckpoint.builder()
				.migrationId("other").processedRows(0).build();
		final var status = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, foreignCheckpoint)));
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, status, null, null));

		final var correctStatus = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, null)));
		final var foreignProgress = new BulkMigrationProgressSnapshot("other", 0, null,
				Duration.ZERO, 0, null, null);
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, correctStatus, null, foreignProgress));
		final var foreignExecution = new BulkMigrationOperationalReport.Execution(
				"TASK_STARTED", "other", Instant.now(), null, null, null);
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, correctStatus, null, null, foreignExecution));
		assertThrows(IllegalArgumentException.class, () -> builder.build(plan,
				correctStatus, null, null, Map.of("other", foreignProgress), null));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationOperationalReport.Execution("UNKNOWN", "customers",
						Instant.now(), null, null, null));
	}

	@Test
	void commandWritesReportAndValidatesRequiredValues() {
		final BulkMigrationJobPlan plan = plan();
		final var status = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, null)));
		final Path target = directory.resolve("command-report.json");
		final var command = new GenerateBulkMigrationOperationalReportCommand();
		command.setPlan(plan);
		command.setStatus(status);
		command.setTargetFile(target.toFile());

		command.run();

		assertTrue(target.toFile().isFile());
		assertNotNull(new JsonConverter().fromJsonString(target.toFile(), Map.class)
				.get("generatedAt"));
		assertThrows(RuntimeException.class,
				new GenerateBulkMigrationOperationalReportCommand()::run);
	}

	@Test
	@SuppressWarnings("unchecked")
	void jobListenerRefreshesReportFromDurableCheckpointState() throws Exception {
		final var store = new InMemoryBulkMigrationCheckpointStore();
		final BulkMigrationJobPlan plan = plan(store);
		final Path target = directory.resolve("live-status.json");
		final var listener = new BulkMigrationOperationalReportJobListener(plan, target);

		listener.onTaskStarted("customers", 0, 1);
		Map<String, Object> json = new JsonConverter().fromJsonString(target.toFile(), Map.class);
		List<Map<String, Object>> tasks = (List<Map<String, Object>>) json.get("tasks");
		assertEquals("NOT_STARTED", tasks.get(0).get("state"));

		store.save(BulkMigrationCheckpoint.builder().migrationId("顧客移行")
				.sourceFingerprint("source").targetFingerprint("target")
				.processedRows(25).completedChunks(1).chunkSize(10_000)
				.lastChunkHash("durable-hash").complete(true).build());
		listener.onTaskCompleted("customers",
				new ChunkedBulkMigrationResult(0, 25, 1, false), 0, 1);

		json = new JsonConverter().fromJsonString(target.toFile(), Map.class);
		tasks = (List<Map<String, Object>>) json.get("tasks");
		assertEquals("COMPLETE", tasks.get(0).get("state"));
		assertEquals(25, ((Number) json.get("processedRows")).longValue());
		final Map<String, Object> execution =
				(Map<String, Object>) json.get("execution");
		assertEquals("TASK_COMPLETED", execution.get("event"));
		assertEquals(25, ((Number) execution.get("processedRows")).longValue());

		listener.onJobCompleted(new BulkMigrationJobResult(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskResult("customers",
						new ChunkedBulkMigrationResult(0, 25, 1, false)))));
		json = new JsonConverter().fromJsonString(target.toFile(), Map.class);
		final Map<String, Object> jobExecution =
				(Map<String, Object>) json.get("execution");
		assertEquals("JOB_COMPLETED", jobExecution.get("event"));
		assertNull(jobExecution.get("taskId"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void chunkListenerPublishesDurableProgressWithinALongTask() throws Exception {
		final var store = new InMemoryBulkMigrationCheckpointStore();
		final BulkMigrationJobPlan plan = plan(store);
		final var latest = new AtomicReference<BulkMigrationProgressSnapshot>();
		final Path target = directory.resolve("chunk-status.json");
		final var jobListener = new BulkMigrationOperationalReportJobListener(plan,
				target, () -> null, latest::get);
		final var chunkListener =
				new BulkMigrationOperationalReportChunkListener(jobListener);
		jobListener.onJobStarted(plan.getFingerprint(), 1);

		store.save(BulkMigrationCheckpoint.builder().migrationId("顧客移行")
				.sourceFingerprint("source").targetFingerprint("target")
				.processedRows(25).completedChunks(1).chunkSize(10_000)
				.lastChunkHash("durable-hash").complete(false).build());
		latest.set(new BulkMigrationProgressSnapshot("顧客移行", 25, 100L,
				Duration.ofSeconds(5), 5, 0.25, Duration.ofSeconds(15)));
		chunkListener.onChunkCompleted(
				new com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress(
						"顧客移行", 0, 25, 0, 25));

		final Map<String, Object> json = new JsonConverter().fromJsonString(
				target.toFile(), Map.class);
		final Map<String, Object> progress =
				(Map<String, Object>) json.get("progress");
		assertEquals(25, ((Number) progress.get("processedRows")).longValue());
		final List<Map<String, Object>> tasks =
				(List<Map<String, Object>>) json.get("tasks");
		assertEquals("IN_PROGRESS", tasks.get(0).get("state"));
		final Map<String, Object> execution =
				(Map<String, Object>) json.get("execution");
		assertEquals("JOB_STARTED", execution.get("event"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void jobListenerCanContinueAfterObservableReportFailure() {
		final var delegate = new InMemoryBulkMigrationCheckpointStore();
		final var failing = new AtomicBoolean(true);
		final BulkMigrationCheckpointStore store = new BulkMigrationCheckpointStore() {
			@Override
			public Optional<BulkMigrationCheckpoint> load(String migrationId)
					throws java.sql.SQLException {
				if (failing.get()) {
					throw new java.sql.SQLException("status unavailable");
				}
				return delegate.load(migrationId);
			}

			@Override
			public void save(BulkMigrationCheckpoint checkpoint) throws java.sql.SQLException {
				delegate.save(checkpoint);
			}

			@Override
			public void delete(String migrationId) throws java.sql.SQLException {
				delegate.delete(migrationId);
			}
		};
		final BulkMigrationJobPlan plan = plan(store);
		final var failures = new java.util.ArrayList<RuntimeException>();
		final var listener = new BulkMigrationOperationalReportJobListener(plan,
				directory.resolve("best-effort.json"), () -> null, () -> null,
				BulkMigrationOperationalReportFailurePolicy.CONTINUE_JOB, failures::add);

		listener.onTaskStarted("customers", 0, 1);
		assertEquals(1, failures.size());
		assertNotNull(listener.getLastFailure());

		failing.set(false);
		listener.onTaskStarted("customers", 0, 1);
		assertNull(listener.getLastFailure());
		assertTrue(directory.resolve("best-effort.json").toFile().isFile());
		listener.onTaskFailed("customers", new java.sql.SQLException("write failed"), 0, 1);
		final Map<String, Object> failedJson = new JsonConverter().fromJsonString(
				directory.resolve("best-effort.json").toFile(), Map.class);
		final Map<String, Object> execution =
				(Map<String, Object>) failedJson.get("execution");
		assertEquals("TASK_FAILED", execution.get("event"));
		assertEquals("write failed", execution.get("failureMessage"));

		failing.set(true);
		final var strict = new BulkMigrationOperationalReportJobListener(plan,
				directory.resolve("strict.json"));
		assertThrows(RuntimeException.class,
				() -> strict.onTaskStarted("customers", 0, 1));
	}

	private static BulkMigrationJobPlan plan() {
		return plan(null);
	}

	private static BulkMigrationOperationalReport copyWith(
			final BulkMigrationOperationalReport source, final int formatVersion,
			final int totalTasks) {
		return new BulkMigrationOperationalReport(formatVersion, source.generatedAt(),
				source.planFingerprint(), source.compatible(), source.processedRows(),
				source.completedTasks(), totalTasks, source.tasks(), source.operations(),
				source.maintenance(), source.progress(), source.progressByMigration(),
				source.execution());
	}

	private static BulkMigrationOperationalReport copyWithContent(
			final BulkMigrationOperationalReport source,
			final List<BulkMigrationOperationalReport.Task> tasks,
			final List<BulkMigrationOperationalReport.Progress> progressByMigration) {
		return new BulkMigrationOperationalReport(source.formatVersion(),
				source.generatedAt(), source.planFingerprint(), source.compatible(),
				source.processedRows(), source.completedTasks(), tasks.size(), tasks,
				source.operations(), source.maintenance(), source.progress(),
				progressByMigration, source.execution());
	}

	private static BulkMigrationOperationalReport copyWithExecution(
			final BulkMigrationOperationalReport source,
			final BulkMigrationOperationalReport.Execution execution) {
		return new BulkMigrationOperationalReport(source.formatVersion(),
				source.generatedAt(), source.planFingerprint(), source.compatible(),
				source.processedRows(), source.completedTasks(), source.totalTasks(),
				source.tasks(), source.operations(), source.maintenance(), source.progress(),
				source.progressByMigration(), execution);
	}

	private static BulkMigrationJobPlan plan(
			final BulkMigrationCheckpointStore checkpointStore) {
		final Table table = new Table("CUSTOMERS");
		table.getColumns().add(new Column("ID"));
		table.setPrimaryKey("PK_CUSTOMERS", table.getColumns().get("ID"));
		final var taskBuilder = BulkMigrationJobTask.builder().taskId("customers")
				.sourceTable(table).options(ChunkedBulkMigrationOption.builder()
						.migrationId("顧客移行").sourceFingerprint("source")
						.targetFingerprint("target").build());
		if (checkpointStore != null) {
			taskBuilder.checkpointStore(checkpointStore);
		}
		final var task = taskBuilder.build();
		final BulkMigrationJobLifecycle lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "test-lifecycle-v1";
			}

			@Override
			public List<BulkMigrationJobOperation> plan(List<BulkMigrationJobTask> tasks) {
				return List.of(new BulkMigrationJobOperation("DISABLE_CONSTRAINTS",
						BulkMigrationJobOperationPhase.BEFORE, "Disable constraints", true));
			}
		};
		return BulkMigrationJobPlanner.plan(List.of(task), lifecycle);
	}
}
