/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;

import com.sqlapp.data.schemas.migration.MigrationNodeManifest;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/**
 * Atomically persists the state artifact consumed by selective migration runs.
 */
public final class MigrationNodeManifestIO {

	public MigrationNodeManifest read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration node manifest does not exist: " + absolute);
		}
		try {
			return validate(new JsonConverter().fromJsonString(absolute.toFile(), MigrationNodeManifest.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read migration node manifest: " + absolute, e);
		}
	}

	public void write(final Path file, final MigrationNodeManifest manifest) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		final MigrationNodeManifest validated = validate(manifest);
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), validated));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write migration node manifest: " + absolute, e);
		}
	}

	private static MigrationNodeManifest validate(final MigrationNodeManifest manifest) {
		if (manifest == null || manifest.planFingerprint() == null || manifest.planFingerprint().isBlank()) {
			throw new CommandException("Migration node manifest header is invalid");
		}
		for (final var node : manifest.nodes().values()) {
			if (new HashSet<>(node.dependencies()).size() != node.dependencies().size()) {
				throw new CommandException("Migration node dependencies must be unique: " + node.id());
			}
			for (final String dependency : node.dependencies()) {
				if (!manifest.nodes().containsKey(dependency)) {
					throw new CommandException(
							"Migration node dependency is missing: " + node.id() + " -> " + dependency);
				}
			}
		}
		return manifest;
	}
}
