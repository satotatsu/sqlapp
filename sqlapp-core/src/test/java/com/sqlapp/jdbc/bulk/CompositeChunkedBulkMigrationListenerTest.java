/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CompositeChunkedBulkMigrationListenerTest {
	@Test
	void dispatchesInOrderAndAsksEveryListenerBeforePausing() {
		final List<String> events = new ArrayList<>();
		final var first = listener("first", true, events);
		final var second = listener("second", false, events);
		final var composite = CompositeChunkedBulkMigrationListener.of(first, second);
		final var progress = new ChunkedBulkMigrationProgress("migration", 0, 1, 0, 1);

		composite.onChunkStarted(progress);
		composite.onChunkCompleted(progress);
		assertTrue(composite.pauseAfterChunk(progress));

		assertEquals(List.of("start-first", "start-second", "complete-first",
				"complete-second", "pause-first", "pause-second"), events);
	}

	private static ChunkedBulkMigrationListener listener(final String name,
			final boolean pause, final List<String> events) {
		return new ChunkedBulkMigrationListener() {
			@Override
			public void onChunkStarted(ChunkedBulkMigrationProgress progress) {
				events.add("start-" + name);
			}

			@Override
			public void onChunkCompleted(ChunkedBulkMigrationProgress progress) {
				events.add("complete-" + name);
			}

			@Override
			public boolean pauseAfterChunk(ChunkedBulkMigrationProgress progress) {
				events.add("pause-" + name);
				return pause;
			}
		};
	}
}
