/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationIsolation;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReportIO;

class MigrationCutoverArtifactServiceTest {
	@TempDir
	Path directory;

	@Test
	void inspectsAndApprovesTheSameLinkedSnapshots() throws Exception {
		final Instant verifiedAt = Instant.now().minusSeconds(30);
		final var verification = new BulkMigrationVerificationReport(
				BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION, verifiedAt, "plan",
				BulkMigrationVerificationIsolation.DEFAULT.name(), true, 0, 0, 0, List.of(), null);
		final Path verificationFile = directory.resolve(MigrationCutoverArtifactService.VERIFICATION_REPORT_FILE);
		final var verificationSnapshot = new BulkMigrationVerificationReportIO().writeSnapshot(verificationFile,
				verification);
		final var cutover = new MigrationCutoverReport(verifiedAt.plusSeconds(5), MigrationCutoverReport.Status.READY,
				Duration.ofSeconds(5), List.of());
		final Path cutoverFile = directory.resolve(MigrationCutoverArtifactService.CUTOVER_REPORT_FILE);
		final var cutoverSnapshot = new MigrationCutoverReportIO().writeSnapshot(cutoverFile, cutover, null);
		final var evidence = new MigrationCutoverEvidence(MigrationCutoverEvidence.CURRENT_FORMAT_VERSION,
				verifiedAt.plusSeconds(10), "plan", verificationSnapshot.fingerprint(), verifiedAt,
				cutoverSnapshot.fingerprint(), cutover.assessedAt(), cutover.status());
		final Path evidenceFile = directory.resolve(MigrationCutoverArtifactService.EVIDENCE_FILE);
		final var evidenceSnapshot = new MigrationCutoverEvidenceIO().writeSnapshot(evidenceFile, evidence, null);
		final var service = new MigrationCutoverArtifactService();

		final var inspected = service.inspectPackage(directory, 10_000L, 10_000L, 10_000L);
		assertEquals(evidence, inspected.evidence());
		assertEquals(verification, inspected.verification());
		assertEquals(cutover, inspected.cutover());
		assertEquals(inspected, service.approvePackage(directory, evidenceSnapshot.fingerprint(), Duration.ofMinutes(5),
				10_000L, 10_000L, 10_000L));
		assertThrows(IllegalArgumentException.class, () -> service.approvePackage(directory, "sha256:" + "0".repeat(64),
				Duration.ofMinutes(5), null, null, null));
		assertThrows(IllegalArgumentException.class, () -> service.approvePackage(directory,
				evidenceSnapshot.fingerprint(), Duration.ofSeconds(1), null, null, null));

		Files.writeString(directory.resolve("extra.txt"), "extra");
		assertThrows(com.sqlapp.exceptions.CommandException.class,
				() -> service.inspectPackage(directory, null, null, null));
	}
}
