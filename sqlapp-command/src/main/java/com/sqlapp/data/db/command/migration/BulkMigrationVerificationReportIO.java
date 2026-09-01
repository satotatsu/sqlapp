/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.util.JsonConverter;

/** Atomically writes a bounded verification summary. */
public final class BulkMigrationVerificationReportIO {
	public void write(final Path file, final String planFingerprint,
			final BulkMigrationJobVerificationResult result) {
		final var tasks = result.getTasks().stream().map(task -> {
			final var verification = task.getVerificationResult();
			final var mismatches = verification.getMismatches().stream().map(chunk ->
					new BulkMigrationVerificationReport.Chunk(chunk.getIndex(),
							chunk.getExpectedRows(), chunk.getActualRows(),
							chunk.getExpectedHash(), chunk.getActualHash())).toList();
			return new BulkMigrationVerificationReport.Task(task.getTaskId(),
					verification.isMatch(), verification.getExpectedRows(),
					verification.getActualRows(), mismatches);
		}).toList();
		write(file, new BulkMigrationVerificationReport(
				BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION, Instant.now(),
				planFingerprint, result.isMatch(), result.getExpectedRows(),
				result.getActualRows(), result.getMismatchedTasks(), tasks));
	}

	public void write(final Path file, final BulkMigrationVerificationReport report) {
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
}
