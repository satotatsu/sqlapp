/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes completed SCD2 execution reports. */
public final class MigrationSnapshotExecutionReportIO {
	public void write(final Path file, final MigrationSnapshotExecutionReport report) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		validate(report);
		try {
			final var converter = converter();
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) throw commandException;
			throw new CommandException("Failed to write migration snapshot report: " + absolute, e);
		}
	}

	public MigrationSnapshotExecutionReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration snapshot report does not exist: " + absolute);
		}
		try {
			return validate(converter().fromJsonString(absolute.toFile(), MigrationSnapshotExecutionReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) throw commandException;
			throw new CommandException("Failed to read migration snapshot report: " + absolute, e);
		}
	}

	private static JsonConverter converter() {
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		return converter;
	}

	static MigrationSnapshotExecutionReport validate(final MigrationSnapshotExecutionReport report) {
		if (report == null || report.formatVersion() != MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported or missing migration snapshot report formatVersion");
		}
		required(report.generatedAt(), "generatedAt");
		required(report.effectiveAt(), "effectiveAt");
		nonBlank(report.snapshotId(), "snapshotId");
		nonBlank(report.sourceTable(), "sourceTable");
		nonBlank(report.targetTable(), "targetTable");
		nonBlank(report.databaseProductName(), "databaseProductName");
		nonBlank(report.databaseProductVersion(), "databaseProductVersion");
		nonBlank(report.executorClassName(), "executorClassName");
		columns(report.keyColumns(), "keyColumns");
		columns(report.trackedColumns(), "trackedColumns");
		if (report.fetchSize() <= 0 || report.batchSize() <= 0 || report.expiredRows() < 0
				|| report.insertedRows() < 0 || report.unchangedRows() < 0) {
			throw new CommandException("Migration snapshot report contains invalid sizes or counts");
		}
		return report;
	}

	private static void columns(final java.util.List<String> values, final String name) {
		if (values == null || values.isEmpty() || values.stream().anyMatch(x -> x == null || x.isBlank())
				|| new HashSet<>(values).size() != values.size()) {
			throw new CommandException("Migration snapshot report contains invalid " + name);
		}
	}

	private static void required(final Object value, final String name) {
		if (value == null) throw new CommandException("Migration snapshot report requires " + name);
	}

	private static void nonBlank(final String value, final String name) {
		if (value == null || value.isBlank()) throw new CommandException("Migration snapshot report requires " + name);
	}
}
