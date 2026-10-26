/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaCapturePanelTest {
	@TempDir
	Path directory;

	@Test
	void capturesOffEdtClearsSecretsAndReturnsOnlySavedXml() throws Exception {
		final var panel = new AtomicReference<SchemaCapturePanel>();
		final var secret = new AtomicReference<char[]>();
		final var selected = new AtomicReference<Path>();
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new SchemaCapturePanel(directory, selected::set, () -> {
			}, (spec, password, schema, folder) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertEquals("jdbc:source", spec.jdbcUrl());
				assertEquals("REPORTS", schema);
				assertEquals(directory, folder);
				assertArrayEquals("secret".toCharArray(), password);
				secret.set(password);
				return directory.resolve("schema.xml");
			}, (spec, password) -> {
				throw new AssertionError("Capture must not run a separate connection test");
			});
			panel.set(view);
			configure(view);
			onFinished(view, done);
			view.capture.doClick(0);
			assertTrue(view.isBusy());
			assertFalse(view.cancel.isEnabled());
			assertFalse(view.connection.jdbcUrl.isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[6], secret.get());
		assertEquals(directory.resolve("schema.xml"), selected.get());
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(0, panel.get().connection.password.getPassword().length);
			assertFalse(panel.get().isBusy());
		});
	}

	@Test
	void testConnectionKeepsPasswordForCaptureAndCancelClearsIt() throws Exception {
		final var panel = new AtomicReference<SchemaCapturePanel>();
		final var closed = new AtomicInteger();
		final var done = new CountDownLatch(1);
		final var secret = new AtomicReference<char[]>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new SchemaCapturePanel(directory, path -> fail("Test must not return XML"),
					closed::incrementAndGet, (spec, password, schema, folder) -> {
						throw new AssertionError("Test must not capture");
					}, (spec, password) -> {
						secret.set(password);
						return "Example DB";
					});
			panel.set(view);
			configure(view);
			onFinished(view, done);
			view.connection.test.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[6], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().status.getText().contains("Connection succeeded"));
			assertArrayEquals("secret".toCharArray(), panel.get().connection.password.getPassword());
			panel.get().cancel.doClick(0);
			assertEquals(1, closed.get());
			assertEquals(0, panel.get().connection.password.getPassword().length);
		});
	}

	@Test
	void staleCaptureAndDuplicateActionsCannotReplaceInputs() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var done = new CountDownLatch(1);
		final var panel = new AtomicReference<SchemaCapturePanel>();
		final var calls = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new SchemaCapturePanel(directory, path -> fail("Stale capture must not select XML"),
					() -> {
					}, (spec, password, schema, folder) -> {
						calls.incrementAndGet();
						entered.countDown();
						try {
							assertTrue(release.await(5, TimeUnit.SECONDS));
						} catch (InterruptedException failure) {
							throw new RuntimeException(failure);
						}
						return directory.resolve("schema.xml");
					}, (spec, password) -> {
						throw new AssertionError("Busy panel must not test");
					});
			panel.set(view);
			configure(view);
			onFinished(view, done);
			view.capture.doClick(0);
			view.run(SchemaCapturePanel.Action.TEST);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> panel.get().schema.setText("OTHER"));
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.get().status.getText().contains("Captured XML:")));
	}

	@Test
	void comparisonCaptureRetainsOldReviewOnCancelAndSelectsOnlyRequestedSide() throws Exception {
		final var count = new AtomicInteger();
		final var panel = new AtomicReference<SchemaComparisonPanel>();
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new SchemaComparisonPanel((before, after) -> {
				count.incrementAndGet();
				return new SchemaComparisonService.Result(before, after, "Previous review", "SQL", 1);
			}, (result, path, sql) -> {
			});
			panel.set(view);
			view.original.setText("before.xml");
			view.target.setText("after.xml");
			view.compare.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			view.compare.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			view.captureInto(true, () -> {
				assertTrue(view.isBusy());
				assertFalse(view.beforeDatabase.isEnabled());
				return null;
			});
			assertEquals("Previous review", view.preview.getText());
			assertTrue(view.save.isEnabled());
			view.captureInto(true, () -> {
				throw new IllegalArgumentException("Capture failed");
			});
			assertEquals("Previous review", view.preview.getText());
			assertTrue(view.save.isEnabled());
			view.captureInto(true, () -> directory.resolve("captured.xml"));
			assertEquals(directory.resolve("captured.xml").toString(), view.original.getText());
			assertEquals("after.xml", view.target.getText());
			assertEquals("", view.preview.getText());
			assertFalse(view.save.isEnabled());
			assertEquals(1, count.get());
			view.captureInto(false, () -> {
				view.original.setText("newer.xml");
				return directory.resolve("stale.xml");
			});
			assertEquals("after.xml", view.target.getText());
			assertTrue(view.status.getText().contains("Inputs changed"));
		});
	}

	private static void configure(SchemaCapturePanel panel) {
		panel.connection.jdbcUrl.setText("jdbc:source");
		panel.connection.password.setText("secret");
		panel.schema.setText("REPORTS");
	}

	private static void onFinished(SchemaCapturePanel panel, CountDownLatch done) {
		panel.capture.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}
}
