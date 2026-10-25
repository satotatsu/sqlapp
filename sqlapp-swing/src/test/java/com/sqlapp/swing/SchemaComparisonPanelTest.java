/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class SchemaComparisonPanelTest {
	@Test
	void changingEitherInputInvalidatesThePreviousReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaComparisonPanel();
			assertFalse(panel.save.isEnabled());
			for (final var input : new javax.swing.JTextField[] { panel.original, panel.target }) {
				panel.preview.setText("old SQL");
				panel.save.setEnabled(true);
				input.setText("changed.xml");
				assertEquals("", panel.preview.getText());
				assertFalse(panel.save.isEnabled());
			}
		});
	}

	@Test
	void comparesAndSavesOffEdtAndRetainsDraftAfterSaveFailure() throws Exception {
		final var ref = new java.util.concurrent.atomic.AtomicReference<SchemaComparisonPanel>();
		final var ready = new java.util.concurrent.CountDownLatch(1);
		final var failed = new java.util.concurrent.CountDownLatch(1);
		final var saved = new java.util.concurrent.CountDownLatch(1);
		final var snapshot = new SchemaComparisonService.Result(java.nio.file.Path.of("before.xml"),
				java.nio.file.Path.of("after.xml"), "review", "CREATE TABLE T(ID INT);", 1);
		final var calls = new java.util.concurrent.atomic.AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaComparisonPanel((before, after) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				return snapshot;
			}, (result, destination, sqlDraft) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertSame(snapshot, result);
				assertTrue(sqlDraft);
				if (calls.getAndIncrement() == 0)
					throw new java.io.IOException("disk full");
			});
			ref.set(panel);
			panel.original.setText("before.xml");
			panel.target.setText("after.xml");
			onEnabled(panel.compare, ready);
			panel.compare.doClick();
		});
		assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertEquals(snapshot.sqlDraft(), panel.sqlPreview.getText());
			assertTrue(panel.saveSql.isEnabled());
			onEnabled(panel.compare, failed);
			panel.saveTo(java.nio.file.Path.of("draft.sql"), true);
			assertFalse(panel.saveSql.isEnabled());
		});
		assertTrue(failed.await(5, java.util.concurrent.TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertTrue(panel.status.getText().contains("disk full"));
			assertEquals(snapshot.sqlDraft(), panel.sqlPreview.getText());
			onEnabled(panel.compare, saved);
			panel.saveTo(java.nio.file.Path.of("retry.sql"), true);
		});
		assertTrue(saved.await(5, java.util.concurrent.TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertTrue(panel.status.getText().contains("Saved SQL draft"));
			panel.target.setText("new.xml");
			assertFalse(panel.saveSql.isEnabled());
			assertTrue(panel.sqlPreview.getText().isEmpty());
		});
	}

	@Test
	void discardsComparisonCompletedAfterInputsChanged() throws Exception {
		final var ref = new java.util.concurrent.atomic.AtomicReference<SchemaComparisonPanel>();
		final var release = new java.util.concurrent.CountDownLatch(1);
		final var ready = new java.util.concurrent.CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaComparisonPanel((before, after) -> {
				if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
					throw new IllegalStateException("timeout");
				return new SchemaComparisonService.Result(before, after, "stale", "stale SQL", 1);
			}, (result, destination, sqlDraft) -> fail("No stale export"));
			ref.set(panel);
			panel.original.setText("before.xml");
			panel.target.setText("after.xml");
			onEnabled(panel.compare, ready);
			panel.compare.doClick();
			panel.target.setText("changed.xml");
		});
		release.countDown();
		assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(ref.get().preview.getText().isEmpty());
			assertTrue(ref.get().sqlPreview.getText().isEmpty());
			assertFalse(ref.get().save.isEnabled());
			assertFalse(ref.get().saveSql.isEnabled());
		});
	}

	@Test
	void noChangesAllowsReviewSavingButDisablesSqlExport() throws Exception {
		final var ref = new java.util.concurrent.atomic.AtomicReference<SchemaComparisonPanel>();
		final var ready = new java.util.concurrent.CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaComparisonPanel((before, after) -> new SchemaComparisonService.Result(before,
					after, "No changes", "-- No changes", 0), (result, destination, sqlDraft) -> {
					});
			ref.set(panel);
			panel.original.setText("same.xml");
			panel.target.setText("same.xml");
			onEnabled(panel.compare, ready);
			panel.compare.doClick();
		});
		assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(ref.get().save.isEnabled());
			assertFalse(ref.get().saveSql.isEnabled());
		});
	}

	private static void onEnabled(final javax.swing.JButton button, final java.util.concurrent.CountDownLatch latch) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				latch.countDown();
		});
	}

}
