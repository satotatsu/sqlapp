/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
			if (!migrationId.equals(values.getProperty("migrationId"))) {
				throw new SQLException("Checkpoint migrationId does not match its file: " + file);
			}
			return Optional.of(BulkMigrationCheckpoint.builder()
					.migrationId(migrationId)
					.sourceFingerprint(emptyToNull(values.getProperty("sourceFingerprint")))
					.targetFingerprint(emptyToNull(values.getProperty("targetFingerprint")))
					.processedRows(Long.parseLong(values.getProperty("processedRows")))
					.completedChunks(Long.parseLong(values.getProperty("completedChunks")))
					.lastChunkHash(emptyToNull(values.getProperty("lastChunkHash")))
					.resumeToken(emptyToNull(values.getProperty("resumeToken")))
					.complete(Boolean.parseBoolean(values.getProperty("complete"))).build()
					.validate());
		} catch (IOException | IllegalArgumentException e) {
			throw new SQLException("Failed to read migration checkpoint: " + file, e);
		}
	}

	@Override
	public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
		java.util.Objects.requireNonNull(checkpoint, "checkpoint").validate();
		final Path file = file(checkpoint.getMigrationId());
		Path temporary = null;
		try {
			Files.createDirectories(directory);
			temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
			final Properties values = new Properties();
			values.setProperty("migrationId", checkpoint.getMigrationId());
			values.setProperty("sourceFingerprint", nullToEmpty(checkpoint.getSourceFingerprint()));
			values.setProperty("targetFingerprint", nullToEmpty(checkpoint.getTargetFingerprint()));
			values.setProperty("processedRows", Long.toString(checkpoint.getProcessedRows()));
			values.setProperty("completedChunks", Long.toString(checkpoint.getCompletedChunks()));
			values.setProperty("lastChunkHash", nullToEmpty(checkpoint.getLastChunkHash()));
			values.setProperty("resumeToken", nullToEmpty(checkpoint.getResumeToken()));
			values.setProperty("complete", Boolean.toString(checkpoint.isComplete()));
			try (OutputStream output = Files.newOutputStream(temporary)) {
				values.store(output, "sqlapp bulk migration checkpoint");
			}
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
			temporary = null;
		} catch (IOException e) {
			throw new SQLException("Failed to save migration checkpoint: " + file, e);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException e) {
					// Preserve the primary write failure; orphaned temp files are harmless.
				}
			}
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
}
