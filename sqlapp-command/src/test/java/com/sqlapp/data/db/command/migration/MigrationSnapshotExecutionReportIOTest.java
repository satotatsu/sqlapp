/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;

class MigrationSnapshotExecutionReportIOTest {
	@TempDir Path directory;

	@Test
	void atomicallyRoundTripsAValidatedReport() {
		final var report = report(MigrationSnapshotExecutionReport.CURRENT_FORMAT_VERSION);
		final Path file = directory.resolve("nested/snapshot.json");

		new MigrationSnapshotExecutionReportIO().write(file, report);

		assertEquals(report, new MigrationSnapshotExecutionReportIO().read(file));
	}

	@Test
	void rejectsUnsupportedAndCorruptReports() throws Exception {
		final var io = new MigrationSnapshotExecutionReportIO();
		assertThrows(CommandException.class, () -> io.write(directory.resolve("unsupported.json"), report(2)));
		final Path corrupt = directory.resolve("corrupt.json");
		Files.writeString(corrupt, "{\"formatVersion\":1}");
		assertThrows(CommandException.class, () -> io.read(corrupt));
	}

	private static MigrationSnapshotExecutionReport report(final int version) {
		return new MigrationSnapshotExecutionReport(version, Instant.parse("2026-09-16T01:00:00Z"), "customer",
				"PUBLIC.CUSTOMER", "PUBLIC.CUSTOMER_HISTORY", List.of("ID"), List.of("NAME"), true,
				Instant.parse("2026-09-16T00:00:00Z"), 1000, 500, "HSQL Database Engine", "2.7",
				"example.Executor", true, 2, 2, 1);
	}
}
