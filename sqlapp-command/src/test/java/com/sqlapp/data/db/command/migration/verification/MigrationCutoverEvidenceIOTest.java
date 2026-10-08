/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class MigrationCutoverEvidenceIOTest {
	@TempDir
	Path directory;

	@Test
	void roundTripsOneBoundedSnapshotAndRejectsInvalidLinkage() throws Exception {
		final Instant verifiedAt = Instant.parse("2026-10-07T00:00:00Z");
		final Instant assessedAt = verifiedAt.plusSeconds(30);
		final var evidence = new MigrationCutoverEvidence(MigrationCutoverEvidence.CURRENT_FORMAT_VERSION,
				assessedAt.plusSeconds(1), "plan", "sha256:" + "1".repeat(64), verifiedAt, "sha256:" + "2".repeat(64),
				assessedAt, MigrationCutoverReport.Status.READY);
		final Path file = directory.resolve("nested/evidence.json");
		final var snapshot = new MigrationCutoverEvidenceIO().writeSnapshot(file, evidence, 10_000L);

		assertEquals(evidence, snapshot.evidence());
		assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(file.toFile()),
				snapshot.fingerprint());
		assertEquals(evidence, new MigrationCutoverEvidenceIO().read(file, 10_000L));
		assertThrows(CommandException.class, () -> new MigrationCutoverEvidenceIO().read(file, 1L));

		Files.writeString(file, "{}\n");
		assertThrows(CommandException.class, () -> new MigrationCutoverEvidenceIO().read(file));
		assertThrows(CommandException.class,
				() -> new MigrationCutoverEvidenceIO().write(file,
						new MigrationCutoverEvidence(1, verifiedAt, "plan", "sha256:" + "1".repeat(64), verifiedAt,
								"sha256:" + "2".repeat(64), assessedAt, MigrationCutoverReport.Status.READY)));
	}
}
