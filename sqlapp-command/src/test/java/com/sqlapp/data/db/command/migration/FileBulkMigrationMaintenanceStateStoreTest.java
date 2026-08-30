/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;

class FileBulkMigrationMaintenanceStateStoreTest {
	@TempDir
	java.nio.file.Path directory;

	@Test
	void savesReplacesLoadsAndDeletesState() throws Exception {
		final var store = new FileBulkMigrationMaintenanceStateStore(directory);
		final String fingerprint = "plan-日本語-v1";
		final var prepared = new BulkMigrationMaintenanceState(fingerprint,
				BulkMigrationMaintenanceStatus.PREPARED,
				Instant.parse("2026-08-31T01:02:03Z"), null);
		store.save(prepared);
		assertEquals(prepared, store.load(fingerprint).orElseThrow());

		final var failed = new BulkMigrationMaintenanceState(fingerprint,
				BulkMigrationMaintenanceStatus.RESTORE_FAILED,
				Instant.parse("2026-08-31T01:03:00Z"), "enable constraint failed");
		store.save(failed);
		assertEquals(failed, store.load(fingerprint).orElseThrow());
		try (var files = Files.list(directory)) {
			assertEquals(1, files.count());
		}

		store.delete(fingerprint);
		assertFalse(store.load(fingerprint).isPresent());
	}

	@Test
	void rejectsCorruptState() throws Exception {
		final var store = new FileBulkMigrationMaintenanceStateStore(directory);
		final String fingerprint = "corrupt-plan";
		store.save(new BulkMigrationMaintenanceState(fingerprint,
				BulkMigrationMaintenanceStatus.PREPARING, Instant.now(), null));
		final java.nio.file.Path file;
		try (var files = Files.list(directory)) {
			file = files.findFirst().orElseThrow();
		}
		Files.writeString(file, "planFingerprint=other\nstatus=UNKNOWN\nupdatedAt=bad\n");

		assertThrows(SQLException.class, () -> store.load(fingerprint));
	}
}
