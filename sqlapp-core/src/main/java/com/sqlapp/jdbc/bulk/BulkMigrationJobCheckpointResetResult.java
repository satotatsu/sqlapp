/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import lombok.Value;

/** Checkpoints successfully deleted from a validated migration job plan. */
@Value
public class BulkMigrationJobCheckpointResetResult {
	String planFingerprint;
	List<String> resetTaskIds;

	public BulkMigrationJobCheckpointResetResult(final String planFingerprint,
			final List<String> resetTaskIds) {
		if (planFingerprint == null || planFingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
		Objects.requireNonNull(resetTaskIds, "resetTaskIds");
		if (resetTaskIds.stream().anyMatch(id -> id == null || id.isBlank())
				|| new HashSet<>(resetTaskIds).size() != resetTaskIds.size()) {
			throw new IllegalArgumentException(
					"resetTaskIds must contain unique non-empty task IDs");
		}
		this.planFingerprint = planFingerprint;
		this.resetTaskIds = List.copyOf(resetTaskIds);
	}

	public BulkMigrationJobCheckpointResetResult validateAgainst(
			final BulkMigrationJobPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		if (!plan.getFingerprint().equals(planFingerprint)) {
			throw new IllegalArgumentException(
					"Checkpoint reset result plan differs from the current plan");
		}
		int previous = -1;
		for (final String taskId : resetTaskIds) {
			final int current = plan.getTaskIds().indexOf(taskId);
			if (current <= previous) {
				throw new IllegalArgumentException(
						"Reset task IDs must be a dependency-ordered subset of the plan");
			}
			previous = current;
		}
		return this;
	}

	public boolean isCompleteFor(final BulkMigrationJobPlan plan) {
		validateAgainst(plan);
		return resetTaskIds.equals(plan.getTaskIds());
	}
}
