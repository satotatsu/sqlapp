/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import lombok.Getter;

/** Dependency-ordered, reviewable repair plan for a multi-table migration job. */
@Getter
public final class BulkMigrationJobRepairPlan {
	private final List<Task> tasks;
	private final String fingerprint;

	BulkMigrationJobRepairPlan(final List<Task> tasks) {
		this.tasks = List.copyOf(tasks);
		this.fingerprint = calculateFingerprint();
	}

	public List<String> getTaskIds() {
		return tasks.stream().map(Task::taskId).toList();
	}

	public long getEstimatedReplayRows() {
		long rows = 0;
		for (final Task task : tasks) {
			rows = Math.addExact(rows, task.repairPlan().getEstimatedReplayRows());
		}
		return rows;
	}

	public long getMismatchChunks() {
		long chunks = 0;
		for (final Task task : tasks) {
			chunks = Math.addExact(chunks, task.repairPlan().getMismatchChunks().size());
		}
		return chunks;
	}

	public boolean isAtomic() {
		return tasks.stream().map(Task::repairPlan)
				.allMatch(BulkMigrationRepairPlan::isAtomic);
	}

	public boolean isUnchanged() {
		return tasks.stream().map(Task::repairPlan)
				.allMatch(BulkMigrationRepairPlan::isUnchanged)
				&& fingerprint.equals(calculateFingerprint());
	}

	public void validateUnchanged() {
		if (!isUnchanged()) {
			throw new IllegalStateException(
					"Migration job repair plan changed after it was created");
		}
	}

	private String calculateFingerprint() {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (final Task task : tasks) {
				update(digest, task.taskId(), task.repairPlan().getFingerprint());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void update(final MessageDigest digest, final Object... values) {
		for (final Object value : values) {
			final byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
			digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
			digest.update(bytes);
		}
	}

	public record Task(String taskId, BulkMigrationRepairPlan repairPlan) {
		public Task {
			if (taskId == null || taskId.isBlank()) {
				throw new IllegalArgumentException("taskId must not be empty");
			}
			java.util.Objects.requireNonNull(repairPlan, "repairPlan");
		}
	}
}
