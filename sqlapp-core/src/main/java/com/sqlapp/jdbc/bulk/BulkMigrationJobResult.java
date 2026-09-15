/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** Completed multi-table job results in dependency execution order. */
@Getter
@EqualsAndHashCode
@ToString
public class BulkMigrationJobResult {
	private final String planFingerprint;
	private final List<BulkMigrationJobTaskResult> tasks;

	public BulkMigrationJobResult(final List<BulkMigrationJobTaskResult> tasks) {
		this(null, tasks);
	}

	public BulkMigrationJobResult(final String planFingerprint,
			final List<BulkMigrationJobTaskResult> tasks) {
		if (planFingerprint != null && planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		for (final BulkMigrationJobTaskResult task : tasks) {
			Objects.requireNonNull(task, "task");
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate migration result task ID: "
						+ task.getTaskId());
			}
		}
		this.planFingerprint = planFingerprint;
		this.tasks = List.copyOf(tasks);
	}

	public long getProcessedRows() {
		long rows = 0;
		for (final BulkMigrationJobTaskResult task : tasks) {
			rows = Math.addExact(rows, task.getMigrationResult().getProcessedRows());
		}
		return rows;
	}

	public BulkMigrationJobResult validateAgainst(final BulkMigrationJobPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		if (!plan.getFingerprint().equals(planFingerprint)) {
			throw new IllegalArgumentException(
					"Migration result plan differs from the current plan");
		}
		if (!tasks.stream().map(BulkMigrationJobTaskResult::getTaskId).toList()
				.equals(plan.getTaskIds())) {
			throw new IllegalArgumentException(
					"Migration result tasks do not match the plan and dependency order");
		}
		return this;
	}

	public BulkMigrationJobResult validateCompletedPrefixAgainst(
			final BulkMigrationJobPlan plan, final String stoppedTaskId) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		if (!plan.getFingerprint().equals(planFingerprint)) {
			throw new IllegalArgumentException(
					"Migration result plan differs from the current plan");
		}
		if (stoppedTaskId == null || stoppedTaskId.isBlank()) {
			throw new IllegalArgumentException("stoppedTaskId must not be empty");
		}
		final int stoppedIndex = plan.getTaskIds().indexOf(stoppedTaskId);
		if (stoppedIndex < 0) {
			throw new IllegalArgumentException(
					"Stopped task does not belong to the migration plan: " + stoppedTaskId);
		}
		if (!tasks.stream().map(BulkMigrationJobTaskResult::getTaskId).toList()
				.equals(plan.getTaskIds().subList(0, stoppedIndex))) {
			throw new IllegalArgumentException(
					"Completed tasks must be the plan prefix before the stopped task");
		}
		return this;
	}

	public long getAlreadyCompleteTasks() {
		return tasks.stream().map(BulkMigrationJobTaskResult::getMigrationResult)
				.filter(ChunkedBulkMigrationResult::isAlreadyComplete).count();
	}
}
