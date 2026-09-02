/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.util.JsonConverter;

/** Atomically writes a bounded verification summary. */
public final class BulkMigrationVerificationReportIO {
	public static final int DEFAULT_MAX_REPORTED_MISMATCHES = 1_000;
	public BulkMigrationVerificationReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration verification report does not exist: "
					+ absolute);
		}
		try {
			return validate(new JsonConverter().fromJsonString(absolute.toFile(),
					BulkMigrationVerificationReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration verification report: "
					+ absolute, e);
		}
	}

	public BulkMigrationVerificationReport read(final Path file,
			final String expectedPlanFingerprint) {
		if (expectedPlanFingerprint == null || expectedPlanFingerprint.isBlank()) {
			throw new IllegalArgumentException("expectedPlanFingerprint must not be empty");
		}
		final var report = read(file);
		if (!expectedPlanFingerprint.equals(report.planFingerprint())) {
			throw new CommandException(
					"Bulk migration verification report plan fingerprint mismatch");
		}
		return report;
	}

	public void write(final Path file, final String planFingerprint,
			final BulkMigrationJobVerificationResult result) {
		write(file, planFingerprint, BulkMigrationVerificationIsolation.DEFAULT,
				DEFAULT_MAX_REPORTED_MISMATCHES, result);
	}

	public void write(final Path file, final String planFingerprint,
			final BulkMigrationVerificationIsolation isolation,
			final BulkMigrationJobVerificationResult result) {
		write(file, planFingerprint, isolation, DEFAULT_MAX_REPORTED_MISMATCHES, result);
	}

	public void write(final Path file, final String planFingerprint,
			final BulkMigrationVerificationIsolation isolation,
			final int maxReportedMismatches,
			final BulkMigrationJobVerificationResult result) {
		Objects.requireNonNull(isolation, "isolation");
		if (maxReportedMismatches <= 0) {
			throw new IllegalArgumentException("maxReportedMismatches must be greater than zero");
		}
		final var tasks = result.getTasks().stream().map(task -> {
			final var verification = task.getVerificationResult();
			final var allMismatches = verification.getMismatches();
			final var mismatches = allMismatches.stream().limit(maxReportedMismatches).map(chunk ->
					new BulkMigrationVerificationReport.Chunk(chunk.getIndex(),
							chunk.getExpectedRows(), chunk.getActualRows(),
							chunk.getExpectedHash(), chunk.getActualHash(),
							chunk.getExpectedFirstKey(), chunk.getExpectedLastKey(),
							chunk.getActualFirstKey(), chunk.getActualLastKey())).toList();
			return new BulkMigrationVerificationReport.Task(task.getTaskId(), task.getColumns(),
					verification.getExpectedKeysetFingerprint(),
					verification.getActualKeysetFingerprint(),
					verification.isMatch(), verification.getExpectedRows(),
					verification.getActualRows(), allMismatches.size(), mismatches);
		}).toList();
		write(file, new BulkMigrationVerificationReport(
				BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION, Instant.now(),
				planFingerprint, isolation.name(), result.isMatch(), result.getExpectedRows(),
				result.getActualRows(), result.getMismatchedTasks(), tasks));
	}

	public void write(final Path file, final BulkMigrationVerificationReport report) {
		validate(report);
		final Path absolute = file.toAbsolutePath().normalize();
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
			throw new CommandException("Failed to write bulk migration verification report: "
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

	private static BulkMigrationVerificationReport validate(
			final BulkMigrationVerificationReport report) {
		if (report == null) {
			throw new CommandException("Bulk migration verification report must not be null");
		}
		if (report.formatVersion() != BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported bulk migration verification report format: "
					+ report.formatVersion());
		}
		Objects.requireNonNull(report.generatedAt(), "generatedAt");
		if (report.planFingerprint() == null || report.planFingerprint().isBlank()) {
			throw new CommandException("Verification report planFingerprint must not be empty");
		}
		try {
			BulkMigrationVerificationIsolation.valueOf(report.isolation());
		} catch (NullPointerException | IllegalArgumentException e) {
			throw new CommandException("Verification report isolation is invalid: "
					+ report.isolation(), e);
		}
		if (report.expectedRows() < 0 || report.actualRows() < 0
				|| report.mismatchedTasks() < 0) {
			throw new CommandException("Verification report counts must not be negative");
		}
		Objects.requireNonNull(report.tasks(), "tasks");
		final var ids = new HashSet<String>();
		long expectedRows = 0;
		long actualRows = 0;
		long mismatchedTasks = 0;
		for (final var task : report.tasks()) {
			if (task == null || task.taskId() == null || task.taskId().isBlank()
					|| !ids.add(task.taskId())) {
				throw new CommandException("Verification report task IDs must be unique and non-empty");
			}
			if (task.columns() == null || task.columns().isEmpty()
					|| task.columns().stream().anyMatch(name -> name == null || name.isBlank())
					|| new HashSet<>(task.columns()).size() != task.columns().size()) {
				throw new CommandException("Verification report columns must be non-empty and unique: "
						+ task.taskId());
			}
			if ((task.expectedKeysetFingerprint() == null)
					!= (task.actualKeysetFingerprint() == null)
					|| task.expectedKeysetFingerprint() != null
							&& (task.expectedKeysetFingerprint().isBlank()
									|| task.actualKeysetFingerprint().isBlank())) {
				throw new CommandException("Verification report keyset fingerprints are invalid: "
						+ task.taskId());
			}
			if (task.expectedRows() < 0 || task.actualRows() < 0
					|| task.mismatchedChunks() < 0 || task.mismatches() == null) {
				throw new CommandException("Verification report task values are invalid: "
						+ task.taskId());
			}
			if (task.match() != (task.mismatchedChunks() == 0)
					|| task.mismatches().size() > task.mismatchedChunks()
					|| (!task.match() && task.mismatches().isEmpty())) {
				throw new CommandException("Verification task match flag disagrees with mismatches: "
						+ task.taskId());
			}
			for (final var chunk : task.mismatches()) {
				if (chunk == null || chunk.index() < 0 || chunk.expectedRows() < 0
						|| chunk.actualRows() < 0 || chunk.expectedHash() == null
						|| chunk.actualHash() == null) {
					throw new CommandException("Verification report mismatch chunk is invalid: "
							+ task.taskId());
				}
				final boolean hasKeyRange = chunk.expectedFirstKey() != null
						|| chunk.expectedLastKey() != null || chunk.actualFirstKey() != null
						|| chunk.actualLastKey() != null;
				if ((task.expectedKeysetFingerprint() != null || hasKeyRange)
						&& (!validKeyRange(chunk.expectedRows(),
						chunk.expectedFirstKey(), chunk.expectedLastKey())
						|| !validKeyRange(chunk.actualRows(), chunk.actualFirstKey(),
								chunk.actualLastKey()))) {
					throw new CommandException("Verification report mismatch key range is invalid: "
							+ task.taskId());
				}
			}
			try {
				expectedRows = Math.addExact(expectedRows, task.expectedRows());
				actualRows = Math.addExact(actualRows, task.actualRows());
			} catch (ArithmeticException e) {
				throw new CommandException("Verification report aggregate count overflow", e);
			}
			if (!task.match()) {
				mismatchedTasks++;
			}
		}
		if (expectedRows != report.expectedRows() || actualRows != report.actualRows()
				|| mismatchedTasks != report.mismatchedTasks()
				|| report.match() != (mismatchedTasks == 0)) {
			throw new CommandException("Verification report aggregate values are inconsistent");
		}
		return report;
	}

	private static boolean validKeyRange(final int rows, final String first,
			final String last) {
		if (rows == 0) {
			return first == null && last == null;
		}
		return first != null && !first.isBlank() && last != null && !last.isBlank();
	}
}
