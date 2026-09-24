/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes versioned migration-plan JSON artifacts. */
public final class MigrationPlanIO {
	public void write(final Path file, final MigrationPlan plan) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		try {
			final JsonConverter converter = converter();
			final MigrationPlanArtifact artifact = MigrationPlanArtifact.of(plan);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), artifact));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to write migration plan: " + absolute, e);
		}
	}

	public MigrationPlanArtifact read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration plan does not exist: " + absolute);
		}
		try {
			return converter().fromJsonString(absolute.toFile(), MigrationPlanArtifact.class);
		} catch (RuntimeException e) {
			throw new CommandException("Failed to read migration plan: " + absolute, e);
		}
	}

	private static JsonConverter converter() {
		final JsonConverter converter = new JsonConverter();
		converter.setIndentOutput(true);
		return converter;
	}
}
