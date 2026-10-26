/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ReportAutoLoadPanelTest {
	private static final ReportReviewService.Review REVIEW = new ReportReviewService.Review("MATCH", List.of(),
			List.of(), "{}");

	@Test
	void automaticLoadSelectsDetectedTypeWithoutClearingReview() throws Exception {
		final var done = new CountDownLatch(1);
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewPanel((file, type) -> {
				throw new AssertionError("Must use automatic loader");
			}, file -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertEquals(Path.of("report.json"), file);
				return new ReportReviewAutoLoader.Detected(ReportReviewService.Type.BULK_VERIFICATION, REVIEW);
			});
			panel.set(view);
			view.file.setText("report.json");
			onFinished(view, done);
			view.detect.doClick(0);
			assertTrue(view.isBusy());
			assertFalse(view.chooseFormat.isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals("MATCH", view.summary.getText());
			assertEquals(ReportReviewService.Type.BULK_VERIFICATION, view.type.getSelectedItem());
			assertTrue(view.status.getText().startsWith("Detected "));
			assertTrue(view.statusPanel.detailsText().contains("No live checks"));
			assertFalse(view.isBusy());
		});
	}

	@Test
	void changedInputCannotPublishDetectionAndKnownHandoffIsRejectedWhileBusy() throws Exception {
		final var release = new CountDownLatch(1);
		final var done = new CountDownLatch(1);
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewPanel((file, type) -> {
				throw new AssertionError("Must not load explicit type");
			}, file -> {
				try {
					assertTrue(release.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException failure) {
					throw new RuntimeException(failure);
				}
				return new ReportReviewAutoLoader.Detected(ReportReviewService.Type.BULK_FAILURE, REVIEW);
			});
			panel.set(view);
			view.file.setText("old.json");
			onFinished(view, done);
			view.detect.doClick(0);
			assertFalse(view.requestOpen(Path.of("handoff.json"), ReportReviewService.Type.BULK_VERIFICATION));
			view.file.setText("new.json");
		});
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("", panel.get().summary.getText());
			assertEquals(ReportReviewService.Type.SCHEMA_PLAN, panel.get().type.getSelectedItem());
		});
	}

	@Test
	void failedDetectionCanBeRetriedWithExplicitFormat() throws Exception {
		final var done = new CountDownLatch(1);
		final var retried = new CountDownLatch(1);
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewPanel((file, type) -> {
				assertEquals(ReportReviewService.Type.BULK_VERIFICATION, type);
				return REVIEW;
			}, file -> {
				throw new IllegalArgumentException("Ambiguous report format. Use Choose format.");
			});
			panel.set(view);
			view.file.setText("report.json");
			onFinished(view, done);
			view.detect.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(view.statusPanel.detailsText().contains("Automatic detection"));
			assertTrue(view.statusPanel.detailsText().contains("Ambiguous"));
			view.chooseFormat.doClick(0);
			view.type.setSelectedItem(ReportReviewService.Type.BULK_VERIFICATION);
			onFinished(view, retried);
			view.load.doClick(0);
		});
		assertTrue(retried.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> assertEquals("MATCH", panel.get().summary.getText()));
	}

	@Test
	void knownReportHandoffKeepsExplicitTypeInsteadOfGuessing() throws Exception {
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewPanel((file, type) -> {
				assertEquals(ReportReviewService.Type.BULK_FAILURE, type);
				return REVIEW;
			}, file -> {
				throw new AssertionError("Known report type must not be inferred");
			});
			onFinished(view, done);
			assertTrue(view.requestOpen(Path.of("known.json"), ReportReviewService.Type.BULK_FAILURE));
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
	}

	private static void onFinished(ReportReviewPanel panel, CountDownLatch done) {
		panel.load.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}
}
