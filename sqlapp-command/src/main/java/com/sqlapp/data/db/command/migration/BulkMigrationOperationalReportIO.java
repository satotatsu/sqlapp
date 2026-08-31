/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;
import com.sqlapp.util.JsonConverter;

/** Atomically writes bulk migration operational reports as UTF-8 JSON. */
public final class BulkMigrationOperationalReportIO {
	private final JsonConverter converter;

	public BulkMigrationOperationalReportIO() {
		this(new JsonConverter());
	}

	BulkMigrationOperationalReportIO(final JsonConverter converter) {
		this.converter = Objects.requireNonNull(converter, "converter");
		this.converter.setIndentOutput(true);
	}

	public BulkMigrationOperationalReport read(final Path file) {
		Objects.requireNonNull(file, "file");
		final Path absolute = file.toAbsolutePath();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration report does not exist: " + absolute);
		}
		try {
			return validate(converter.fromJsonString(absolute.toFile(),
					BulkMigrationOperationalReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration report: " + absolute, e);
		}
	}

	public BulkMigrationOperationalReport read(final Path file,
			final String expectedPlanFingerprint) {
		if (expectedPlanFingerprint == null || expectedPlanFingerprint.isBlank()) {
			throw new IllegalArgumentException(
					"expectedPlanFingerprint must not be empty");
		}
		final BulkMigrationOperationalReport report = read(file);
		if (!expectedPlanFingerprint.equals(report.planFingerprint())) {
			throw new CommandException("Bulk migration report plan fingerprint mismatch");
		}
		return report;
	}

	public BulkMigrationResumeReadiness assessResume(final Path file,
			final String expectedPlanFingerprint) {
		return BulkMigrationOperationalReportResumeAssessor.assess(
				read(file, expectedPlanFingerprint));
	}

	public void write(final Path file, final BulkMigrationOperationalReport report) {
		Objects.requireNonNull(file, "file");
		Objects.requireNonNull(report, "report");
		final Path absolute = file.toAbsolutePath();
		final Path directory = absolute.getParent();
		Path temporary = null;
		try {
			Files.createDirectories(directory);
			temporary = Files.createTempFile(directory, absolute.getFileName().toString(), ".tmp");
			converter.writeJsonValue(temporary.toFile(), report);
			try {
				Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration report: " + absolute, e);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
					// Preserve the original report-writing failure.
				}
			}
		}
	}

	private static BulkMigrationOperationalReport validate(
			final BulkMigrationOperationalReport report) {
		if (report == null) {
			throw new CommandException("Bulk migration report must not be null");
		}
		if (report.formatVersion()
				!= BulkMigrationOperationalReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported bulk migration report formatVersion: "
					+ report.formatVersion());
		}
		if (report.generatedAt() == null || report.planFingerprint() == null
				|| report.planFingerprint().isBlank()) {
			throw new CommandException(
					"Bulk migration report requires generatedAt and planFingerprint");
		}
		if (report.processedRows() < 0 || report.completedTasks() < 0
				|| report.totalTasks() < 0 || report.completedTasks() > report.totalTasks()) {
			throw new CommandException("Bulk migration report contains invalid aggregate counts");
		}
		if (report.tasks() == null || report.operations() == null
				|| report.progressByMigration() == null
				|| report.tasks().size() != report.totalTasks()) {
			throw new CommandException(
					"Bulk migration report contains invalid task or operation lists");
		}
		if (report.tasks().stream().anyMatch(task -> task == null || task.taskId() == null
				|| task.taskId().isBlank() || task.migrationId() == null
				|| task.migrationId().isBlank())) {
			throw new CommandException("Bulk migration report contains an invalid task identity");
		}
		final Set<String> taskIds = new HashSet<>();
		final Set<String> migrationIds = new HashSet<>();
		for (BulkMigrationOperationalReport.Task task : report.tasks()) {
			if (!knownTaskState(task.state())) {
				throw new CommandException(
						"Bulk migration report contains an unknown task state");
			}
			if (!taskIds.add(task.taskId()) || !migrationIds.add(task.migrationId())) {
				throw new CommandException(
						"Bulk migration report contains duplicate task identities");
			}
			if (task.checkpoint() != null && !task.migrationId()
					.equals(task.checkpoint().migrationId())) {
				throw new CommandException(
						"Bulk migration report checkpoint migrationId mismatch");
			}
		}
		final Set<String> progressMigrationIds = new HashSet<>();
		for (BulkMigrationOperationalReport.Progress progress : report.progressByMigration()) {
			if (progress == null || progress.migrationId() == null
					|| !migrationIds.contains(progress.migrationId())
					|| !progressMigrationIds.add(progress.migrationId())) {
				throw new CommandException(
						"Bulk migration report contains invalid progress identities");
			}
		}
		if (report.progress() != null
				&& !migrationIds.contains(report.progress().migrationId())) {
			throw new CommandException(
					"Bulk migration report current progress migrationId mismatch");
		}
		if (report.maintenance() != null
				&& !knownMaintenanceStatus(report.maintenance().status())) {
			throw new CommandException(
					"Bulk migration report contains an unknown maintenance status");
		}
		return report;
	}

	private static boolean knownTaskState(final String state) {
		try {
			BulkMigrationJobTaskState.valueOf(state);
			return true;
		} catch (IllegalArgumentException | NullPointerException e) {
			return false;
		}
	}

	private static boolean knownMaintenanceStatus(final String status) {
		try {
			BulkMigrationMaintenanceStatus.valueOf(status);
			return true;
		} catch (IllegalArgumentException | NullPointerException e) {
			return false;
		}
	}
}
