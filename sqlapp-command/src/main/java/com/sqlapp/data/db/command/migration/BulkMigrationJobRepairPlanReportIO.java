/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairPlan;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes job-level repair-plan JSON snapshots. */
public final class BulkMigrationJobRepairPlanReportIO {
	public BulkMigrationJobRepairPlanReport fromPlan(final BulkMigrationJobRepairPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		final var planIO = new BulkMigrationRepairPlanReportIO();
		return validate(new BulkMigrationJobRepairPlanReport(
				BulkMigrationJobRepairPlanReport.CURRENT_FORMAT_VERSION, Instant.now(),
				plan.getFingerprint(), plan.getEstimatedReplayRows(), plan.getMismatchChunks(),
				plan.isAtomic(), plan.getTasks().stream().map(task ->
						new BulkMigrationJobRepairPlanReport.Task(task.taskId(),
								planIO.fromPlan(task.repairPlan()))).toList()));
	}

	public void write(final Path file, final BulkMigrationJobRepairPlan plan) {
		write(file, fromPlan(plan));
	}

	public void write(final Path file, final BulkMigrationJobRepairPlanReport report) {
		validate(report);
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		final Path directory = absolute.getParent();
		Path temporary = null;
		try {
			Files.createDirectories(directory);
			temporary = Files.createTempFile(directory, absolute.getFileName().toString(), ".tmp");
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			converter.writeJsonValue(temporary.toFile(), report);
			try {
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration job repair plan report: "
					+ absolute, e);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException e) {
					// Preserve the primary failure.
				}
			}
		}
	}

	public BulkMigrationJobRepairPlanReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration job repair plan report does not exist: "
					+ absolute);
		}
		try {
			return validate(new JsonConverter().fromJsonString(absolute.toFile(),
					BulkMigrationJobRepairPlanReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration job repair plan report: "
					+ absolute, e);
		}
	}

	public BulkMigrationJobRepairPlanReport read(final Path file,
			final String expectedPlanFingerprint) {
		if (expectedPlanFingerprint == null || expectedPlanFingerprint.isBlank()) {
			throw new IllegalArgumentException("expectedPlanFingerprint must not be empty");
		}
		final var report = read(file);
		if (!expectedPlanFingerprint.equals(report.planFingerprint())) {
			throw new CommandException("Bulk migration job repair plan fingerprint mismatch");
		}
		return report;
	}

	static BulkMigrationJobRepairPlanReport validate(
			final BulkMigrationJobRepairPlanReport report) {
		if (report == null) {
			throw new CommandException("Bulk migration job repair plan report must not be null");
		}
		if (report.formatVersion() != BulkMigrationJobRepairPlanReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported bulk migration job repair plan format: "
					+ report.formatVersion());
		}
		Objects.requireNonNull(report.generatedAt(), "generatedAt");
		if (report.planFingerprint() == null || report.planFingerprint().isBlank()
				|| report.estimatedReplayRows() < 0 || report.mismatchChunks() < 0
				|| report.tasks() == null) {
			throw new CommandException("Bulk migration job repair plan header is invalid");
		}
		long rows = 0;
		long chunks = 0;
		boolean atomic = true;
		final var ids = new HashSet<String>();
		for (final var task : report.tasks()) {
			if (task == null || task.taskId() == null || task.taskId().isBlank()
					|| !ids.add(task.taskId())) {
				throw new CommandException("Bulk migration job repair task is invalid");
			}
			final var child = BulkMigrationRepairPlanReportIO.validate(task.repairPlan());
			try {
				rows = Math.addExact(rows, child.estimatedReplayRows());
				chunks = Math.addExact(chunks, child.mismatchChunks().size());
			} catch (ArithmeticException e) {
				throw new CommandException("Bulk migration job repair plan count overflow", e);
			}
			atomic &= child.atomic();
		}
		if (rows != report.estimatedReplayRows() || chunks != report.mismatchChunks()
				|| atomic != report.atomic()
				|| !report.planFingerprint().equals(fingerprint(report))) {
			throw new CommandException("Bulk migration job repair plan summary is inconsistent");
		}
		return report;
	}

	private static String fingerprint(final BulkMigrationJobRepairPlanReport report) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (final var task : report.tasks()) {
				update(digest, task.taskId(), task.repairPlan().planFingerprint());
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
}
