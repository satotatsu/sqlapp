/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Atomically persists a cutover decision and its measured evidence. */
public final class MigrationCutoverReportIO {
	public record Snapshot(MigrationCutoverReport report, String fingerprint) {
	}

	public MigrationCutoverReport read(final Path file) {
		return readSnapshot(file, null).report();
	}

	public MigrationCutoverReport read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).report();
	}

	public Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration cutover report does not exist: " + absolute);
		}
		try {
			final byte[] bytes = BoundedMigrationFile.read(absolute, maxFileSizeBytes, "maxCutoverReportFileSizeBytes",
					"Migration cutover report");
			final var report = validate(new JsonConverter().fromJsonString(new String(bytes, StandardCharsets.UTF_8),
					MigrationCutoverReport.class));
			return new Snapshot(report, "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
		} catch (IOException e) {
			throw new CommandException("Failed to read migration cutover report: " + absolute, e);
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read migration cutover report: " + absolute, e);
		}
	}

	public void write(final Path file, final MigrationCutoverReport report) {
		writeSnapshot(file, report, null);
	}

	public Snapshot writeSnapshot(final Path file, final MigrationCutoverReport report, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		final MigrationCutoverReport validated = validate(report);
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), validated));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write migration cutover report: " + absolute, e);
		}
		final Snapshot snapshot = readSnapshot(absolute, maxFileSizeBytes);
		if (!validated.equals(snapshot.report())) {
			throw new CommandException("Written migration cutover report does not match the requested report");
		}
		return snapshot;
	}

	private static MigrationCutoverReport validate(final MigrationCutoverReport report) {
		if (report == null || report.assessedAt() == null || report.status() == null || report.freshness() == null) {
			throw new CommandException("Migration cutover report header is invalid");
		}
		final var ids = new HashSet<String>();
		for (final var freshness : report.freshness()) {
			if (freshness == null || freshness.id() == null || freshness.id().isBlank() || freshness.status() == null
					|| !ids.add(freshness.id())) {
				throw new CommandException("Migration cutover report contains invalid or duplicate freshness checks");
			}
		}
		return report;
	}

}
