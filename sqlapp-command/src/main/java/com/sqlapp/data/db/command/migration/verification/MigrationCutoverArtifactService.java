/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReportIO;
import com.sqlapp.exceptions.CommandException;

/** Shared file-only inspection and approval for migration cutover artifacts. */
public final class MigrationCutoverArtifactService {
	public static final String VERIFICATION_REPORT_FILE = "verification.json";
	public static final String CUTOVER_REPORT_FILE = "cutover.json";
	public static final String EVIDENCE_FILE = "evidence.json";
	private static final Set<String> PACKAGE_FILES = Set.of(VERIFICATION_REPORT_FILE, CUTOVER_REPORT_FILE,
			EVIDENCE_FILE);

	public record Inspection(MigrationCutoverEvidence evidence, String evidenceFingerprint,
			BulkMigrationVerificationReport verification, MigrationCutoverReport cutover) {
		public Inspection {
			Objects.requireNonNull(evidence, "evidence");
			Objects.requireNonNull(evidenceFingerprint, "evidenceFingerprint");
			Objects.requireNonNull(verification, "verification");
			Objects.requireNonNull(cutover, "cutover");
		}
	}

	public Inspection inspect(final Path evidenceFile, final Path verificationReport, final Path cutoverReport,
			final Long maxEvidenceFileSizeBytes, final Long maxVerificationFileSizeBytes,
			final Long maxCutoverFileSizeBytes) {
		return inspect(evidenceFile, null, verificationReport, cutoverReport, maxEvidenceFileSizeBytes,
				maxVerificationFileSizeBytes, maxCutoverFileSizeBytes);
	}

	public Inspection approve(final Path evidenceFile, final String expectedEvidenceFingerprint,
			final Path verificationReport, final Path cutoverReport, final Duration maximumReportAge,
			final Long maxEvidenceFileSizeBytes, final Long maxVerificationFileSizeBytes,
			final Long maxCutoverFileSizeBytes) {
		validateFingerprint(expectedEvidenceFingerprint);
		validateMaximumAge(maximumReportAge);
		final Inspection inspection = inspect(evidenceFile, expectedEvidenceFingerprint, verificationReport,
				cutoverReport, maxEvidenceFileSizeBytes, maxVerificationFileSizeBytes, maxCutoverFileSizeBytes);
		validateReadyAndRecent(inspection.cutover(), maximumReportAge, Instant.now());
		return inspection;
	}

	public Inspection inspectPackage(final Path packageDirectory, final Long maxEvidenceFileSizeBytes,
			final Long maxVerificationFileSizeBytes, final Long maxCutoverFileSizeBytes) {
		final Path directory = validatePackageDirectory(packageDirectory);
		return inspect(directory.resolve(EVIDENCE_FILE), directory.resolve(VERIFICATION_REPORT_FILE),
				directory.resolve(CUTOVER_REPORT_FILE), maxEvidenceFileSizeBytes, maxVerificationFileSizeBytes,
				maxCutoverFileSizeBytes);
	}

	public Inspection approvePackage(final Path packageDirectory, final String expectedEvidenceFingerprint,
			final Duration maximumReportAge, final Long maxEvidenceFileSizeBytes,
			final Long maxVerificationFileSizeBytes, final Long maxCutoverFileSizeBytes) {
		final Path directory = validatePackageDirectory(packageDirectory);
		return approve(directory.resolve(EVIDENCE_FILE), expectedEvidenceFingerprint,
				directory.resolve(VERIFICATION_REPORT_FILE), directory.resolve(CUTOVER_REPORT_FILE), maximumReportAge,
				maxEvidenceFileSizeBytes, maxVerificationFileSizeBytes, maxCutoverFileSizeBytes);
	}

	public Path validatePackageDirectory(final Path packageDirectory) {
		final Path directory = Objects.requireNonNull(packageDirectory, "packageDirectory").toAbsolutePath()
				.normalize();
		if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
			throw new CommandException(
					"Migration cutover package does not exist or is not a regular directory: " + directory);
		}
		try (var files = Files.list(directory)) {
			final Set<String> actual = files.map(path -> path.getFileName().toString())
					.collect(Collectors.toUnmodifiableSet());
			if (!PACKAGE_FILES.equals(actual)) {
				throw new CommandException("Migration cutover package must contain exactly " + PACKAGE_FILES);
			}
		} catch (IOException e) {
			throw new CommandException("Failed to inspect migration cutover package: " + directory, e);
		}
		for (final String name : PACKAGE_FILES) {
			final Path file = directory.resolve(name);
			if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
				throw new CommandException("Migration cutover package entry must be a regular file: " + name);
			}
		}
		return directory;
	}

	private Inspection inspect(final Path evidenceFile, final String expectedEvidenceFingerprint,
			final Path verificationReport, final Path cutoverReport, final Long maxEvidenceFileSizeBytes,
			final Long maxVerificationFileSizeBytes, final Long maxCutoverFileSizeBytes) {
		final var evidenceSnapshot = new MigrationCutoverEvidenceIO()
				.readSnapshot(Objects.requireNonNull(evidenceFile, "evidenceFile"), maxEvidenceFileSizeBytes);
		if (expectedEvidenceFingerprint != null
				&& !expectedEvidenceFingerprint.equals(evidenceSnapshot.fingerprint())) {
			throw new IllegalArgumentException("Migration cutover evidence fingerprint does not match");
		}
		final MigrationCutoverEvidence evidence = evidenceSnapshot.evidence();
		if (evidence.createdAt().isAfter(Instant.now())) {
			throw new IllegalArgumentException("Migration cutover evidence createdAt is in the future");
		}
		final var verificationSnapshot = new BulkMigrationVerificationReportIO().readSnapshot(
				Objects.requireNonNull(verificationReport, "verificationReport"), maxVerificationFileSizeBytes);
		final BulkMigrationVerificationReport verification = verificationSnapshot.report();
		if (!evidence.verificationReportFingerprint().equals(verificationSnapshot.fingerprint())
				|| !evidence.planFingerprint().equals(verification.planFingerprint())
				|| !evidence.verifiedAt().equals(verification.generatedAt()) || !verification.match()) {
			throw new IllegalArgumentException("Migration verification report does not match cutover evidence");
		}
		final var cutoverSnapshot = new MigrationCutoverReportIO()
				.readSnapshot(Objects.requireNonNull(cutoverReport, "cutoverReport"), maxCutoverFileSizeBytes);
		final MigrationCutoverReport cutover = cutoverSnapshot.report();
		if (!evidence.cutoverReportFingerprint().equals(cutoverSnapshot.fingerprint())
				|| !evidence.assessedAt().equals(cutover.assessedAt()) || evidence.status() != cutover.status()) {
			throw new IllegalArgumentException("Migration cutover report does not match cutover evidence");
		}
		return new Inspection(evidence, evidenceSnapshot.fingerprint(), verification, cutover);
	}

	private static void validateFingerprint(final String fingerprint) {
		if (fingerprint == null || !fingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new IllegalArgumentException("expectedEvidenceFingerprint must be a lowercase SHA-256 value");
		}
	}

	private static void validateMaximumAge(final Duration maximumReportAge) {
		if (maximumReportAge == null || maximumReportAge.isZero() || maximumReportAge.isNegative()) {
			throw new IllegalArgumentException("maximumReportAge must be greater than zero");
		}
	}

	private static void validateReadyAndRecent(final MigrationCutoverReport report, final Duration maximumReportAge,
			final Instant now) {
		if (report.assessedAt().isAfter(now)) {
			throw new IllegalArgumentException("Migration cutover report assessedAt is in the future");
		}
		try {
			if (report.assessedAt().plus(maximumReportAge).isBefore(now)) {
				throw new IllegalArgumentException("Migration cutover report has expired");
			}
		} catch (DateTimeException | ArithmeticException e) {
			throw new IllegalArgumentException("maximumReportAge is outside the supported time range", e);
		}
		if (report.status() != MigrationCutoverReport.Status.READY) {
			throw new IllegalStateException("Migration cutover report is not READY: " + report.status());
		}
	}
}
