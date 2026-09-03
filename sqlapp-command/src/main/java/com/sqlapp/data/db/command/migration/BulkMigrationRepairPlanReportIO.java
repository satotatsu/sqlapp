/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationRepairPlan;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes reviewable repair-plan JSON snapshots. */
public final class BulkMigrationRepairPlanReportIO {
	public BulkMigrationRepairPlanReport fromPlan(final BulkMigrationRepairPlan plan) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		return validate(new BulkMigrationRepairPlanReport(
				BulkMigrationRepairPlanReport.CURRENT_FORMAT_VERSION, Instant.now(),
				plan.getFingerprint(), relation(plan.getExpected()), relation(plan.getTarget()),
				plan.isKeysetSource(), plan.getVerification().getExpectedKeysetFingerprint(),
				plan.getVerification().getActualKeysetFingerprint(),
				plan.getDatabaseProductName(), plan.getDatabaseProductVersion(),
				plan.getExecutorClassName(), plan.isAtomic(),
				plan.isTransactionBreakingStaging(), plan.getStagingTableName(),
				plan.getVerification().getChunkSize(), plan.getEstimatedReplayRows(),
				plan.getOptions().getMaxBufferedRows(),
				plan.getOptions().isVerifyExpectedHashes(),
				plan.getVerification().getColumns(), plan.getKeyColumns(),
				plan.getStagingColumns(), plan.getUpdateColumns(),
				plan.getMismatchChunks().stream().map(chunk ->
						new BulkMigrationRepairPlanReport.Chunk(chunk.getIndex(),
								chunk.getExpectedRows(), chunk.getActualRows(),
								chunk.getExpectedHash(), chunk.getActualHash(),
								chunk.getExpectedFirstKey(), chunk.getExpectedLastKey(),
								chunk.getActualFirstKey(), chunk.getActualLastKey()))
						.toList()));
	}

	public void write(final Path file, final BulkMigrationRepairPlan plan) {
		write(file, fromPlan(plan));
	}

	public void write(final Path file, final BulkMigrationRepairPlanReport report) {
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
			throw new CommandException("Failed to write bulk migration repair plan report: "
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

	public BulkMigrationRepairPlanReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration repair plan report does not exist: "
					+ absolute);
		}
		try {
			return validate(new JsonConverter().fromJsonString(absolute.toFile(),
					BulkMigrationRepairPlanReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration repair plan report: "
					+ absolute, e);
		}
	}

	public BulkMigrationRepairPlanReport read(final Path file,
			final String expectedPlanFingerprint) {
		if (expectedPlanFingerprint == null || expectedPlanFingerprint.isBlank()) {
			throw new IllegalArgumentException("expectedPlanFingerprint must not be empty");
		}
		final var report = read(file);
		if (!expectedPlanFingerprint.equals(report.planFingerprint())) {
			throw new CommandException("Bulk migration repair plan fingerprint mismatch");
		}
		return report;
	}

	private static BulkMigrationRepairPlanReport.Relation relation(final Table table) {
		return new BulkMigrationRepairPlanReport.Relation(table.getCatalogName(),
				table.getSchemaName(), table.getName());
	}

	private static BulkMigrationRepairPlanReport validate(
			final BulkMigrationRepairPlanReport report) {
		if (report == null) {
			throw new CommandException("Bulk migration repair plan report must not be null");
		}
		if (report.formatVersion() != BulkMigrationRepairPlanReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported bulk migration repair plan format: "
					+ report.formatVersion());
		}
		Objects.requireNonNull(report.generatedAt(), "generatedAt");
		nonBlank(report.planFingerprint(), "planFingerprint");
		relation(report.source(), "source");
		relation(report.target(), "target");
		nonBlank(report.databaseProductName(), "databaseProductName");
		nonBlank(report.databaseProductVersion(), "databaseProductVersion");
		nonBlank(report.executorClassName(), "executorClassName");
		if ((report.expectedKeysetFingerprint() == null)
				!= (report.actualKeysetFingerprint() == null)
				|| report.keysetSource() && report.expectedKeysetFingerprint() == null) {
			throw new CommandException("Repair plan keyset fingerprints are invalid");
		}
		if (report.expectedKeysetFingerprint() != null) {
			nonBlank(report.expectedKeysetFingerprint(), "expectedKeysetFingerprint");
			nonBlank(report.actualKeysetFingerprint(), "actualKeysetFingerprint");
		}
		if (report.chunkSize() <= 0 || report.estimatedReplayRows() < 0
				|| report.maxBufferedRows() < 0) {
			throw new CommandException("Repair plan counts must not be negative");
		}
		if (report.atomic() && report.transactionBreakingStaging()) {
			throw new CommandException(
					"Repair plan cannot be atomic with transaction-breaking staging");
		}
		uniqueNames(report.verificationColumns(), "verificationColumns", true);
		uniqueNames(report.keyColumns(), "keyColumns", false);
		uniqueNames(report.stagingColumns(), "stagingColumns", false);
		uniqueNames(report.updateColumns(), "updateColumns", false);
		Objects.requireNonNull(report.mismatchChunks(), "mismatchChunks");
		long replayRows = 0;
		final var indexes = new HashSet<Long>();
		for (final var chunk : report.mismatchChunks()) {
			if (chunk == null || chunk.index() < 0 || !indexes.add(chunk.index())
					|| chunk.expectedRows() < 0 || chunk.actualRows() < 0
					|| chunk.expectedRows() > report.chunkSize()
					|| chunk.actualRows() > report.chunkSize()
					|| chunk.expectedHash() == null || chunk.actualHash() == null) {
				throw new CommandException("Repair plan mismatch chunk is invalid");
			}
			if (chunk.expectedRows() == chunk.actualRows()
					&& chunk.expectedHash().equals(chunk.actualHash())) {
				throw new CommandException("Repair plan contains a matching chunk: "
						+ chunk.index());
			}
			final boolean hasBoundaries = chunk.expectedFirstKey() != null
					|| chunk.expectedLastKey() != null || chunk.actualFirstKey() != null
					|| chunk.actualLastKey() != null;
			if ((report.expectedKeysetFingerprint() != null || hasBoundaries)
					&& (!validKeyRange(chunk.expectedRows(), chunk.expectedFirstKey(),
							chunk.expectedLastKey())
							|| !validKeyRange(chunk.actualRows(), chunk.actualFirstKey(),
									chunk.actualLastKey()))) {
				throw new CommandException("Repair plan mismatch key range is invalid: "
						+ chunk.index());
			}
			try {
				replayRows = Math.addExact(replayRows, chunk.expectedRows());
			} catch (ArithmeticException e) {
				throw new CommandException("Repair plan estimatedReplayRows overflow", e);
			}
		}
		if (replayRows != report.estimatedReplayRows()) {
			throw new CommandException("Repair plan estimatedReplayRows is inconsistent");
		}
		return report;
	}

	private static boolean validKeyRange(final int rows, final String first,
			final String last) {
		return rows == 0 ? first == null && last == null
				: first != null && !first.isBlank() && last != null && !last.isBlank();
	}

	private static void relation(final BulkMigrationRepairPlanReport.Relation relation,
			final String role) {
		if (relation == null || relation.tableName() == null || relation.tableName().isBlank()) {
			throw new CommandException("Repair plan " + role + " table name must not be empty");
		}
	}

	private static void uniqueNames(final List<String> values, final String name,
			final boolean required) {
		if (values == null || required && values.isEmpty()
				|| values != null && (values.stream().anyMatch(value -> value == null || value.isBlank())
						|| new HashSet<>(values).size() != values.size())) {
			throw new CommandException("Repair plan " + name + " is invalid");
		}
	}

	private static void nonBlank(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new CommandException("Repair plan " + name + " must not be empty");
		}
	}
}
