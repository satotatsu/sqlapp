/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;

class FileBulkMigrationCheckpointStoreTest {
	@TempDir
	java.nio.file.Path directory;

	@Test
	void savesReplacesLoadsAndDeletesCheckpoint() throws Exception {
		final var store = new FileBulkMigrationCheckpointStore(directory);
		final var first = BulkMigrationCheckpoint.builder().migrationId("schema/table 日本語")
				.sourceFingerprint("source-1").targetFingerprint("target-1")
				.processedRows(20).completedChunks(2).lastChunkHash("abc").build();
		store.save(first);
		assertEquals(first, store.load(first.getMigrationId()).orElseThrow());

		final var complete = first.toBuilder().processedRows(30).completedChunks(3)
				.lastChunkHash("def").complete(true).build();
		store.save(complete);
		assertEquals(complete, store.load(first.getMigrationId()).orElseThrow());
		try (var files = Files.list(directory)) {
			assertEquals(1, files.count());
		}

		store.delete(first.getMigrationId());
		assertFalse(store.load(first.getMigrationId()).isPresent());
		try (var files = Files.list(directory)) {
			assertTrue(files.findAny().isEmpty());
		}
	}
}
