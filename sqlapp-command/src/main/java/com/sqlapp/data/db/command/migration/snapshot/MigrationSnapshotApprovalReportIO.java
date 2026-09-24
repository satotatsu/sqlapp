/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.snapshot;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes review-only SCD2 approval artifacts. */
public final class MigrationSnapshotApprovalReportIO {
	private final Clock clock;

	public MigrationSnapshotApprovalReportIO() {
		this(Clock.systemUTC());
	}

	public MigrationSnapshotApprovalReportIO(final Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public record ApprovedArtifact(MigrationSnapshotApprovalReport report, String artifactFingerprint) {
	}

	public MigrationSnapshotApprovalReport fromResolution(
			final MigrationSnapshotConfigurationResolver.Resolution resolution) {
		Objects.requireNonNull(resolution, "resolution");
		return validate(new MigrationSnapshotApprovalReport(MigrationSnapshotApprovalReport.CURRENT_FORMAT_VERSION,
				clock.instant(), resolution.configurationFingerprint(), resolution.definition().id(),
				name(resolution.sourceTable()), name(resolution.targetTable()), resolution.definition().keyColumns(),
				resolution.definition().trackedColumns(), resolution.definition().expireMissingRows(),
				resolution.effectiveAt(), resolution.fetchSize(), resolution.batchSize(),
				resolution.approvalValidFor()));
	}

	public void write(final Path file, final MigrationSnapshotApprovalReport report) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		validate(report);
		try {
			AtomicMigrationFile.write(absolute, temporary -> converter().writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException)
				throw commandException;
			throw new CommandException("Failed to write migration snapshot approval report: " + absolute, e);
		}
	}

	public MigrationSnapshotApprovalReport read(final Path file) {
		return load(file).report();
	}

	public ApprovedArtifact readArtifact(final Path file) {
		return load(file);
	}

	private ApprovedArtifact load(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration snapshot approval report does not exist: " + absolute);
		}
		try {
			final byte[] bytes = Files.readAllBytes(absolute);
			final MigrationSnapshotApprovalReport report = validate(converter()
					.fromJsonString(new String(bytes, StandardCharsets.UTF_8), MigrationSnapshotApprovalReport.class));
			return new ApprovedArtifact(report, sha256(bytes));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException)
				throw commandException;
			throw new CommandException("Failed to read migration snapshot approval report: " + absolute, e);
		}
	}

	public MigrationSnapshotApprovalReport read(final Path file, final String expectedConfigurationFingerprint) {
		if (expectedConfigurationFingerprint == null || expectedConfigurationFingerprint.isBlank()) {
			throw new IllegalArgumentException("expectedConfigurationFingerprint must not be empty");
		}
		final MigrationSnapshotApprovalReport report = read(file);
		if (!expectedConfigurationFingerprint.equals(report.configurationFingerprint())) {
			throw new CommandException("Migration snapshot approval report configuration fingerprint mismatch");
		}
		return report;
	}

	public MigrationSnapshotApprovalReport read(final Path file,
			final MigrationSnapshotConfigurationResolver.Resolution expected) {
		return readApproved(file, expected).report();
	}

	public ApprovedArtifact readApproved(final Path file,
			final MigrationSnapshotConfigurationResolver.Resolution expected) {
		Objects.requireNonNull(expected, "expected");
		final ApprovedArtifact artifact = load(file);
		final MigrationSnapshotApprovalReport report = artifact.report();
		if (!expected.configurationFingerprint().equals(report.configurationFingerprint())) {
			throw new CommandException("Migration snapshot approval report configuration fingerprint mismatch");
		}
		final MigrationSnapshotApprovalReport resolved = fromResolution(expected);
		matches(report.snapshotId(), resolved.snapshotId(), "snapshotId");
		matches(report.sourceTable(), resolved.sourceTable(), "sourceTable");
		matches(report.targetTable(), resolved.targetTable(), "targetTable");
		matches(report.keyColumns(), resolved.keyColumns(), "keyColumns");
		matches(report.trackedColumns(), resolved.trackedColumns(), "trackedColumns");
		matches(report.expireMissingRows(), resolved.expireMissingRows(), "expireMissingRows");
		matches(report.effectiveAt(), resolved.effectiveAt(), "effectiveAt");
		matches(report.fetchSize(), resolved.fetchSize(), "fetchSize");
		matches(report.batchSize(), resolved.batchSize(), "batchSize");
		matches(report.approvalValidFor(), resolved.approvalValidFor(), "approvalValidFor");
		return artifact;
	}

	private static JsonConverter converter() {
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		return converter;
	}

	static MigrationSnapshotApprovalReport validate(final MigrationSnapshotApprovalReport report) {
		if (report == null || report.formatVersion() != MigrationSnapshotApprovalReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported or missing migration snapshot approval report formatVersion");
		}
		required(report.generatedAt(), "generatedAt");
		required(report.effectiveAt(), "effectiveAt");
		nonBlank(report.snapshotId(), "snapshotId");
		nonBlank(report.sourceTable(), "sourceTable");
		nonBlank(report.targetTable(), "targetTable");
		if (report.configurationFingerprint() == null
				|| !report.configurationFingerprint().matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("Migration snapshot approval report contains invalid configurationFingerprint");
		}
		columns(report.keyColumns(), "keyColumns");
		columns(report.trackedColumns(), "trackedColumns");
		if (report.fetchSize() <= 0 || report.batchSize() <= 0) {
			throw new CommandException("Migration snapshot approval report contains invalid sizes");
		}
		if (report.approvalValidFor() != null
				&& (report.approvalValidFor().isZero() || report.approvalValidFor().isNegative())) {
			throw new CommandException("Migration snapshot approval report contains invalid approvalValidFor");
		}
		return report;
	}

	private static String name(final Table table) {
		final var parts = new java.util.ArrayList<String>();
		if (table.getCatalogName() != null)
			parts.add(table.getCatalogName());
		if (table.getSchemaName() != null)
			parts.add(table.getSchemaName());
		parts.add(table.getName());
		return String.join(".", parts);
	}

	private static void columns(final java.util.List<String> values, final String name) {
		if (values == null || values.isEmpty() || values.stream().anyMatch(x -> x == null || x.isBlank())
				|| new HashSet<>(values).size() != values.size()) {
			throw new CommandException("Migration snapshot approval report contains invalid " + name);
		}
	}

	private static void required(final Object value, final String name) {
		if (value == null)
			throw new CommandException("Migration snapshot approval report requires " + name);
	}

	private static void nonBlank(final String value, final String name) {
		if (value == null || value.isBlank()) {
			throw new CommandException("Migration snapshot approval report requires " + name);
		}
	}

	private static void matches(final Object actual, final Object expected, final String name) {
		if (!Objects.equals(actual, expected)) {
			throw new CommandException("Migration snapshot approval report " + name + " mismatch");
		}
	}

	private static String sha256(final byte[] bytes) {
		try {
			return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}
}
