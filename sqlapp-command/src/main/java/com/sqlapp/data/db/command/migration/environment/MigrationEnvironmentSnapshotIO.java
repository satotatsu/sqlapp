/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes migration environment snapshots. */
public final class MigrationEnvironmentSnapshotIO {
	public void write(final Path file, final MigrationEnvironmentSnapshot snapshot) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		try {
			final JsonConverter converter = converter();
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), snapshot));
		} catch (final IOException | RuntimeException e) {
			throw e instanceof CommandException commandException ? commandException
					: new CommandException("Failed to write migration environment snapshot: " + absolute, e);
		}
	}

	public MigrationEnvironmentSnapshot read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration environment snapshot does not exist: " + absolute);
		}
		try {
			return converter().fromJsonString(absolute.toFile(), MigrationEnvironmentSnapshot.class);
		} catch (final RuntimeException e) {
			throw new CommandException("Failed to read migration environment snapshot: " + absolute, e);
		}
	}

	private static JsonConverter converter() {
		final JsonConverter converter = new JsonConverter();
		converter.setIndentOutput(true);
		return converter;
	}
}
