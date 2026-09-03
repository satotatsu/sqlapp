/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class BulkMigrationJobRepairPlanReportIOTest {
	@TempDir
	Path directory;

	@Test
	void writesReadsAndChecksTheApprovedJobFingerprint() throws Exception {
		final var task = new BulkMigrationJobRepairPlanReport.Task("parent", child("child-plan"));
		final var report = report(List.of(task), 1, 1, true);
		final Path file = directory.resolve("job-repair-plan.json");
		final var io = new BulkMigrationJobRepairPlanReportIO();

		io.write(file, report);

		assertEquals(report, io.read(file));
		assertEquals(report, io.read(file, report.planFingerprint()));
		assertThrows(CommandException.class, () -> io.read(file, "not-approved"));
	}

	@Test
	void rejectsDuplicateTasksAndInconsistentSummaries() throws Exception {
		final var first = new BulkMigrationJobRepairPlanReport.Task("same", child("first"));
		final var second = new BulkMigrationJobRepairPlanReport.Task("same", child("second"));
		final var io = new BulkMigrationJobRepairPlanReportIO();
		assertThrows(CommandException.class,
				() -> io.write(directory.resolve("duplicate.json"),
						report(List.of(first, second), 2, 2, true)));
		assertThrows(CommandException.class,
				() -> io.write(directory.resolve("rows.json"),
						report(List.of(first), 2, 1, true)));
	}

	private static BulkMigrationJobRepairPlanReport report(
			final List<BulkMigrationJobRepairPlanReport.Task> tasks,
			final long rows, final long chunks, final boolean atomic) throws Exception {
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (final var task : tasks) {
			update(digest, task.taskId(), task.repairPlan().planFingerprint());
		}
		return new BulkMigrationJobRepairPlanReport(1,
				Instant.parse("2026-09-03T00:00:00Z"),
				HexFormat.of().formatHex(digest.digest()), rows, chunks, atomic, tasks);
	}

	private static BulkMigrationRepairPlanReport child(final String fingerprint) {
		return new BulkMigrationRepairPlanReport(1, Instant.parse("2026-09-03T00:00:00Z"),
				fingerprint,
				new BulkMigrationRepairPlanReport.Relation(null, null, "SOURCE_ROWS"),
				new BulkMigrationRepairPlanReport.Relation(null, null, "TARGET_ROWS"),
				false, null, null, "SQLite", "3.50", "com.example.Executor", true,
				false, "repair_stage", 1, 1, 100, true, List.of("ID"), List.of("ID"),
				List.of("ID"), List.of(), List.of(new BulkMigrationRepairPlanReport.Chunk(
						0, 1, 0, "expected", "actual", null, null, null, null)));
	}

	private static void update(final MessageDigest digest, final Object... values) {
		for (final Object value : values) {
			final byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
			digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
			digest.update(bytes);
		}
	}
}
