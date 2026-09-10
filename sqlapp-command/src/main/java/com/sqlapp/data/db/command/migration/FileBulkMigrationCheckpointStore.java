/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;

import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;

/** Durable per-migration checkpoint files updated by atomic replacement. */
public class FileBulkMigrationCheckpointStore implements BulkMigrationCheckpointStore {
	private final Path directory;

	public FileBulkMigrationCheckpointStore(final Path directory) {
		this.directory = java.util.Objects.requireNonNull(directory, "directory")
				.toAbsolutePath().normalize();
	}

	@Override
	public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
		final Path file = file(migrationId);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		final Properties values = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			values.load(input);
			if (!migrationId.equals(required(values, "migrationId"))) {
				throw new IllegalArgumentException(
						"checkpoint migrationId does not match its file");
			}
			return Optional.of(BulkMigrationCheckpoint.builder()
					.migrationId(migrationId)
					.sourceFingerprint(emptyToNull(values.getProperty("sourceFingerprint")))
					.targetFingerprint(emptyToNull(values.getProperty("targetFingerprint")))
					.processedRows(longValue(values, "processedRows"))
					.completedChunks(longValue(values, "completedChunks"))
					.chunkSize(intValue(values, "chunkSize"))
					.lastChunkHash(emptyToNull(values.getProperty("lastChunkHash")))
					.resumeToken(emptyToNull(values.getProperty("resumeToken")))
					.complete(booleanValue(values, "complete")).build()
					.validate());
		} catch (IOException | IllegalArgumentException e) {
			throw new SQLException("Failed to read migration checkpoint: " + file, e);
		}
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
		java.util.Objects.requireNonNull(checkpoint, "checkpoint").validate();
		final Path file = file(checkpoint.getMigrationId());
		try {
			final Properties values = new Properties();
			values.setProperty("migrationId", checkpoint.getMigrationId());
			values.setProperty("sourceFingerprint", nullToEmpty(checkpoint.getSourceFingerprint()));
			values.setProperty("targetFingerprint", nullToEmpty(checkpoint.getTargetFingerprint()));
			values.setProperty("processedRows", Long.toString(checkpoint.getProcessedRows()));
			values.setProperty("completedChunks", Long.toString(checkpoint.getCompletedChunks()));
			values.setProperty("chunkSize", Integer.toString(checkpoint.getChunkSize()));
			values.setProperty("lastChunkHash", nullToEmpty(checkpoint.getLastChunkHash()));
			values.setProperty("resumeToken", nullToEmpty(checkpoint.getResumeToken()));
			values.setProperty("complete", Boolean.toString(checkpoint.isComplete()));
			AtomicMigrationFile.writeProperties(file, values,
					"sqlapp bulk migration checkpoint");
		} catch (IOException e) {
			throw new SQLException("Failed to save migration checkpoint: " + file, e);
		}
	}

	@Override
	public void delete(final String migrationId) throws SQLException {
		try {
			Files.deleteIfExists(file(migrationId));
		} catch (IOException e) {
			throw new SQLException("Failed to delete migration checkpoint: " + migrationId, e);
		}
	}

	private Path file(final String migrationId) {
		BulkMigrationCheckpoint.validateMigrationId(migrationId);
		try {
			final byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(migrationId.getBytes(StandardCharsets.UTF_8));
			return directory.resolve(HexFormat.of().formatHex(hash) + ".checkpoint");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	private static String emptyToNull(final String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	private static String required(final Properties values, final String name) {
		final String value = values.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"Missing checkpoint property: " + name);
		}
		return value;
	}

	private static long longValue(final Properties values, final String name) {
		return Long.parseLong(required(values, name));
	}

	private static int intValue(final Properties values, final String name) {
		return Integer.parseInt(required(values, name));
	}

	private static boolean booleanValue(final Properties values,
			final String name) {
		final String value = required(values, name);
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		throw new IllegalArgumentException(
				"Invalid boolean checkpoint property " + name + ": " + value);
	}
}
