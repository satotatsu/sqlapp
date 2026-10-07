/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Atomically persists the fingerprints linking verification to cutover. */
public final class MigrationCutoverEvidenceIO {
	public record Snapshot(MigrationCutoverEvidence evidence, String fingerprint) {
	}

	public MigrationCutoverEvidence read(final Path file) {
		return readSnapshot(file, null).evidence();
	}

	public MigrationCutoverEvidence read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).evidence();
	}

	public Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Migration cutover evidence does not exist: " + absolute);
		}
		try {
			final byte[] bytes = BoundedMigrationFile.read(absolute, maxFileSizeBytes,
					"maxCutoverEvidenceFileSizeBytes", "Migration cutover evidence");
			final var evidence = validate(new JsonConverter().fromJsonString(
					new String(bytes, StandardCharsets.UTF_8), MigrationCutoverEvidence.class));
			return new Snapshot(evidence,
					"sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
		} catch (IOException e) {
			throw new CommandException("Failed to read migration cutover evidence: " + absolute, e);
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read migration cutover evidence: " + absolute, e);
		}
	}

	public void write(final Path file, final MigrationCutoverEvidence evidence) {
		writeSnapshot(file, evidence, null);
	}

	public Snapshot writeSnapshot(final Path file, final MigrationCutoverEvidence evidence,
			final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		final MigrationCutoverEvidence validated = validate(evidence);
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), validated));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write migration cutover evidence: " + absolute, e);
		}
		final Snapshot snapshot = readSnapshot(absolute, maxFileSizeBytes);
		if (!validated.equals(snapshot.evidence())) {
			throw new CommandException("Written migration cutover evidence does not match the requested evidence");
		}
		return snapshot;
	}

	private static MigrationCutoverEvidence validate(final MigrationCutoverEvidence evidence) {
		if (evidence == null || evidence.formatVersion() != MigrationCutoverEvidence.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported migration cutover evidence format");
		}
		if (evidence.createdAt() == null || evidence.verifiedAt() == null || evidence.assessedAt() == null
				|| evidence.status() == null || evidence.planFingerprint() == null
				|| evidence.planFingerprint().isBlank()) {
			throw new CommandException("Migration cutover evidence header is invalid");
		}
		validateFingerprint(evidence.verificationReportFingerprint(), "verificationReportFingerprint");
		validateFingerprint(evidence.cutoverReportFingerprint(), "cutoverReportFingerprint");
		if (evidence.createdAt().isBefore(evidence.assessedAt())
				|| evidence.assessedAt().isBefore(evidence.verifiedAt())) {
			throw new CommandException("Migration cutover evidence timestamps are inconsistent");
		}
		return evidence;
	}

	private static void validateFingerprint(final String fingerprint, final String name) {
		if (fingerprint == null || !fingerprint.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException("Migration cutover evidence " + name + " is invalid");
		}
	}
}
