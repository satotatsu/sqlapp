/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table.TableOrder;

/** Verifies every table in a migration job in dependency order. */
public final class BulkMigrationJobVerifier {
	private BulkMigrationJobVerifier() {
	}

	public static BulkMigrationJobVerificationResult verify(
			final BulkMigrationJobPlan plan,
			final List<BulkMigrationJobVerificationTask> tasks) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		Objects.requireNonNull(tasks, "tasks");
		final Map<String, BulkMigrationJobVerificationTask> byId = new LinkedHashMap<>();
		for (final BulkMigrationJobVerificationTask task : tasks) {
			Objects.requireNonNull(task, "task");
			if (byId.putIfAbsent(task.getTaskId(), task) != null) {
				throw new IllegalArgumentException("Duplicate verification task ID: "
						+ task.getTaskId());
			}
		}
		if (!byId.keySet().equals(new HashSet<>(plan.getTaskIds()))) {
			throw new IllegalArgumentException(
					"Verification tasks must exactly match migration plan task IDs");
		}
		final List<BulkMigrationJobVerificationTask> ordered = new ArrayList<>(tasks.size());
		for (final BulkMigrationJobTask planned : plan.getTasks()) {
			final BulkMigrationJobVerificationTask task = byId.get(planned.getTaskId());
			final var source = planned.getSourceTable() != null ? planned.getSourceTable()
					: planned.getKeysetSource().getTable();
			if (!SchemaUtils.isSameTable(source, task.getExpected())) {
				throw new IllegalArgumentException("Verification expected table differs from "
						+ "migration plan task: " + planned.getTaskId());
			}
			ordered.add(task);
		}
		final BulkMigrationJobVerificationResult result = verify(ordered);
		if (!result.getTasks().stream().map(BulkMigrationJobTaskVerificationResult::getTaskId)
				.toList().equals(plan.getTaskIds())) {
			throw new IllegalArgumentException(
					"Verification task dependency order differs from migration plan");
		}
		return new BulkMigrationJobVerificationResult(plan.getFingerprint(), result.getTasks())
				.validateAgainst(plan);
	}

	public static BulkMigrationJobVerificationResult verify(
			final List<BulkMigrationJobVerificationTask> tasks) {
		Objects.requireNonNull(tasks, "tasks");
		final Set<String> taskIds = new HashSet<>();
		for (final BulkMigrationJobVerificationTask task : tasks) {
			Objects.requireNonNull(task, "task");
			if (task.getTaskId() == null || task.getTaskId().isBlank()) {
				throw new IllegalArgumentException("taskId must not be empty");
			}
			if (!taskIds.add(task.getTaskId())) {
				throw new IllegalArgumentException("Duplicate verification task ID: "
						+ task.getTaskId());
			}
			Objects.requireNonNull(task.getExpected(), "expected");
			Objects.requireNonNull(task.getActual(), "actual");
			if (task.getChunkSize() <= 0) {
				throw new IllegalArgumentException("chunkSize must be greater than zero: "
						+ task.getTaskId());
			}
		}
		final List<BulkMigrationJobVerificationTask> ordered = TableOrder.CREATE.sort(
				tasks, BulkMigrationJobVerificationTask::getExpected);
		final List<BulkMigrationJobTaskVerificationResult> results = new ArrayList<>(ordered.size());
		for (final BulkMigrationJobVerificationTask task : ordered) {
			final var verification = BulkMigrationVerifier.verify(task.getExpected(),
					task.getActual(), task.getChunkSize());
			results.add(new BulkMigrationJobTaskVerificationResult(task.getTaskId(),
					verification.getColumns(), verification));
		}
		return new BulkMigrationJobVerificationResult(List.copyOf(results));
	}
}
