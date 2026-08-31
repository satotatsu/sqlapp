/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobListener;
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
	private final BulkMigrationOperationalReportBuilder builder;
	private final BulkMigrationOperationalReportIO reportIO;

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile) {
		this(plan, targetFile, () -> null, () -> null);
	}

	public BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier) {
		this(plan, targetFile, maintenanceSupplier, progressSupplier,
				new BulkMigrationOperationalReportBuilder(),
				new BulkMigrationOperationalReportIO());
	}

	BulkMigrationOperationalReportJobListener(final BulkMigrationJobPlan plan,
			final Path targetFile,
			final Supplier<BulkMigrationMaintenanceState> maintenanceSupplier,
			final Supplier<BulkMigrationProgressSnapshot> progressSupplier,
			final BulkMigrationOperationalReportBuilder builder,
			final BulkMigrationOperationalReportIO reportIO) {
		this.plan = Objects.requireNonNull(plan, "plan");
		this.targetFile = Objects.requireNonNull(targetFile, "targetFile");
		this.maintenanceSupplier = maintenanceSupplier == null ? () -> null
				: maintenanceSupplier;
		this.progressSupplier = progressSupplier == null ? () -> null : progressSupplier;
		this.builder = Objects.requireNonNull(builder, "builder");
		this.reportIO = Objects.requireNonNull(reportIO, "reportIO");
	}

	@Override
	public void onTaskStarted(final String taskId, final int taskIndex,
			final int taskCount) {
		publish();
	}

	@Override
	public void onTaskCompleted(final String taskId,
			final ChunkedBulkMigrationResult result, final int taskIndex,
			final int taskCount) {
		publish();
	}

	@Override
	public void onTaskFailed(final String taskId, final SQLException cause,
			final int taskIndex, final int taskCount) {
		publish();
	}

	@Override
	public void onTaskPaused(final String taskId,
			final ChunkedBulkMigrationProgress progress, final int taskIndex,
			final int taskCount) {
		publish();
	}

	/** Writes a report immediately without running or changing the job. */
	public synchronized BulkMigrationOperationalReport publish() {
		try {
			final var status = BulkMigrationJobStatusInspector.inspect(plan);
			final var report = builder.build(plan, status, maintenanceSupplier.get(),
					progressSupplier.get());
			reportIO.write(targetFile, report);
			return report;
		} catch (SQLException e) {
			throw new CommandException("Failed to inspect bulk migration status for report", e);
		}
	}
}
