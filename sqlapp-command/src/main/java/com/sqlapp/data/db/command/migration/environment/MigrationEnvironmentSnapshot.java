/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.environment;

import com.sqlapp.data.db.command.migration.schema.MigrationPlan;
import com.sqlapp.data.db.command.migration.schema.Status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Tamper-evident migration state captured from one environment. */
public record MigrationEnvironmentSnapshot(int formatVersion, String fingerprint, String environmentId,
		long capturedAtEpochMillis, MigrationPlan.DatabaseIdentity databaseIdentity,
		List<VersionedEntry> versioned, List<RepeatableEntry> repeatables) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public MigrationEnvironmentSnapshot {
		if (formatVersion != CURRENT_FORMAT_VERSION || environmentId == null || environmentId.isBlank()
				|| capturedAtEpochMillis <= 0) {
			throw new IllegalArgumentException("Invalid migration environment snapshot identity or format");
		}
		versioned = List.copyOf(versioned);
		repeatables = List.copyOf(repeatables);
		if (new HashSet<>(versioned.stream().map(VersionedEntry::version).toList()).size() != versioned.size()
				|| new HashSet<>(repeatables.stream().map(RepeatableEntry::name).toList()).size() != repeatables.size()) {
			throw new IllegalArgumentException("Duplicate migration identity in environment snapshot");
		}
		if (!fingerprint(environmentId, capturedAtEpochMillis, databaseIdentity, versioned, repeatables)
				.equals(fingerprint)) {
			throw new IllegalArgumentException("Migration environment snapshot fingerprint mismatch");
		}
	}

	public MigrationEnvironmentSnapshot(final String environmentId, final long capturedAtEpochMillis,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final List<VersionedEntry> versioned,
			final List<RepeatableEntry> repeatables) {
		this(CURRENT_FORMAT_VERSION,
				fingerprint(environmentId, capturedAtEpochMillis, databaseIdentity, versioned, repeatables),
				environmentId, capturedAtEpochMillis, databaseIdentity, versioned, repeatables);
	}

	public record VersionedEntry(long version, Status status, String checksum) {
	}

	public record RepeatableEntry(String name, String checksum) {
	}

	private static String fingerprint(final String environmentId, final long capturedAtEpochMillis,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final List<VersionedEntry> versioned,
			final List<RepeatableEntry> repeatables) {
		Objects.requireNonNull(versioned, "versioned");
		Objects.requireNonNull(repeatables, "repeatables");
		final String value = environmentId + "\n" + capturedAtEpochMillis + "\n" + databaseIdentity + "\n"
				+ versioned + "\n" + repeatables;
		try {
			return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
