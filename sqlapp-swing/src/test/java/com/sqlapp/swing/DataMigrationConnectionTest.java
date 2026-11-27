/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class DataMigrationConnectionTest {
	@Test
	void testsOnlySelectedConnectionWithoutJobAndKeepsCredentialsForNextAction() throws Exception {
		for (boolean source : new boolean[] { true, false }) {
			final var done = new CountDownLatch(1);
			final var copied = new AtomicReference<char[]>();
			final var panel = new AtomicReference<DataMigrationPlanPanel>();
			SwingUtilities.invokeAndWait(() -> {
				final var view = panel((connection, password) -> {
					assertFalse(SwingUtilities.isEventDispatchThread());
					assertEquals(source ? "jdbc:source" : "jdbc:target", connection.jdbcUrl());
					assertEquals("user", connection.username());
					assertEquals("driver.Class", connection.driverClass());
					assertArrayEquals("secret".toCharArray(), password);
					copied.set(password);
					return "ExampleDB 1.0";
				});
				panel.set(view);
				final var fields = source ? view.source : view.target;
				fields.jdbcUrl.setText(source ? "jdbc:source" : "jdbc:target");
				fields.username.setText("user");
				fields.driverClass.setText("driver.Class");
				fields.password.setText("secret");
				view.view.summary.setText("Previous review");
				onFinished(view, done);
				fields.test.doClick(0);
				assertTrue(view.isBusy());
				assertFalse(view.newMigration.isEnabled());
				assertFalse(view.source.test.isEnabled());
				assertFalse(view.target.test.isEnabled());
			});
			assertTrue(done.await(5, TimeUnit.SECONDS));
			assertArrayEquals(new char[6], copied.get());
			SwingUtilities.invokeAndWait(() -> {
				final var view = panel.get();
				final var fields = source ? view.source : view.target;
				assertTrue(view.status.getText()
						.startsWith(source ? "Source connection succeeded" : "Target connection succeeded"));
				assertTrue(view.statusPanel.detailsText().contains("does not approve execution"));
				assertEquals("Previous review", view.view.summary.getText());
				assertArrayEquals("secret".toCharArray(), fields.password.getPassword());
				assertFalse(view.apply.isEnabled());
				assertFalse(view.isBusy());
			});
		}
	}

	@Test
	void rejectsDuplicateChecksAndDiscardsStaleResult() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var done = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				calls.incrementAndGet();
				entered.countDown();
				try {
					assertTrue(release.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException failure) {
					throw new RuntimeException(failure);
				}
				return "Old result";
			});
			panel.set(view);
			view.source.jdbcUrl.setText("jdbc:source");
			onFinished(view, done);
			view.source.test.doClick(0);
			view.testConnection(view.target);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> panel.get().source.jdbcUrl.setText("jdbc:changed"));
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().status.getText().contains("inputs changed"));
			assertFalse(panel.get().statusPanel.detailsText().contains("Old result"));
			assertTrue(panel.get().source.test.isEnabled());
		});
	}

	@Test
	void failedCheckRetainsReviewAndShowsDiagnosticsAndCanRetry() throws Exception {
		final var done = new CountDownLatch(1);
		final var copied = new AtomicReference<char[]>();
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				copied.set(password);
				throw new IllegalArgumentException("Connection refused");
			});
			panel.set(view);
			view.target.jdbcUrl.setText("jdbc:target");
			view.target.password.setText("secret");
			view.configurationFile.setText("previous.yaml");
			view.view.summary.setText("Previous review");
			onFinished(view, done);
			view.target.test.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[6], copied.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(view.status.getText().startsWith("Target connection failed"));
			assertTrue(view.statusPanel.detailsText().contains("Connection refused"));
			assertEquals("previous.yaml", view.configurationFile.getText());
			assertEquals("Previous review", view.view.summary.getText());
			assertTrue(view.target.test.isEnabled());
		});
	}

	@Test
	void invalidUrlFailsBeforeOpeningConnection() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				throw new AssertionError("Must not connect");
			});
			view.source.test.doClick(0);
			assertTrue(view.status.getText().startsWith("Source connection: enter a JDBC URL"));
			assertFalse(view.isBusy());
		});
	}

	private static DataMigrationPlanPanel panel(DataMigrationPlanPanel.ConnectionTester tester) {
		return new DataMigrationPlanPanel((request, source, sourcePassword, target, targetPassword) -> {
			throw new AssertionError("Connection testing must not inspect a migration plan");
		}, (result, path) -> {
			throw new AssertionError("Connection testing must not save a migration");
		}, tester);
	}

	private static void onFinished(DataMigrationPlanPanel panel, CountDownLatch done) {
		panel.build.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}
}
