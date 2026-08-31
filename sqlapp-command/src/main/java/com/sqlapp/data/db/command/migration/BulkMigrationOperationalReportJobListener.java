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
import com.sqlapp.jdbc.bulk.BulkMigrationJobException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatusInspector;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult;

/** Publishes a fresh read-only JSON report at job task boundaries. */
public final class BulkMigrationOperationalReportJobListener
		implements BulkMigrationJobListener {
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

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile) {
		this(plan, targetFile, () -> null, () -> null);
	}

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier) {
		this(plan, targetFile, maintenanceSupplier, progressSupplier,
				() -> Map.of(), BulkMigrationOperationalReportFailurePolicy.FAIL_JOB,
				failure -> { });
	}

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier,
			final BulkMigrationOperationalReportFailurePolicy failurePolicy,
			final Consumer<RuntimeException> failureConsumer) {
		this(plan, targetFile, maintenanceSupplier, progressSupplier, () -> Map.of(),
				failurePolicy,
				failureConsumer,
				new BulkMigrationOperationalReportBuilder(),
				new BulkMigrationOperationalReportIO());
	}

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier,
			final Supplier<Map<String, BulkMigrationProgressSnapshot>> progressSnapshotsSupplier,
			final BulkMigrationOperationalReportFailurePolicy failurePolicy,
			final Consumer<RuntimeException> failureConsumer) {
		this(plan, targetFile, maintenanceSupplier, progressSupplier,
				progressSnapshotsSupplier, failurePolicy, failureConsumer,
				new BulkMigrationOperationalReportBuilder(),
				new BulkMigrationOperationalReportIO());
	}

	BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier,
			final Supplier<Map<String, BulkMigrationProgressSnapshot>> progressSnapshotsSupplier,
			final BulkMigrationOperationalReportFailurePolicy failurePolicy,
			final Consumer<RuntimeException> failureConsumer,
			final BulkMigrationOperationalReportBuilder builder,
			final BulkMigrationOperationalReportIO reportIO) {
		this.plan = Objects.requireNonNull(plan, "plan");
		this.targetFile = Objects.requireNonNull(targetFile, "targetFile");
		this.maintenanceSupplier = maintenanceSupplier == null ? () -> null
				: maintenanceSupplier;
		this.progressSupplier = progressSupplier == null ? () -> null : progressSupplier;
		this.progressSnapshotsSupplier = progressSnapshotsSupplier == null
				? () -> Map.of() : progressSnapshotsSupplier;
		this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
		this.failureConsumer = failureConsumer == null ? failure -> { } : failureConsumer;
		this.builder = Objects.requireNonNull(builder, "builder");
		this.reportIO = Objects.requireNonNull(reportIO, "reportIO");
	}

	@Override
	public void onJobStarted(final String planFingerprint, final int taskCount) {
		publishBoundary(execution("JOB_STARTED", null, null, null));
	}

	@Override
	public void onJobCompleted(final BulkMigrationJobResult result) {
		publishBoundary(execution("JOB_COMPLETED", null,
				result == null ? null : result.getProcessedRows(), null));
	}

	@Override
	public void onJobFailed(final String planFingerprint, final Throwable cause) {
		final String taskId = cause instanceof BulkMigrationJobException jobFailure
				? jobFailure.getFailedTaskId() : null;
		publishBoundary(execution("JOB_FAILED", taskId, null, cause));
	}

	@Override
	public void onJobPaused(final String planFingerprint, final String taskId,
			final ChunkedBulkMigrationProgress progress) {
		publishBoundary(execution("JOB_PAUSED", taskId,
				progress == null ? null : progress.getProcessedRowsAfter(), null));
	}

	@Override
	public void onTaskStarted(final String taskId, final int taskIndex,
			final int taskCount) {
		publishBoundary(execution("TASK_STARTED", taskId, null, null));
	}

	@Override
	public void onTaskCompleted(final String taskId,
			final ChunkedBulkMigrationResult result, final int taskIndex,
			final int taskCount) {
		publishBoundary(execution("TASK_COMPLETED", taskId,
				result == null ? null : result.getPreviouslyProcessedRows()
						+ result.getProcessedRows(), null));
	}

	@Override
	public void onTaskFailed(final String taskId, final SQLException cause,
			final int taskIndex, final int taskCount) {
		publishBoundary(execution("TASK_FAILED", taskId, null, cause));
	}

	@Override
	public void onTaskPaused(final String taskId,
			final ChunkedBulkMigrationProgress progress, final int taskIndex,
			final int taskCount) {
		publishBoundary(execution("TASK_PAUSED", taskId,
				progress == null ? null : progress.getProcessedRowsAfter(), null));
	}

	private synchronized void publishBoundary(
			final BulkMigrationOperationalReport.Execution execution) {
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

	/** Writes a report immediately without running or changing the job. */
	public synchronized BulkMigrationOperationalReport publish() {
		try {
			final var status = BulkMigrationJobStatusInspector.inspect(plan);
			final var report = builder.build(plan, status, maintenanceSupplier.get(),
					progressSupplier.get(), progressSnapshotsSupplier.get(), latestExecution);
			reportIO.write(targetFile, report);
			return report;
		} catch (SQLException e) {
			throw new CommandException("Failed to inspect bulk migration status for report", e);
		}
	}

	private static BulkMigrationOperationalReport.Execution execution(
			final String event, final String taskId, final Long processedRows,
			final Throwable failure) {
		return new BulkMigrationOperationalReport.Execution(event, taskId, Instant.now(),
				processedRows, failure == null ? null : failure.getClass().getName(),
				failure == null ? null : failureMessage(failure));
	}

	private static String failureMessage(final Throwable failure) {
		final String raw = failure.getMessage();
		if (raw == null) {
			return null;
		}
		return raw.length() <= BulkMigrationOperationalReport.Execution.FAILURE_MESSAGE_MAX_LENGTH
				? raw : raw.substring(0,
						BulkMigrationOperationalReport.Execution.FAILURE_MESSAGE_MAX_LENGTH);
	}
}
