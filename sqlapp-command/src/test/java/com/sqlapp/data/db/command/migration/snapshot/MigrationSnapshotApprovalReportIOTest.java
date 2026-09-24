/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.snapshot;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class MigrationSnapshotApprovalReportIOTest {
	@TempDir
	Path directory;

	@Test
	void atomicallyRoundTripsAndChecksFingerprint() {
		final var io = new MigrationSnapshotApprovalReportIO();
		final var report = report("sha256:" + "0".repeat(64));
		final Path file = directory.resolve("nested/approval.json");
		io.write(file, report);
		assertEquals(report, io.read(file));
		assertEquals(report, io.read(file, report.configurationFingerprint()));
		assertThrows(CommandException.class, () -> io.read(file, "sha256:" + "f".repeat(64)));
	}

	@Test
	void rejectsInvalidArtifact() {
		final var io = new MigrationSnapshotApprovalReportIO();
		assertThrows(CommandException.class, () -> io.write(directory.resolve("invalid.json"), report("invalid")));
		assertThrows(CommandException.class, () -> io.read(directory.resolve("missing.json")));
	}

	private static MigrationSnapshotApprovalReport report(final String fingerprint) {
		return new MigrationSnapshotApprovalReport(MigrationSnapshotApprovalReport.CURRENT_FORMAT_VERSION,
				Instant.parse("2026-09-16T01:00:00Z"), fingerprint, "CUSTOMER", "PUBLIC.CUSTOMER",
				"PUBLIC.CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"), true, Instant.parse("2026-09-16T00:00:00Z"),
				1000, 500, null);
	}
}
