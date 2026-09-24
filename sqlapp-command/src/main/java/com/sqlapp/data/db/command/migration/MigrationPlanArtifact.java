/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Versioned machine-readable wrapper for a migration plan. */
public record MigrationPlanArtifact(int formatVersion, long createdAtEpochMillis, String planFingerprint,
		MigrationPlan plan) {
	public static final int CURRENT_FORMAT_VERSION = 9;
	private static final int PREVIOUS_FORMAT_VERSION = 8;
	private static final int LEGACY_FORMAT_VERSION = 7;

	public MigrationPlanArtifact {
		if (formatVersion != LEGACY_FORMAT_VERSION && formatVersion != PREVIOUS_FORMAT_VERSION
				&& formatVersion != CURRENT_FORMAT_VERSION) {
			throw new IllegalArgumentException("Unsupported migration plan formatVersion: " + formatVersion);
		}
		Objects.requireNonNull(plan, "plan");
		if (formatVersion == LEGACY_FORMAT_VERSION) {
			if (!fingerprint(plan).equals(planFingerprint)) {
				throw new IllegalArgumentException("Migration plan fingerprint mismatch");
			}
		} else if (createdAtEpochMillis <= 0) {
			throw new IllegalArgumentException("Migration plan createdAtEpochMillis must be positive");
		} else if (!(formatVersion == PREVIOUS_FORMAT_VERSION
				? fingerprint(createdAtEpochMillis, plan, false) : fingerprint(createdAtEpochMillis, plan))
				.equals(planFingerprint)) {
			throw new IllegalArgumentException("Migration plan fingerprint mismatch");
		}
	}

	public MigrationPlanArtifact(final int formatVersion, final long createdAtEpochMillis, final MigrationPlan plan) {
		this(formatVersion, createdAtEpochMillis,
				formatVersion == LEGACY_FORMAT_VERSION ? fingerprint(plan)
						: formatVersion == PREVIOUS_FORMAT_VERSION ? fingerprint(createdAtEpochMillis, plan, false)
								: fingerprint(createdAtEpochMillis, plan),
				plan);
	}

	/** Retains the constructor used by format-version 7 callers. */
	public MigrationPlanArtifact(final int formatVersion, final MigrationPlan plan) {
		this(formatVersion, formatVersion == LEGACY_FORMAT_VERSION ? 0L : System.currentTimeMillis(), plan);
	}

	public static MigrationPlanArtifact of(final MigrationPlan plan) {
		return new MigrationPlanArtifact(CURRENT_FORMAT_VERSION, System.currentTimeMillis(), plan);
	}

	public static String fingerprint(final MigrationPlan plan) {
		return fingerprint(null, plan);
	}

	public static String fingerprint(final long createdAtEpochMillis, final MigrationPlan plan) {
		return fingerprint(Long.valueOf(createdAtEpochMillis), plan);
	}

	private static String fingerprint(final Long createdAtEpochMillis, final MigrationPlan plan) {
		return fingerprint(createdAtEpochMillis, plan, true);
	}

	private static String fingerprint(final Long createdAtEpochMillis, final MigrationPlan plan,
			final boolean includeRepeatables) {
		Objects.requireNonNull(plan, "plan");
		final StringBuilder value = new StringBuilder();
		if (createdAtEpochMillis != null) {
			add(value, createdAtEpochMillis);
		}
		add(value, plan.historyExists());
		add(value, plan.currentVersion());
		add(value, plan.targetVersion());
		add(value, plan.setupStatements());
		add(value, plan.finalizeStatements());
		for (final var entry : plan.pending()) {
			add(value, entry.version());
			add(value, entry.description());
			add(value, entry.statements());
			add(value, entry.transactional());
			add(value, entry.checksumWillBeRecorded());
			add(value, entry.rollbackAvailable());
			add(value, entry.sourceChecksum());
		}
		for (final var issue : plan.historyIssues()) {
			add(value, issue.version());
			add(value, issue.status());
		}
		if (plan.checksumValidation() != null) {
			for (final var entry : plan.checksumValidation().entries()) {
				add(value, entry.version());
				add(value, entry.state());
				add(value, entry.expectedChecksum());
				add(value, entry.actualChecksum());
			}
		}
		add(value, plan.schemaDrift());
		add(value, plan.outOfOrderVersions());
		add(value, plan.outOfOrderRejected());
		add(value, plan.nonTransactionalRejected());
		add(value, plan.downMigrationRequired());
		add(value, plan.databaseIdentity());
		if (includeRepeatables) {
			for (final var entry : plan.pendingRepeatables()) {
				add(value, entry.name());
				add(value, entry.statements());
				add(value, entry.transactional());
				add(value, entry.sourceChecksum());
				add(value, entry.previousChecksum());
			}
		}
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
