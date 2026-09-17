/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes bounded SCD2 failure reports. */
public final class MigrationSnapshotFailureReportIO {
	public static final int FAILURE_MESSAGE_MAX_LENGTH = 4_096;

	public void write(final Path file, final MigrationSnapshotFailureReport report) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		validate(report);
		try {
			AtomicMigrationFile.write(absolute, temporary -> converter().writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) throw commandException;
			throw new CommandException("Failed to write migration snapshot failure report: " + absolute, e);
		}
	}

	public MigrationSnapshotFailureReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration snapshot failure report does not exist: " + absolute);
		}
		try {
			return validate(converter().fromJsonString(absolute.toFile(), MigrationSnapshotFailureReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) throw commandException;
			throw new CommandException("Failed to read migration snapshot failure report: " + absolute, e);
		}
	}

	public MigrationSnapshotApprovalReport verifyApproval(final MigrationSnapshotFailureReport report,
			final Path approvalFile) {
		validate(report);
		if (report.approvalGeneratedAt() == null || report.approvalArtifactFingerprint() == null) {
			throw new CommandException("Migration snapshot failure report does not contain approval evidence");
		}
		final var artifact = new MigrationSnapshotApprovalReportIO().readArtifact(approvalFile);
		final var approval = artifact.report();
		matches(artifact.artifactFingerprint(), report.approvalArtifactFingerprint(), "artifact fingerprint");
		matches(approval.generatedAt(), report.approvalGeneratedAt(), "generatedAt");
		matches(approval.configurationFingerprint(), report.configurationFingerprint(), "configuration fingerprint");
		matches(approval.snapshotId(), report.snapshotId(), "snapshotId");
		matches(approval.sourceTable(), report.sourceTable(), "sourceTable");
		matches(approval.targetTable(), report.targetTable(), "targetTable");
		matches(approval.effectiveAt(), report.effectiveAt(), "effectiveAt");
		if (approval.approvalValidFor() != null
				&& !approval.generatedAt().plus(approval.approvalValidFor()).isAfter(report.startedAt())) {
			throw new CommandException("Migration snapshot failure approval was expired at execution start");
		}
		return approval;
	}

	static MigrationSnapshotFailureReport validate(final MigrationSnapshotFailureReport report) {
		if (report == null || report.formatVersion() != MigrationSnapshotFailureReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported or missing migration snapshot failure report formatVersion");
		}
		required(report.generatedAt(), "generatedAt");
		required(report.startedAt(), "startedAt");
		required(report.effectiveAt(), "effectiveAt");
		if (report.generatedAt().isBefore(report.startedAt())) {
			throw new CommandException("Migration snapshot failure report generatedAt precedes startedAt");
		}
		required(report.phase(), "phase");
		nonBlank(report.snapshotId(), "snapshotId");
		nonBlank(report.sourceTable(), "sourceTable");
		nonBlank(report.targetTable(), "targetTable");
		nonBlank(report.failureType(), "failureType");
		nonBlank(report.failureMessage(), "failureMessage");
		if (report.failureMessage().length() > FAILURE_MESSAGE_MAX_LENGTH) {
			throw new CommandException("Migration snapshot failure report failureMessage is too long");
		}
		if (report.configurationFingerprint() == null
				|| !report.configurationFingerprint().matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("Migration snapshot failure report contains invalid configurationFingerprint");
		}
		if ((report.approvalGeneratedAt() == null) != (report.approvalArtifactFingerprint() == null)
				|| report.approvalArtifactFingerprint() != null
						&& !report.approvalArtifactFingerprint().matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("Migration snapshot failure report contains invalid approval evidence");
		}
		if (report.approvalGeneratedAt() != null && report.approvalGeneratedAt().isAfter(report.startedAt())) {
			throw new CommandException("Migration snapshot failure report approval postdates execution start");
		}
		return report;
	}

	private static JsonConverter converter() {
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		return converter;
	}

	private static void required(final Object value, final String name) {
		if (value == null) throw new CommandException("Migration snapshot failure report requires " + name);
	}

	private static void nonBlank(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new CommandException("Migration snapshot failure report requires " + name);
		}
	}

	private static void matches(final Object approval, final Object failure, final String name) {
		if (!Objects.equals(approval, failure)) {
			throw new CommandException("Migration snapshot failure approval evidence " + name + " mismatch");
		}
	}
}
