/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportRecentFilesTest {
	@TempDir
	Path directory;

	@Test
	void explicitHistoryRereadsChangedFileAndMissingFileDoesNotShowCachedResults() throws Exception {
		final Path file = Files.writeString(directory.resolve("result.json"), "first");
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> panel.set(new ReportReviewPanel((path, type) -> {
			assertEquals(ReportReviewService.Type.BULK_FAILURE, type);
			try {
				return review(Files.readString(path));
			} catch (java.io.IOException failure) {
				throw new java.io.UncheckedIOException(failure);
			}
		}, path -> {
			throw new AssertionError("Explicit format must not be guessed");
		})));
		load(panel.get(), () -> panel.get().requestOpen(file, ReportReviewService.Type.BULK_FAILURE));
		Files.writeString(file, "second");
		load(panel.get(), () -> panel.get().reopen.doClick(0));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("second", panel.get().summary.getText());
			assertEquals(1, panel.get().recent.getItemCount());
			assertFalse(panel.get().recent.getItemAt(0).automatic());
			assertEquals(file, panel.get().recent.getItemAt(0).file());
		});
		Files.delete(file);
		load(panel.get(), () -> panel.get().reopen.doClick(0));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().status.getText().startsWith("Load failed"));
			assertNotEquals("second", panel.get().summary.getText());
			assertEquals(1, panel.get().recent.getItemCount());
			final String error = panel.get().summary.getText();
			panel.get().clearRecent.doClick(0);
			assertEquals(0, panel.get().recent.getItemCount());
			assertFalse(panel.get().recentRow.isVisible());
			assertEquals(error, panel.get().summary.getText());
		});
	}

	@Test
	void automaticHistoryRedetectsContentsAndSamePathCanSwitchToExplicitMode() throws Exception {
		final var response = new AtomicReference<>(new ReportReviewAutoLoader.Detected(
				ReportReviewService.Type.BULK_VERIFICATION, review("verification")));
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(
				() -> panel.set(new ReportReviewPanel((path, type) -> review("explicit"), path -> response.get())));
		load(panel.get(), () -> {
			panel.get().file.setText(directory.resolve("report.json").toString());
			panel.get().detect.doClick(0);
		});
		response.set(new ReportReviewAutoLoader.Detected(ReportReviewService.Type.BULK_OPERATIONAL, review("status")));
		load(panel.get(), () -> panel.get().reopen.doClick(0));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("status", panel.get().summary.getText());
			assertEquals(ReportReviewService.Type.BULK_OPERATIONAL, panel.get().type.getSelectedItem());
			assertTrue(panel.get().recent.getItemAt(0).automatic());
			assertEquals(1, panel.get().recent.getItemCount());
		});
		load(panel.get(), () -> panel.get().requestOpen(directory.resolve("report.json"),
				ReportReviewService.Type.SCHEMA_EXECUTION));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(1, panel.get().recent.getItemCount());
			assertFalse(panel.get().recent.getItemAt(0).automatic());
			assertEquals(ReportReviewService.Type.SCHEMA_EXECUTION, panel.get().recent.getItemAt(0).type());
		});
	}

	@Test
	void historyIsBoundedDeduplicatedAndSelectionAloneDoesNotLoad() throws Exception {
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(
				() -> panel.set(new ReportReviewPanel((path, type) -> review(path.getFileName().toString()))));
		for (int index = 0; index < 12; index++) {
			final Path file = directory.resolve(index + ".json");
			load(panel.get(), () -> panel.get().requestOpen(file, ReportReviewService.Type.SCHEMA_PLAN));
		}
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(10, panel.get().recent.getItemCount());
			assertEquals(directory.resolve("11.json"), panel.get().recent.getItemAt(0).file());
			assertEquals(directory.resolve("2.json"), panel.get().recent.getItemAt(9).file());
			panel.get().recent.setSelectedIndex(9);
			assertEquals("11.json", panel.get().summary.getText());
			assertFalse(panel.get().isBusy());
		});
		load(panel.get(), () -> panel.get().reopen.doClick(0));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("2.json", panel.get().summary.getText());
			assertEquals(10, panel.get().recent.getItemCount());
			assertEquals(directory.resolve("2.json"), panel.get().recent.getItemAt(0).file());
			final var other = new ReportReviewPanel();
			assertEquals(0, other.recent.getItemCount());
			assertFalse(other.recentRow.isVisible());
		});
	}

	@Test
	void failedAndStaleReadsNeverEnterHistoryAndHistoryCannotChangeWhileBusy() throws Exception {
		final var release = new CountDownLatch(1);
		final var done = new CountDownLatch(1);
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> panel.set(new ReportReviewPanel((path, type) -> {
			if (path.getFileName().toString().equals("failed.json"))
				throw new IllegalArgumentException("Invalid report");
			if (path.getFileName().toString().equals("stale.json")) {
				try {
					assertTrue(release.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException failure) {
					throw new RuntimeException(failure);
				}
			}
			return review("loaded");
		})));
		load(panel.get(),
				() -> panel.get().requestOpen(directory.resolve("failed.json"), ReportReviewService.Type.SCHEMA_PLAN));
		SwingUtilities.invokeAndWait(() -> assertEquals(0, panel.get().recent.getItemCount()));
		load(panel.get(),
				() -> panel.get().requestOpen(directory.resolve("good.json"), ReportReviewService.Type.SCHEMA_PLAN));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			view.load.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			view.requestOpen(directory.resolve("stale.json"), ReportReviewService.Type.SCHEMA_PLAN);
			assertFalse(view.reopen.isEnabled());
			assertFalse(view.clearRecent.isEnabled());
			view.clearRecentReports();
			view.openRecent();
			assertEquals(1, view.recent.getItemCount());
			view.file.setText("newer.json");
		});
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(1, panel.get().recent.getItemCount());
			assertEquals(directory.resolve("good.json"), panel.get().recent.getItemAt(0).file());
			assertEquals("", panel.get().summary.getText());
		});
	}

	private static ReportReviewService.Review review(String text) {
		return new ReportReviewService.Review(text, List.of(), List.of(), "{}");
	}

	private static void load(ReportReviewPanel panel, Runnable action) throws Exception {
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			panel.load.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			action.run();
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
	}
}
