/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Machine-readable audit result for one migration command invocation. */
public record MigrationExecutionReport(int formatVersion, String reportFingerprint, long startedAtEpochMillis,
		long finishedAtEpochMillis, boolean successful, MigrationPlan.DatabaseIdentity databaseIdentity, String planFingerprint,
		List<Long> selectedVersions, List<Long> committedVersions, MigrationExecutionFailure failure) {
	public static final int CURRENT_FORMAT_VERSION = 1;

	public MigrationExecutionReport {
		if (formatVersion != CURRENT_FORMAT_VERSION) {
			throw new IllegalArgumentException("Unsupported migration execution report formatVersion: " + formatVersion);
		}
		selectedVersions = List.copyOf(selectedVersions);
		committedVersions = List.copyOf(committedVersions);
		if (startedAtEpochMillis <= 0 || finishedAtEpochMillis < startedAtEpochMillis) {
			throw new IllegalArgumentException("Invalid migration execution report timestamps");
		}
		if (new HashSet<>(selectedVersions).size() != selectedVersions.size()
				|| new HashSet<>(committedVersions).size() != committedVersions.size()
				|| !selectedVersions.containsAll(committedVersions)) {
			throw new IllegalArgumentException("Invalid selected or committed migration versions");
		}
		if (successful && failure != null) {
			throw new IllegalArgumentException("A successful migration report cannot contain failure details");
		}
		final String expected = fingerprint(formatVersion, startedAtEpochMillis, finishedAtEpochMillis, successful,
				databaseIdentity, planFingerprint, selectedVersions, committedVersions, failure);
		if (!expected.equals(reportFingerprint)) {
			throw new IllegalArgumentException("Migration execution report fingerprint mismatch");
		}
	}

	public MigrationExecutionReport(final int formatVersion, final long startedAtEpochMillis,
			final long finishedAtEpochMillis, final boolean successful,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final String planFingerprint,
			final List<Long> selectedVersions, final List<Long> committedVersions,
			final MigrationExecutionFailure failure) {
		this(formatVersion,
				fingerprint(formatVersion, startedAtEpochMillis, finishedAtEpochMillis, successful, databaseIdentity,
						planFingerprint, selectedVersions, committedVersions, failure),
				startedAtEpochMillis, finishedAtEpochMillis, successful, databaseIdentity, planFingerprint,
				selectedVersions, committedVersions, failure);
	}

	public static String fingerprint(final int formatVersion, final long startedAtEpochMillis,
			final long finishedAtEpochMillis, final boolean successful,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final String planFingerprint,
			final List<Long> selectedVersions, final List<Long> committedVersions,
			final MigrationExecutionFailure failure) {
		Objects.requireNonNull(selectedVersions, "selectedVersions");
		Objects.requireNonNull(committedVersions, "committedVersions");
		final StringBuilder value = new StringBuilder();
		add(value, formatVersion);
		add(value, startedAtEpochMillis);
		add(value, finishedAtEpochMillis);
		add(value, successful);
		add(value, databaseIdentity);
		add(value, planFingerprint);
		add(value, selectedVersions);
		add(value, committedVersions);
		add(value, failure);
		try {
			return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void add(final StringBuilder builder, final Object value) {
		final String text = String.valueOf(value);
		builder.append(text.length()).append(':').append(text).append(';');
	}
}
