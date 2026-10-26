/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SavedReportButtonTest {
	@Test
	void opensOnlyRecordedSaveAndRetriesBusyViewerWithoutChangingDestination() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var blocked = new AtomicBoolean();
			final var accept = new AtomicBoolean();
			final var calls = new AtomicInteger();
			final var file = new AtomicReference<Path>();
			final var type = new AtomicReference<ReportReviewService.Type>();
			final var button = new SavedReportButton(blocked::get);
			button.setReceiver((path, format) -> {
				calls.incrementAndGet();
				file.set(path);
				type.set(format);
				return accept.get();
			});
			assertFalse(button.isEnabled());
			button.saved(Path.of("saved-plan.json"), ReportReviewService.Type.SCHEMA_PLAN);
			assertTrue(button.isEnabled());
			button.doClick(0);
			assertEquals(Path.of("saved-plan.json").toAbsolutePath().normalize(), file.get());
			assertEquals(ReportReviewService.Type.SCHEMA_PLAN, type.get());
			assertTrue(button.getToolTipText().contains("viewer is busy"));
			blocked.set(true);
			button.doClick(0);
			assertEquals(1, calls.get());
			button.refresh();
			assertFalse(button.isEnabled());
			blocked.set(false);
			accept.set(true);
			button.refresh();
			button.doClick(0);
			assertEquals(2, calls.get());
			assertTrue(button.getToolTipText().contains("saved-plan.json"));
			assertFalse(button.getToolTipText().contains("viewer is busy"));
			button.saved(Path.of("verification.json"), ReportReviewService.Type.BULK_VERIFICATION);
			button.doClick(0);
			assertEquals(ReportReviewService.Type.BULK_VERIFICATION, type.get());
			button.setReceiver(null);
			assertFalse(button.isEnabled());
		});
	}
}
