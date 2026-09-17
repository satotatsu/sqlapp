/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.Map;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLease;
import com.sqlapp.jdbc.bulk.BulkMigrationJobException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult;

/** Publishes a fresh read-only JSON report at job task boundaries. */
public final class BulkMigrationOperationalReportJobListener implements BulkMigrationJobListener {
	private final BulkMigrationJobPlan plan;
	private final Path targetFile;
	private final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier;
	private final Supplier<BulkMigrationProgressSnapshot> progressSupplier;
	private final Supplier<Map<String, BulkMigrationProgressSnapshot>> progressSnapshotsSupplier;
	private final BulkMigrationOperationalReportBuilder builder;
	private final BulkMigrationOperationalReportIO reportIO;
	private final BulkMigrationOperationalReportFailurePolicy failurePolicy;
	private final Consumer<RuntimeException> failureConsumer;
	private volatile RuntimeException lastFailure;
	private volatile BulkMigrationOperationalReport.Execution latestExecution;
	private volatile String leaseAcquisitionId;
	private volatile boolean leaseAcquiredForAttempt;

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan, final Path targetFile) {
		this(plan, targetFile, () -> null, () -> null);
	}

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan, final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier) {
		this(plan, targetFile, maintenanceSupplier, progressSupplier, () -> Map.of(),
				BulkMigrationOperationalReportFailurePolicy.FAIL_JOB, failure -> {
				});
	}

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan, final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier,
			final BulkMigrationOperationalReportFailurePolicy failurePolicy,
			final Consumer<RuntimeException> failureConsumer) {
		this(plan, targetFile, maintenanceSupplier, progressSupplier, () -> Map.of(), failurePolicy, failureConsumer,
				new BulkMigrationOperationalReportBuilder(), new BulkMigrationOperationalReportIO());
	}

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan, final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier,
			final Supplier<Map<String, BulkMigrationProgressSnapshot>> progressSnapshotsSupplier,
			final BulkMigrationOperationalReportFailurePolicy failurePolicy,
			final Consumer<RuntimeException> failureConsumer) {
		this(plan, targetFile, maintenanceSupplier, progressSupplier, progressSnapshotsSupplier, failurePolicy,
				failureConsumer, new BulkMigrationOperationalReportBuilder(), new BulkMigrationOperationalReportIO());
	}

	BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan, final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier,
			final Supplier<Map<String, BulkMigrationProgressSnapshot>> progressSnapshotsSupplier,
			final BulkMigrationOperationalReportFailurePolicy failurePolicy,
			final Consumer<RuntimeException> failureConsumer, final BulkMigrationOperationalReportBuilder builder,
			final BulkMigrationOperationalReportIO reportIO) {
		this.plan = Objects.requireNonNull(plan, "plan");
		this.targetFile = Objects.requireNonNull(targetFile, "targetFile");
		this.maintenanceSupplier = maintenanceSupplier == null ? () -> null : maintenanceSupplier;
		this.progressSupplier = progressSupplier == null ? () -> null : progressSupplier;
		this.progressSnapshotsSupplier = progressSnapshotsSupplier == null ? () -> Map.of() : progressSnapshotsSupplier;
		this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
		this.failureConsumer = failureConsumer == null ? failure -> {
		} : failureConsumer;
		this.builder = Objects.requireNonNull(builder, "builder");
		this.reportIO = Objects.requireNonNull(reportIO, "reportIO");
	}

	@Override
	public void onLeaseAcquired(final BulkMigrationJobLease lease) {
		Objects.requireNonNull(lease, "lease").validateAgainst(plan);
		leaseAcquisitionId = lease.acquisitionId();
		leaseAcquiredForAttempt = true;
	}

	@Override
	public void onLeaseAcquisitionFailed(final String planFingerprint, final Throwable cause) {
		requirePlanFingerprint(planFingerprint);
		leaseAcquisitionId = null;
		leaseAcquiredForAttempt = false;
	}

	@Override
	public void onJobStarted(final String planFingerprint, final int taskCount) {
		requirePlanFingerprint(planFingerprint);
		if (taskCount != plan.getTasks().size()) {
			throw new IllegalArgumentException("Event taskCount does not match the operational report plan");
		}
		if (!leaseAcquiredForAttempt) {
			leaseAcquisitionId = null;
		}
		publishBoundary(execution("JOB_STARTED", null, null, null));
	}

	@Override
	public void onJobRejected(final String planFingerprint, final Throwable cause) {
		requirePlanFingerprint(planFingerprint);
		if (!leaseAcquiredForAttempt) {
			leaseAcquisitionId = null;
		}
		terminalBoundary(execution("JOB_REJECTED", null, null, cause));
	}

	@Override
	public void onJobCompleted(final BulkMigrationJobResult result) {
		if (result != null) {
			result.validateAgainst(plan);
		}
		terminalBoundary(execution("JOB_COMPLETED", null, result == null ? null : result.getProcessedRows(), null));
	}

	@Override
	public void onJobFailed(final String planFingerprint, final Throwable cause) {
		requirePlanFingerprint(planFingerprint);
		final String taskId = cause instanceof BulkMigrationJobException jobFailure ? jobFailure.getFailedTaskId()
				: null;
		if (taskId != null) {
			requireTask(taskId);
		}
		terminalBoundary(execution("JOB_FAILED", taskId, null, cause));
	}

	@Override
	public void onJobPaused(final String planFingerprint, final String taskId,
			final ChunkedBulkMigrationProgress progress) {
		requirePlanFingerprint(planFingerprint);
		requireTask(taskId);
		terminalBoundary(
				execution("JOB_PAUSED", taskId, progress == null ? null : progress.getProcessedRowsAfter(), null));
	}

	private void terminalBoundary(final BulkMigrationOperationalReport.Execution execution) {
		try {
			publishBoundary(execution);
		} finally {
			leaseAcquiredForAttempt = false;
			leaseAcquisitionId = null;
		}
	}

	@Override
	public void onTaskStarted(final String taskId, final int taskIndex, final int taskCount) {
		requireTaskPosition(taskId, taskIndex, taskCount);
		publishBoundary(execution("TASK_STARTED", taskId, null, null));
	}

	@Override
	public void onTaskCompleted(final String taskId, final ChunkedBulkMigrationResult result, final int taskIndex,
			final int taskCount) {
		requireTaskPosition(taskId, taskIndex, taskCount);
		publishBoundary(execution("TASK_COMPLETED", taskId,
				result == null ? null : result.getPreviouslyProcessedRows() + result.getProcessedRows(), null));
	}

	@Override
	public void onTaskFailed(final String taskId, final SQLException cause, final int taskIndex, final int taskCount) {
		requireTaskPosition(taskId, taskIndex, taskCount);
		publishBoundary(execution("TASK_FAILED", taskId, null, cause));
	}

	@Override
	public void onTaskPaused(final String taskId, final ChunkedBulkMigrationProgress progress, final int taskIndex,
			final int taskCount) {
		requireTaskPosition(taskId, taskIndex, taskCount);
		publishBoundary(
				execution("TASK_PAUSED", taskId, progress == null ? null : progress.getProcessedRowsAfter(), null));
	}

	private synchronized void publishBoundary(final BulkMigrationOperationalReport.Execution execution) {
		latestExecution = execution;
		try {
			publish();
			lastFailure = null;
		} catch (RuntimeException failure) {
			lastFailure = failure;
			try {
				failureConsumer.accept(failure);
			} catch (RuntimeException consumerFailure) {
				failure.addSuppressed(consumerFailure);
			}
			if (failurePolicy == BulkMigrationOperationalReportFailurePolicy.FAIL_JOB) {
				throw failure;
			}
		}
	}

	public RuntimeException getLastFailure() {
		return lastFailure;
	}

	public BulkMigrationOperationalReport.Execution getLatestExecution() {
		return latestExecution;
	}

	/** Refreshes from an automatic boundary using the configured failure policy. */
	public void refresh() {
		publishBoundary(latestExecution);
	}

	/** Refreshes after a durable chunk belonging to this listener's plan. */
	public void refreshAfterChunk(final ChunkedBulkMigrationProgress progress) {
		Objects.requireNonNull(progress, "progress");
		if (plan.getTasks().stream().noneMatch(
				task -> task.getOptions().getMigrationId().equals(progress.getMigrationId()))) {
			throw new IllegalArgumentException("Chunk migrationId does not belong to the operational report plan");
		}
		refresh();
	}

	/** Writes a report immediately without running or changing the job. */
	public synchronized BulkMigrationOperationalReport publish() {
		try {
			final var status = BulkMigrationJobStatusInspector.inspect(plan);
			final var report = builder.build(plan, status, maintenanceSupplier.get(), progressSupplier.get(),
					progressSnapshotsSupplier.get(), latestExecution);
			reportIO.write(targetFile, report);
			return report;
		} catch (SQLException e) {
			throw new CommandException("Failed to inspect bulk migration status for report", e);
		}
	}

	private BulkMigrationOperationalReport.Execution execution(final String event, final String taskId,
			final Long processedRows, final Throwable failure) {
		return new BulkMigrationOperationalReport.Execution(event, taskId, Instant.now(), processedRows,
				leaseAcquisitionId,
				failure == null ? null : failure.getClass().getName(),
				failure == null ? null : failureMessage(failure));
	}

	private static String failureMessage(final Throwable failure) {
		final String raw = failure.getMessage();
		if (raw == null) {
			return null;
		}
		return raw.length() <= BulkMigrationOperationalReport.Execution.FAILURE_MESSAGE_MAX_LENGTH ? raw
				: raw.substring(0, BulkMigrationOperationalReport.Execution.FAILURE_MESSAGE_MAX_LENGTH);
	}

	private void requirePlanFingerprint(final String planFingerprint) {
		if (!plan.getFingerprint().equals(planFingerprint)) {
			throw new IllegalArgumentException("Event planFingerprint does not match the operational report plan");
		}
	}

	private void requireTask(final String taskId) {
		if (taskId == null || !plan.getTaskIds().contains(taskId)) {
			throw new IllegalArgumentException("Event taskId does not belong to the operational report plan");
		}
	}

	private void requireTaskPosition(final String taskId, final int taskIndex, final int taskCount) {
		requireTask(taskId);
		if (taskCount != plan.getTasks().size() || taskIndex < 0 || taskIndex >= taskCount
				|| !plan.getTaskIds().get(taskIndex).equals(taskId)) {
			throw new IllegalArgumentException("Event task position does not match the operational report plan");
		}
	}
}
