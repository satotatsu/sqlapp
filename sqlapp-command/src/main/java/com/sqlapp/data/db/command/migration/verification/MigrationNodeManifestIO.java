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
import com.sqlapp.data.schemas.migration.MigrationNodeManifest;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/**
 * Atomically persists the state artifact consumed by selective migration runs.
 */
public final class MigrationNodeManifestIO {
	public record Snapshot(MigrationNodeManifest manifest, String fingerprint) {
	}

	public MigrationNodeManifest read(final Path file) {
		return readSnapshot(file, null).manifest();
	}

	public MigrationNodeManifest read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).manifest();
	}

	public Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration node manifest does not exist: " + absolute);
		}
		try {
			final byte[] bytes = BoundedMigrationFile.read(absolute, maxFileSizeBytes,
					"maxNodeManifestFileSizeBytes", "Migration node manifest");
			final var manifest = validate(new JsonConverter().fromJsonString(
					new String(bytes, StandardCharsets.UTF_8), MigrationNodeManifest.class));
			return new Snapshot(manifest,
					"sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
		} catch (IOException e) {
			throw new CommandException("Failed to read migration node manifest: " + absolute, e);
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read migration node manifest: " + absolute, e);
		}
	}

	public void write(final Path file, final MigrationNodeManifest manifest) {
		writeSnapshot(file, manifest, null);
	}

	public Snapshot writeSnapshot(final Path file, final MigrationNodeManifest manifest,
			final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		final MigrationNodeManifest validated = validate(manifest);
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), validated));
			final Snapshot snapshot = readSnapshot(absolute, maxFileSizeBytes);
			if (!validated.equals(snapshot.manifest())) {
				throw new CommandException("Written migration node manifest does not match the requested manifest");
			}
			return snapshot;
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
