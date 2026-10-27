/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;

class JobDraftFileTransferTest {
	@Test
	void pickerCapturesOptionsOnEventThreadWritesInBackgroundAndReturnsSavedPath() throws Exception {
		final var saved = new AtomicReference<Path>();
		final var finished = new CountDownLatch(1);
		final var schema = new BulkMigrationJobDraftBuilder.SchemaSnapshot(Path.of("source-schema.xml"),
				"sha256:" + "a".repeat(64),
				List.of(new BulkMigrationJobDraftBuilder.TableChoice("PUBLIC.ITEMS", List.of("ID"), null)));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = JobDraftPanel.fileTransfer(schema, () -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				return (reviewed, tables, chunk, verify) -> {
					assertFalse(SwingUtilities.isEventDispatchThread());
					assertSame(schema, reviewed);
					assertEquals(List.of("PUBLIC.ITEMS"), tables);
					assertTrue(verify);
					assertTrue(chunk > 0);
					return "transfer.yaml";
				};
			}, path -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				saved.set(path);
				finished.countDown();
			}, () -> fail("Unexpected cancellation"));
			assertFalse(panel.schemaFile.getParent().isVisible());
			assertFalse(panel.jobId.isVisible());
			assertFalse(panel.create.isEnabled());
			panel.select.doClick(0);
			assertTrue(panel.create.isEnabled());
			panel.create.doClick(0);
			assertTrue(panel.isBusy());
			assertFalse(panel.cancel.isEnabled());
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(Path.of("transfer.yaml"), saved.get());
	}
}
