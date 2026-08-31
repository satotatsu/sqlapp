/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class BulkMigrationCancellationListenerTest {
	@Test
	void pausesOnlyAfterCancellationAndPreservesFirstReason() {
		final var token = new BulkMigrationCancellationToken();
		final var reasons = new ArrayList<String>();
		final var listener = new BulkMigrationCancellationListener(token, reasons::add);
		final var progress = new ChunkedBulkMigrationProgress("migration", 0, 10, 0, 10);

		assertFalse(listener.pauseAfterChunk(progress));
		assertNull(token.getReason());
		assertTrue(token.requestCancellation("operator shutdown"));
		assertFalse(token.requestCancellation("second request"));
		assertTrue(listener.pauseAfterChunk(progress));

		assertEquals("operator shutdown", token.getReason());
		assertEquals(java.util.List.of("operator shutdown"), reasons);
	}

	@Test
	void rejectsInvalidReasonAndNullProgress() {
		final var token = new BulkMigrationCancellationToken();
		assertThrows(IllegalArgumentException.class,
				() -> token.requestCancellation(" "));
		assertThrows(NullPointerException.class,
				() -> new BulkMigrationCancellationListener(token).pauseAfterChunk(null));
	}
}
