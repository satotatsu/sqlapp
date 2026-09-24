/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Machine-readable audit result for one migration command invocation. */
public record MigrationExecutionReport(int formatVersion, String reportFingerprint, long startedAtEpochMillis,
		long finishedAtEpochMillis, boolean successful, boolean executionRequested,
		MigrationPlan.DatabaseIdentity databaseIdentity, String planFingerprint,
		List<Long> selectedVersions, List<Long> committedVersions, List<String> selectedRepeatables,
		List<String> committedRepeatables, MigrationExecutionFailure failure) {
	public static final int CURRENT_FORMAT_VERSION = 3;
	private static final int PREVIOUS_FORMAT_VERSION = 2;

	public MigrationExecutionReport {
		if (formatVersion != PREVIOUS_FORMAT_VERSION && formatVersion != CURRENT_FORMAT_VERSION) {
			throw new IllegalArgumentException("Unsupported migration execution report formatVersion: " + formatVersion);
		}
		selectedVersions = List.copyOf(selectedVersions);
		committedVersions = List.copyOf(committedVersions);
		selectedRepeatables = selectedRepeatables == null ? List.of() : List.copyOf(selectedRepeatables);
		committedRepeatables = committedRepeatables == null ? List.of() : List.copyOf(committedRepeatables);
		if (startedAtEpochMillis <= 0 || finishedAtEpochMillis < startedAtEpochMillis) {
			throw new IllegalArgumentException("Invalid migration execution report timestamps");
		}
		if (new HashSet<>(selectedVersions).size() != selectedVersions.size()
				|| new HashSet<>(committedVersions).size() != committedVersions.size()
				|| !selectedVersions.containsAll(committedVersions)) {
			throw new IllegalArgumentException("Invalid selected or committed migration versions");
		}
		if (!executionRequested && !committedVersions.isEmpty()) {
			throw new IllegalArgumentException("A non-executing report cannot contain committed versions");
		}
		if (new HashSet<>(selectedRepeatables).size() != selectedRepeatables.size()
				|| new HashSet<>(committedRepeatables).size() != committedRepeatables.size()
				|| !selectedRepeatables.containsAll(committedRepeatables)) {
			throw new IllegalArgumentException("Invalid selected or committed repeatable migrations");
		}
		if (!executionRequested && !committedRepeatables.isEmpty()) {
			throw new IllegalArgumentException("A non-executing report cannot contain committed repeatable migrations");
		}
		if (successful && executionRequested && !selectedVersions.equals(committedVersions)) {
			throw new IllegalArgumentException("A successful execution must commit every selected version in order");
		}
		if (successful && executionRequested && !selectedRepeatables.equals(committedRepeatables)) {
			throw new IllegalArgumentException("A successful execution must commit every selected repeatable migration in order");
		}
		if (successful && failure != null) {
			throw new IllegalArgumentException("A successful migration report cannot contain failure details");
		}
		final String expected = fingerprint(formatVersion, startedAtEpochMillis, finishedAtEpochMillis, successful,
				executionRequested, databaseIdentity, planFingerprint, selectedVersions, committedVersions,
				selectedRepeatables, committedRepeatables, failure, formatVersion == CURRENT_FORMAT_VERSION);
		if (!expected.equals(reportFingerprint)) {
			throw new IllegalArgumentException("Migration execution report fingerprint mismatch");
		}
	}

	public MigrationExecutionReport(final int formatVersion, final long startedAtEpochMillis,
			final long finishedAtEpochMillis, final boolean successful, final boolean executionRequested,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final String planFingerprint,
			final List<Long> selectedVersions, final List<Long> committedVersions,
			final MigrationExecutionFailure failure) {
		this(formatVersion, startedAtEpochMillis, finishedAtEpochMillis, successful, executionRequested,
				databaseIdentity, planFingerprint, selectedVersions, committedVersions, List.of(), List.of(), failure);
	}

	public MigrationExecutionReport(final int formatVersion, final long startedAtEpochMillis,
			final long finishedAtEpochMillis, final boolean successful, final boolean executionRequested,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final String planFingerprint,
			final List<Long> selectedVersions, final List<Long> committedVersions,
			final List<String> selectedRepeatables, final List<String> committedRepeatables,
			final MigrationExecutionFailure failure) {
		this(formatVersion,
				fingerprint(formatVersion, startedAtEpochMillis, finishedAtEpochMillis, successful, executionRequested,
						databaseIdentity, planFingerprint, selectedVersions, committedVersions, selectedRepeatables,
						committedRepeatables, failure, formatVersion == CURRENT_FORMAT_VERSION),
				startedAtEpochMillis, finishedAtEpochMillis, successful, executionRequested, databaseIdentity, planFingerprint,
				selectedVersions, committedVersions, selectedRepeatables, committedRepeatables, failure);
	}

	public static String fingerprint(final int formatVersion, final long startedAtEpochMillis,
			final long finishedAtEpochMillis, final boolean successful, final boolean executionRequested,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final String planFingerprint,
			final List<Long> selectedVersions, final List<Long> committedVersions,
			final MigrationExecutionFailure failure) {
		return fingerprint(formatVersion, startedAtEpochMillis, finishedAtEpochMillis, successful, executionRequested,
				databaseIdentity, planFingerprint, selectedVersions, committedVersions, List.of(), List.of(), failure,
				false);
	}

	public static String fingerprint(final int formatVersion, final long startedAtEpochMillis,
			final long finishedAtEpochMillis, final boolean successful, final boolean executionRequested,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final String planFingerprint,
			final List<Long> selectedVersions, final List<Long> committedVersions,
			final List<String> selectedRepeatables, final List<String> committedRepeatables,
			final MigrationExecutionFailure failure) {
		return fingerprint(formatVersion, startedAtEpochMillis, finishedAtEpochMillis, successful, executionRequested,
				databaseIdentity, planFingerprint, selectedVersions, committedVersions, selectedRepeatables,
				committedRepeatables, failure, true);
	}

	private static String fingerprint(final int formatVersion, final long startedAtEpochMillis,
			final long finishedAtEpochMillis, final boolean successful, final boolean executionRequested,
			final MigrationPlan.DatabaseIdentity databaseIdentity, final String planFingerprint,
			final List<Long> selectedVersions, final List<Long> committedVersions,
			final List<String> selectedRepeatables, final List<String> committedRepeatables,
			final MigrationExecutionFailure failure, final boolean includeRepeatables) {
		Objects.requireNonNull(selectedVersions, "selectedVersions");
		Objects.requireNonNull(committedVersions, "committedVersions");
		if (includeRepeatables) {
			Objects.requireNonNull(selectedRepeatables, "selectedRepeatables");
			Objects.requireNonNull(committedRepeatables, "committedRepeatables");
		}
		final StringBuilder value = new StringBuilder();
		add(value, formatVersion);
		add(value, startedAtEpochMillis);
		add(value, finishedAtEpochMillis);
		add(value, successful);
		add(value, executionRequested);
		add(value, databaseIdentity);
		add(value, planFingerprint);
		add(value, selectedVersions);
		add(value, committedVersions);
		if (includeRepeatables) {
			add(value, selectedRepeatables);
			add(value, committedRepeatables);
		}
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
