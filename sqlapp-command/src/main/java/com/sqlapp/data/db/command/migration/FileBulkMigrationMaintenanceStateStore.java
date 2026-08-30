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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;

import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStateStore;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;

/** Atomically replaced file storage for migration maintenance state. */
public class FileBulkMigrationMaintenanceStateStore
		implements BulkMigrationMaintenanceStateStore {
	private final Path directory;

	public FileBulkMigrationMaintenanceStateStore(final Path directory) {
		this.directory = java.util.Objects.requireNonNull(directory, "directory")
				.toAbsolutePath().normalize();
	}

	@Override
	public Optional<BulkMigrationMaintenanceState> load(final String planFingerprint)
			throws SQLException {
		validateFingerprint(planFingerprint);
		final Path file = file(planFingerprint);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		final Properties values = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			values.load(input);
			if (!planFingerprint.equals(required(values, "planFingerprint"))) {
				throw new IllegalArgumentException(
						"maintenance planFingerprint does not match its file");
			}
			final BulkMigrationMaintenanceStatus status = BulkMigrationMaintenanceStatus
					.valueOf(required(values, "status"));
			final String failureMessage = emptyToNull(values.getProperty("failureMessage"));
			return Optional.of(new BulkMigrationMaintenanceState(planFingerprint, status,
					Instant.parse(required(values, "updatedAt")), failureMessage));
		} catch (IOException | IllegalArgumentException e) {
			throw new SQLException("Failed to read migration maintenance state: " + file, e);
		}
	}

	@Override
	public void save(final BulkMigrationMaintenanceState state) throws SQLException {
		java.util.Objects.requireNonNull(state, "state");
		validateFingerprint(state.planFingerprint());
		final Path file = file(state.planFingerprint());
		Path temporary = null;
		try {
			Files.createDirectories(directory);
			temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
			final Properties values = new Properties();
			values.setProperty("planFingerprint", state.planFingerprint());
			values.setProperty("status", state.status().name());
			values.setProperty("updatedAt", state.updatedAt().toString());
			values.setProperty("failureMessage", nullToEmpty(state.failureMessage()));
			try (OutputStream output = Files.newOutputStream(temporary)) {
				values.store(output, "sqlapp bulk migration maintenance state");
			}
			try {
				Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
			temporary = null;
		} catch (IOException e) {
			throw new SQLException("Failed to save migration maintenance state: " + file, e);
		} finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
				}
			}
		}
	}

	@Override
	public void delete(final String planFingerprint) throws SQLException {
		validateFingerprint(planFingerprint);
		try {
			Files.deleteIfExists(file(planFingerprint));
		} catch (IOException e) {
			throw new SQLException("Failed to delete migration maintenance state", e);
		}
	}

	private Path file(final String fingerprint) {
		try {
			final byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
			return directory.resolve(HexFormat.of().formatHex(hash) + ".maintenance");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void validateFingerprint(final String fingerprint) {
		if (fingerprint == null || fingerprint.isBlank()) {
			throw new IllegalArgumentException("planFingerprint must not be empty");
		}
	}

	private static String required(final Properties values, final String name) {
		final String value = values.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing maintenance state property: " + name);
		}
		return value;
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	private static String emptyToNull(final String value) {
		return value == null || value.isEmpty() ? null : value;
	}
}
