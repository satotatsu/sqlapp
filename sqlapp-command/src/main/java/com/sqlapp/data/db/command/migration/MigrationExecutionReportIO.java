/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Atomically writes migration execution audit reports. */
public final class MigrationExecutionReportIO {
	public MigrationExecutionReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration execution report does not exist: " + absolute);
		}
		try {
			return new JsonConverter().fromJsonString(absolute.toFile(), MigrationExecutionReport.class);
		} catch (final RuntimeException e) {
			throw new CommandException("Failed to read migration execution report: " + absolute, e);
		}
	}

	public void write(final Path file, final MigrationExecutionReport report) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute,
					temporary -> converter.writeJsonValue(temporary.toFile(), Objects.requireNonNull(report, "report")));
		} catch (final IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to write migration execution report: " + absolute, e);
		}
	}
}
