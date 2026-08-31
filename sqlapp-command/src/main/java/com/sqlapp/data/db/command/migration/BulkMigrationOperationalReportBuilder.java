/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;

/** Builds an operational report without changing migration state. */
public final class BulkMigrationOperationalReportBuilder {
	private final Clock clock;

	public BulkMigrationOperationalReportBuilder() {
		this(Clock.systemUTC());
	}

	BulkMigrationOperationalReportBuilder(final Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public BulkMigrationOperationalReport build(final BulkMigrationJobPlan plan,
			final BulkMigrationJobStatus status,
			final BulkMigrationMaintenanceState maintenance,
			final BulkMigrationProgressSnapshot progress) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		Objects.requireNonNull(status, "status");
		if (!plan.getFingerprint().equals(status.getPlanFingerprint())) {
			throw new IllegalArgumentException("Status fingerprint does not match the migration plan");
		}
		if (maintenance != null
				&& !plan.getFingerprint().equals(maintenance.planFingerprint())) {
			throw new IllegalArgumentException(
					"Maintenance fingerprint does not match the migration plan");
		}
		if (!plan.getTaskIds().equals(status.getTasks().stream()
				.map(task -> task.getTaskId()).toList())) {
			throw new IllegalArgumentException(
					"Status tasks do not match the migration plan execution order");
		}
		for (int i = 0; i < plan.getTasks().size(); i++) {
			final var checkpoint = status.getTasks().get(i).getCheckpoint();
			if (checkpoint != null && !plan.getTasks().get(i).getOptions().getMigrationId()
					.equals(checkpoint.getMigrationId())) {
				throw new IllegalArgumentException(
						"Checkpoint migrationId does not match planned task: "
								+ plan.getTaskIds().get(i));
			}
		}
		if (progress != null && plan.getTasks().stream().noneMatch(task ->
				task.getOptions().getMigrationId().equals(progress.migrationId()))) {
			throw new IllegalArgumentException(
					"Progress migrationId does not belong to the migration plan");
		}
		final var tasks = java.util.stream.IntStream.range(0, plan.getTasks().size())
				.mapToObj(i -> {
					final var planned = plan.getTasks().get(i);
					final var current = status.getTasks().get(i);
					final Table table = planned.getSourceTable() != null
							? planned.getSourceTable() : planned.getKeysetSource().getTable();
					return new BulkMigrationOperationalReport.Task(planned.getTaskId(),
							planned.getOptions().getMigrationId(), table.getCatalogName(),
							table.getSchemaName(), table.getName(),
							planned.getOptions().getMode().name(),
							planned.getOptions().getChunkSize(),
							planned.getOptions().getCheckpointMode().name(),
							current.getState().name(), checkpoint(current.getCheckpoint()));
				}).toList();
		final var operations = plan.getOperations().stream().map(operation ->
				new BulkMigrationOperationalReport.Operation(operation.id(),
						operation.phase().name(), operation.description(),
						operation.transactionBreaking())).toList();
		return new BulkMigrationOperationalReport(
				BulkMigrationOperationalReport.CURRENT_FORMAT_VERSION, Instant.now(clock),
				plan.getFingerprint(), status.isCompatible(), status.getProcessedRows(),
				status.getCompletedTasks(), tasks.size(), tasks, operations,
				maintenance(maintenance), progress(progress));
	}

	private static BulkMigrationOperationalReport.Checkpoint checkpoint(
			final BulkMigrationCheckpoint checkpoint) {
		if (checkpoint == null) {
			return null;
		}
		return new BulkMigrationOperationalReport.Checkpoint(checkpoint.getMigrationId(),
				checkpoint.getSourceFingerprint(), checkpoint.getTargetFingerprint(),
				checkpoint.getProcessedRows(), checkpoint.getCompletedChunks(),
				checkpoint.getChunkSize(), checkpoint.isComplete(),
				checkpoint.getLastChunkHash(), checkpoint.getResumeToken());
	}

	private static BulkMigrationOperationalReport.Maintenance maintenance(
			final BulkMigrationMaintenanceState state) {
		return state == null ? null : new BulkMigrationOperationalReport.Maintenance(
				state.status().name(), state.updatedAt(), state.failureMessage());
	}

	private static BulkMigrationOperationalReport.Progress progress(
			final BulkMigrationProgressSnapshot progress) {
		return progress == null ? null : new BulkMigrationOperationalReport.Progress(
				progress.migrationId(), progress.processedRows(), progress.totalRows(),
				progress.elapsed().toMillis(), progress.rowsPerSecond(),
				progress.completionRatio(), progress.estimatedRemaining() == null ? null
						: progress.estimatedRemaining().toMillis());
	}
}
