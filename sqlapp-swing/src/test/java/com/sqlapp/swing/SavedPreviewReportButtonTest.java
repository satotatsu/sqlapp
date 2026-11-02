/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO.Saved;

class SavedPreviewReportButtonTest {
	private Saved saved(String name) {
		return new Saved(Path.of(name).toAbsolutePath(), "sha256:" + "a".repeat(64), 1, 1);
	}

	private void idle(SavedPreviewReportButton button) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		var opening = new AtomicBoolean(true);
		while (opening.get() && System.nanoTime() < deadline) {
			SwingUtilities.invokeAndWait(() -> opening.set(button.isOpening()));
			if (opening.get())
				Thread.sleep(10);
		}
		assertFalse(opening.get(), "Saved-preview opening should complete");
	}

	@Test
	void onlyExplicitSavedActionVerifiesOffEdtAndOpensOnEdt() throws Exception {
		var blocked = new AtomicBoolean();
		var verified = new AtomicInteger();
		var opened = new AtomicInteger();
		var status = new AtomicReference<String>();
		var activity = new AtomicInteger();
		var holder = new SavedPreviewReportButton[1];
		var snapshot = saved("preview.html");
		SwingUtilities.invokeAndWait(() -> {
			var button = new SavedPreviewReportButton(blocked::get, status::set, report -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				verified.incrementAndGet();
				return report.file();
			}, file -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				assertEquals(snapshot.file(), file);
				opened.incrementAndGet();
			});
			holder[0] = button;
			button.setActivityObserver(activity::incrementAndGet);
			assertFalse(button.isEnabled());
			button.saved(snapshot);
			assertTrue(button.isEnabled());
			assertEquals(0, opened.get());
			blocked.set(true);
			button.refresh();
			assertFalse(button.isEnabled());
			button.setEnabled(true);
			button.doClick(0);
			assertEquals(0, verified.get());
			blocked.set(false);
			button.refresh();
			button.doClick(0);
			assertTrue(button.isOpening());
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(1, verified.get());
			assertEquals(1, opened.get());
			assertEquals(2, activity.get());
			assertTrue(holder[0].isEnabled());
			assertTrue(status.get().startsWith("Opened saved preview"));
		});
	}

	@Test
	void verificationFailuresDoNotOpenAFileAndCanBeRetried() throws Exception {
		var attempts = new AtomicInteger();
		var opened = new AtomicInteger();
		var status = new AtomicReference<String>();
		var holder = new SavedPreviewReportButton[1];
		SwingUtilities.invokeAndWait(() -> {
			var button = new SavedPreviewReportButton(() -> false, status::set, report -> {
				if (attempts.incrementAndGet() == 1)
					throw new java.io.IOException("Saved preview report changed after saving");
				return report.file();
			}, file -> opened.incrementAndGet());
			holder[0] = button;
			button.saved(saved("preview.html"));
			button.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(0, opened.get());
			assertTrue(status.get().contains("changed after saving"));
			assertTrue(holder[0].isEnabled());
			holder[0].doClick(0);
		});
		idle(holder[0]);
		assertEquals(1, opened.get());
		assertEquals(2, attempts.get());
	}

	@Test
	void lateVerificationCannotOpenAnOlderSavedSelectionOrBypassAnotherAction() throws Exception {
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var opened = new AtomicInteger();
		var blocked = new AtomicBoolean();
		var status = new AtomicReference<String>();
		var holder = new SavedPreviewReportButton[1];
		SwingUtilities.invokeAndWait(() -> {
			var button = new SavedPreviewReportButton(blocked::get, status::set, report -> {
				started.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return report.file();
			}, file -> opened.incrementAndGet());
			holder[0] = button;
			button.saved(saved("old.html"));
			button.doClick(0);
		});
		assertTrue(started.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> holder[0].saved(saved("new.html")));
		release.countDown();
		idle(holder[0]);
		assertEquals(0, opened.get());
		assertTrue(status.get().contains("last saved preview changed"));
		SwingUtilities.invokeAndWait(() -> {
			holder[0].doClick(0);
			blocked.set(true);
		});
		idle(holder[0]);
		assertEquals(0, opened.get());
		assertTrue(status.get().contains("Another action"));
	}

	@Test
	void browserFailureKeepsTheSavedPathAndReleasesBusyControls() throws Exception {
		var status = new AtomicReference<String>();
		var holder = new SavedPreviewReportButton[1];
		SwingUtilities.invokeAndWait(() -> {
			var button = new SavedPreviewReportButton(() -> false, status::set, Saved::file, file -> {
				throw new java.io.IOException("Open the saved HTML file manually: " + file);
			});
			holder[0] = button;
			button.saved(saved("manual.html"));
			button.doClick(0);
		});
		idle(holder[0]);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(status.get().contains("manually"));
			assertTrue(status.get().contains("manual.html"));
			assertTrue(holder[0].isEnabled());
			assertTrue(holder[0].getToolTipText().contains("manual.html"));
		});
	}
}
