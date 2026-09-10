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
	public Optional<BulkMigrationMaintenanceState> load(final String jobId)
			throws SQLException {
		validateJobId(jobId);
		final Path file = file(jobId);
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		final Properties values = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			values.load(input);
			if (!jobId.equals(required(values, "jobId"))) {
				throw new IllegalArgumentException(
						"maintenance jobId does not match its file");
			}
			final BulkMigrationMaintenanceStatus status = BulkMigrationMaintenanceStatus
					.valueOf(required(values, "status"));
			final String failureMessage = emptyToNull(values.getProperty("failureMessage"));
			return Optional.of(new BulkMigrationMaintenanceState(jobId,
					required(values, "planFingerprint"), status,
					Instant.parse(required(values, "updatedAt")), failureMessage));
		} catch (IOException | IllegalArgumentException e) {
			throw new SQLException("Failed to read migration maintenance state: " + file, e);
		}
	}

	@Override
	public void save(final BulkMigrationMaintenanceState state) throws SQLException {
		java.util.Objects.requireNonNull(state, "state");
		validateJobId(state.jobId());
		final Path file = file(state.jobId());
		try {
			final Properties values = new Properties();
			values.setProperty("jobId", state.jobId());
			values.setProperty("planFingerprint", state.planFingerprint());
			values.setProperty("status", state.status().name());
			values.setProperty("updatedAt", state.updatedAt().toString());
			values.setProperty("failureMessage", nullToEmpty(state.failureMessage()));
			AtomicMigrationFile.writeProperties(file, values,
					"sqlapp bulk migration maintenance state");
		} catch (IOException e) {
			throw new SQLException("Failed to save migration maintenance state: " + file, e);
		}
	}

	@Override
	public void delete(final String jobId) throws SQLException {
		validateJobId(jobId);
		try {
			Files.deleteIfExists(file(jobId));
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

	private static void validateJobId(final String jobId) {
		if (jobId == null || jobId.isBlank()) {
			throw new IllegalArgumentException("jobId must not be empty");
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
