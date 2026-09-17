/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseStore;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;
import com.sqlapp.util.JsonConverter;

/** Atomically writes bulk migration operational reports as UTF-8 JSON. */
public final class BulkMigrationOperationalReportIO {
	private final JsonConverter converter;

	public BulkMigrationOperationalReportIO() {
		this(new JsonConverter());
	}

	BulkMigrationOperationalReportIO(final JsonConverter converter) {
		this.converter = Objects.requireNonNull(converter, "converter");
		this.converter.setIndentOutput(true);
	}

	public BulkMigrationOperationalReport read(final Path file) {
		Objects.requireNonNull(file, "file");
		final Path absolute = file.toAbsolutePath();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration report does not exist: " + absolute);
		}
		try {
			return validate(converter.fromJsonString(absolute.toFile(), BulkMigrationOperationalReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration report: " + absolute, e);
		}
	}

	public BulkMigrationOperationalReport read(final Path file, final String expectedPlanFingerprint) {
		if (expectedPlanFingerprint == null || expectedPlanFingerprint.isBlank()) {
			throw new IllegalArgumentException("expectedPlanFingerprint must not be empty");
		}
		final BulkMigrationOperationalReport report = read(file);
		if (!expectedPlanFingerprint.equals(report.planFingerprint())) {
			throw new CommandException("Bulk migration report plan fingerprint mismatch");
		}
		return report;
	}

	public BulkMigrationResumeReadiness assessResume(final Path file, final String expectedPlanFingerprint) {
		return BulkMigrationOperationalReportResumeAssessor.assess(read(file, expectedPlanFingerprint));
	}

	public BulkMigrationResumeReadiness assessResume(final Path file, final String expectedPlanFingerprint,
			final BulkMigrationJobLeaseStore leaseStore, final Instant now) throws SQLException {
		Objects.requireNonNull(leaseStore, "leaseStore");
		Objects.requireNonNull(now, "now");
		final BulkMigrationOperationalReport report = read(file, expectedPlanFingerprint);
		return BulkMigrationOperationalReportResumeAssessor.assess(report, leaseStore.load(report.jobId()).orElse(null),
				now);
	}

	public void write(final Path file, final BulkMigrationOperationalReport report) {
		Objects.requireNonNull(file, "file");
		validate(Objects.requireNonNull(report, "report"));
		final Path absolute = file.toAbsolutePath();
		try {
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration report: " + absolute, e);
		}
	}

	private static BulkMigrationOperationalReport validate(final BulkMigrationOperationalReport report) {
		if (report == null) {
			throw new CommandException("Bulk migration report must not be null");
		}
		if (report.formatVersion() != BulkMigrationOperationalReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported bulk migration report formatVersion: " + report.formatVersion());
		}
		if (report.generatedAt() == null || report.jobId() == null || report.jobId().isBlank()
				|| report.planFingerprint() == null || report.planFingerprint().isBlank()) {
			throw new CommandException("Bulk migration report requires generatedAt, jobId and planFingerprint");
		}
		if (report.processedRows() < 0 || report.completedTasks() < 0 || report.totalTasks() < 0
				|| report.completedTasks() > report.totalTasks()) {
			throw new CommandException("Bulk migration report contains invalid aggregate counts");
		}
		if (report.tasks() == null || report.operations() == null || report.progressByMigration() == null
				|| report.tasks().size() != report.totalTasks()) {
			throw new CommandException("Bulk migration report contains invalid task or operation lists");
		}
		if (report.tasks().stream().anyMatch(task -> task == null || task.taskId() == null || task.taskId().isBlank()
				|| task.migrationId() == null || task.migrationId().isBlank())) {
			throw new CommandException("Bulk migration report contains an invalid task identity");
		}
		final Set<String> taskIds = new HashSet<>();
		final Set<String> migrationIds = new HashSet<>();
		long processedRows = 0;
		long completedTasks = 0;
		boolean compatible = true;
		for (BulkMigrationOperationalReport.Task task : report.tasks()) {
			final BulkMigrationJobTaskState state = task.state();
			if (state == null) {
				throw new CommandException("Bulk migration report contains an unknown task state");
			}
			if (task.tableName() == null || task.tableName().isBlank() || task.chunkSize() <= 0
					|| task.mode() == null || task.checkpointMode() == null) {
				throw new CommandException("Bulk migration report contains invalid task configuration");
			}
			if (!taskIds.add(task.taskId()) || !migrationIds.add(task.migrationId())) {
				throw new CommandException("Bulk migration report contains duplicate task identities");
			}
			if (task.checkpoint() != null) {
				if (!task.migrationId().equals(task.checkpoint().migrationId())) {
					throw new CommandException("Bulk migration report checkpoint migrationId mismatch");
				}
				validateCheckpoint(task.checkpoint());
				try {
					processedRows = Math.addExact(processedRows, task.checkpoint().processedRows());
				} catch (ArithmeticException e) {
					throw new CommandException("Bulk migration report processed row count overflow", e);
				}
			}
			validateTaskState(task, state);
			if (state == BulkMigrationJobTaskState.COMPLETE) {
				completedTasks++;
			}
			compatible &= state != BulkMigrationJobTaskState.INCOMPATIBLE;
		}
		if (processedRows != report.processedRows() || completedTasks != report.completedTasks()
				|| compatible != report.compatible()) {
			throw new CommandException("Bulk migration report aggregate values are inconsistent");
		}
		final Set<String> operationIds = new HashSet<>();
		for (final BulkMigrationOperationalReport.Operation operation : report.operations()) {
			if (operation == null || operation.id() == null || operation.id().isBlank()
					|| !operationIds.add(operation.id()) || operation.description() == null
					|| operation.description().isBlank() || operation.phase() == null) {
				throw new CommandException("Bulk migration report contains an invalid operation");
			}
		}
		final Set<String> progressMigrationIds = new HashSet<>();
		for (BulkMigrationOperationalReport.Progress progress : report.progressByMigration()) {
			if (progress == null || progress.migrationId() == null || !migrationIds.contains(progress.migrationId())
					|| !progressMigrationIds.add(progress.migrationId())) {
				throw new CommandException("Bulk migration report contains invalid progress identities");
			}
			validateProgress(progress);
			validateProgressCheckpoint(report, progress);
		}
		if (report.progress() != null && !migrationIds.contains(report.progress().migrationId())) {
			throw new CommandException("Bulk migration report current progress migrationId mismatch");
		}
		if (report.progress() != null) {
			validateProgress(report.progress());
			validateProgressCheckpoint(report, report.progress());
			final BulkMigrationOperationalReport.Progress listed = report.progressByMigration().stream()
					.filter(value -> value.migrationId().equals(report.progress().migrationId()))
					.findFirst().orElse(null);
			if (listed != null && !listed.equals(report.progress())) {
				throw new CommandException("Bulk migration report current progress disagrees with progressByMigration");
			}
		}
		if (report.maintenance() != null) {
			if (report.maintenance().planFingerprint() == null || report.maintenance().planFingerprint().isBlank()) {
				throw new CommandException("Bulk migration report maintenance requires planFingerprint");
			}
			if (report.maintenance().status() == null) {
				throw new CommandException("Bulk migration report contains an unknown maintenance status");
			}
			if (!report.planFingerprint().equals(report.maintenance().planFingerprint())
					|| report.maintenance().updatedAt() == null) {
				throw new CommandException("Bulk migration report maintenance does not match the report plan");
			}
			if (report.maintenance().updatedAt().isAfter(report.generatedAt())) {
				throw new CommandException("Bulk migration report maintenance postdates report generation");
			}
			try {
				new com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState(report.jobId(),
						report.maintenance().planFingerprint(),
						report.maintenance().status(),
						report.maintenance().updatedAt(), report.maintenance().failureMessage());
			} catch (IllegalArgumentException | NullPointerException e) {
				throw new CommandException("Bulk migration report maintenance is invalid", e);
			}
		}
		if (report.execution() != null && report.execution().taskId() != null
				&& !taskIds.contains(report.execution().taskId())) {
			throw new CommandException("Bulk migration report execution taskId does not belong to the report");
		}
		if (report.execution() != null && report.execution().leaseAcquisitionId() != null
				&& (report.execution().leaseAcquisitionId().isBlank()
						|| report.execution().leaseAcquisitionId().length() > com.sqlapp.jdbc.bulk.BulkMigrationJobLease.ID_MAX_LENGTH)) {
			throw new CommandException("Bulk migration report execution leaseAcquisitionId is invalid");
		}
		if (report.execution() != null && report.execution().occurredAt().isAfter(report.generatedAt())) {
			throw new CommandException("Bulk migration report execution postdates report generation");
		}
		validateExecution(report);
		return report;
	}

	private static void validateTaskState(final BulkMigrationOperationalReport.Task task,
			final BulkMigrationJobTaskState state) {
		final BulkMigrationOperationalReport.Checkpoint checkpoint = task.checkpoint();
		if (state == BulkMigrationJobTaskState.NOT_STARTED && checkpoint != null) {
			throw new CommandException("Bulk migration report NOT_STARTED task must not have a checkpoint");
		}
		if (state != BulkMigrationJobTaskState.NOT_STARTED && checkpoint == null) {
			throw new CommandException("Bulk migration report " + state + " task requires a checkpoint");
		}
		if (state == BulkMigrationJobTaskState.COMPLETE && !checkpoint.complete()) {
			throw new CommandException("Bulk migration report COMPLETE task requires a complete checkpoint");
		}
		if (state == BulkMigrationJobTaskState.IN_PROGRESS && checkpoint.complete()) {
			throw new CommandException("Bulk migration report IN_PROGRESS task cannot have a complete checkpoint");
		}
	}

	private static void validateExecution(final BulkMigrationOperationalReport report) {
		final BulkMigrationOperationalReport.Execution execution = report.execution();
		if (execution == null) {
			return;
		}
		switch (execution.event()) {
		case JOB_COMPLETED -> validateCompletedJob(report, execution);
		case TASK_COMPLETED -> validateCompletedTask(report, execution);
		case TASK_PAUSED, JOB_PAUSED -> validatePausedTask(report, execution);
		case TASK_FAILED -> validateFailedTask(report, execution);
		case JOB_FAILED -> {
			if (execution.taskId() != null) {
				validateFailedTask(report, execution);
			}
		}
		case TASK_STARTED -> {
			final BulkMigrationOperationalReport.Task task = task(report, execution.taskId());
			if (task.state() == BulkMigrationJobTaskState.INCOMPATIBLE) {
				throw new CommandException("Bulk migration report TASK_STARTED refers to an incompatible task");
			}
		}
		case JOB_STARTED, JOB_REJECTED -> {
			// No additional state transition evidence is required.
		}
		}
	}

	private static void validateCompletedJob(final BulkMigrationOperationalReport report,
			final BulkMigrationOperationalReport.Execution execution) {
			if (report.completedTasks() != report.totalTasks()) {
				throw new CommandException("Bulk migration report JOB_COMPLETED requires every task to be complete");
			}
			if (execution.processedRows() != null && execution.processedRows() != report.processedRows()) {
				throw new CommandException("Bulk migration report JOB_COMPLETED processedRows mismatch");
			}
	}

	private static void validateCompletedTask(final BulkMigrationOperationalReport report,
			final BulkMigrationOperationalReport.Execution execution) {
			final BulkMigrationOperationalReport.Task task = task(report, execution.taskId());
			if (task.state() != BulkMigrationJobTaskState.COMPLETE) {
				throw new CommandException("Bulk migration report TASK_COMPLETED requires a complete task");
			}
			if (execution.processedRows() != null && task.checkpoint() != null
					&& execution.processedRows() != task.checkpoint().processedRows()) {
				throw new CommandException("Bulk migration report TASK_COMPLETED processedRows mismatch");
			}
	}

	private static void validatePausedTask(final BulkMigrationOperationalReport report,
			final BulkMigrationOperationalReport.Execution execution) {
			final BulkMigrationOperationalReport.Task task = task(report, execution.taskId());
			if (task.state() != BulkMigrationJobTaskState.IN_PROGRESS) {
				throw new CommandException("Bulk migration report paused event requires an IN_PROGRESS task");
			}
			if (execution.processedRows() != null && (task.checkpoint() == null
					|| execution.processedRows() != task.checkpoint().processedRows())) {
				throw new CommandException("Bulk migration report paused processedRows mismatch");
			}
	}

	private static void validateFailedTask(final BulkMigrationOperationalReport report,
			final BulkMigrationOperationalReport.Execution execution) {
			final BulkMigrationOperationalReport.Task task = task(report, execution.taskId());
			if (task.state() == BulkMigrationJobTaskState.COMPLETE
					|| task.state() == BulkMigrationJobTaskState.INCOMPATIBLE) {
				throw new CommandException("Bulk migration report failed event refers to a non-runnable task");
			}
	}

	private static BulkMigrationOperationalReport.Task task(final BulkMigrationOperationalReport report,
			final String taskId) {
		return report.tasks().stream().filter(value -> value.taskId().equals(taskId)).findFirst().orElseThrow();
	}

	private static void validateProgressCheckpoint(final BulkMigrationOperationalReport report,
			final BulkMigrationOperationalReport.Progress progress) {
		final BulkMigrationOperationalReport.Task task = report.tasks().stream()
				.filter(value -> value.migrationId().equals(progress.migrationId())).findFirst().orElseThrow();
		if (task.checkpoint() == null || progress.processedRows() > task.checkpoint().processedRows()) {
			throw new CommandException("Bulk migration report progress exceeds durable checkpoint state");
		}
	}

	private static void validateCheckpoint(final BulkMigrationOperationalReport.Checkpoint checkpoint) {
		try {
			BulkMigrationCheckpoint.builder().migrationId(checkpoint.migrationId())
					.sourceFingerprint(checkpoint.sourceFingerprint()).targetFingerprint(checkpoint.targetFingerprint())
					.processedRows(checkpoint.processedRows()).completedChunks(checkpoint.completedChunks())
					.chunkSize(checkpoint.chunkSize()).complete(checkpoint.complete())
					.lastChunkHash(checkpoint.lastChunkHash()).resumeToken(checkpoint.resumeToken()).build().validate();
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new CommandException("Bulk migration report checkpoint is invalid", e);
		}
	}

	private static void validateProgress(final BulkMigrationOperationalReport.Progress progress) {
		try {
			new BulkMigrationProgressSnapshot(progress.migrationId(), progress.processedRows(), progress.totalRows(),
					Duration.ofMillis(progress.elapsedMillis()), progress.rowsPerSecond(), progress.completionRatio(),
					progress.estimatedRemainingMillis() == null ? null
							: Duration.ofMillis(progress.estimatedRemainingMillis()));
		} catch (IllegalArgumentException | NullPointerException | ArithmeticException e) {
			throw new CommandException("Bulk migration report progress is invalid", e);
		}
	}

}
