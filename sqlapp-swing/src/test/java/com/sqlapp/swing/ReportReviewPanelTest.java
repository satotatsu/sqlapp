/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class ReportReviewPanelTest {
	private static final ReportReviewService.Review REVIEW = new ReportReviewService.Review("MATCH",
			List.of("Task", "Outcome"), List.of(List.of("CUSTOMER", "MATCH")), "{\"match\":true}");

	@Test
	void loadsOffTheEdtAndDisplaysReadOnlyResultsThenClearsChangedInputs() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewPanel((file, type) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				return REVIEW;
			});
			panel.set(view);
			start(view, finished);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals("MATCH", view.summary.getText());
			assertEquals("CUSTOMER", view.tasks.getValueAt(0, 0));
			assertFalse(view.tasks.isCellEditable(0, 0));
			assertNotNull(view.tasks.getTableHeader().getParent());
			assertFalse(view.details.isEditable());
			assertEquals(REVIEW.details(), view.details.getText());
			assertTrue(view.status.getText().contains("No live checks"));
			assertFalse(view.isBusy());
			view.file.setText("other.json");
			assertEquals("", view.summary.getText());
			assertEquals(0, view.tasks.getRowCount());
			assertEquals("", view.details.getText());
			view.summary.setText("old");
			view.type.setSelectedItem(ReportReviewService.Type.BULK_FAILURE);
			assertEquals("", view.summary.getText());
		});
	}

	@Test
	void failureClearsPreviousResultsAndRestoresControls() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewPanel((file, type) -> {
				throw new IllegalArgumentException("unsupported format", new java.io.IOException("Bad header"));
			});
			panel.set(view);
			view.details.setText("old report");
			start(view, finished);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(view.summary.getText().contains("unsupported format"));
			assertEquals("", view.details.getText());
			assertEquals(0, view.tasks.getRowCount());
			assertTrue(view.file.isEnabled());
			assertTrue(view.type.isEnabled());
			assertTrue(view.status.getText().startsWith("Load failed"));
			assertFalse(view.isBusy());
			assertTrue(view.statusPanel.detailsText().contains("Bad header"));
			view.file.setText("another-report.json");
			assertEquals(view.status.getText(), view.statusPanel.detailsText());
		});
	}

	@Test
	void discardsInFlightResultWhenInputsHaveChanged() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<ReportReviewPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewPanel((file, type) -> {
				try {
					if (!release.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting for test input change");
					}
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				return REVIEW;
			});
			panel.set(view);
			start(view, finished);
			view.file.setText("changed.json");
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("", panel.get().summary.getText());
			assertEquals("", panel.get().details.getText());
			assertTrue(panel.get().load.isEnabled());
		});
	}

	private void start(final ReportReviewPanel view, final CountDownLatch finished) {
		view.file.setText("report.json");
		view.load.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue())) {
				finished.countDown();
			}
		});
		view.load.doClick();
		assertFalse(view.load.isEnabled());
		assertTrue(view.isBusy());
		assertFalse(view.type.isEnabled());
	}
}
