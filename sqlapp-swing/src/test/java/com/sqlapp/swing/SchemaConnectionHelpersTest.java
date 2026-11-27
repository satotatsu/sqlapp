/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SchemaConnectionHelpersTest {
	@Test
	void testsConnectionWithoutSqlDirectoryAndKeepsReviewAndCredentials() throws Exception {
		final var finished = new CountDownLatch(1);
		final var secret = new AtomicReference<char[]>();
		final var panel = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertEquals("jdbc:example", connection.jdbcUrl());
				assertEquals("user", connection.username());
				assertEquals("custom.Driver", connection.driverClass());
				assertArrayEquals("secret".toCharArray(), password);
				secret.set(password);
				return "ExampleDB 1.0";
			});
			panel.set(view);
			configure(view);
			view.view.summary.setText("Previous review");
			onFinished(view, finished);
			view.test.doClick(0);
			assertTrue(view.isBusy());
			assertFalse(view.buildUrl.isEnabled());
			assertFalse(view.jdbcUrl.isEnabled());
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[6], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals("Connection succeeded: ExampleDB 1.0", view.status.getText());
			assertTrue(view.statusPanel.detailsText().contains("does not approve execution"));
			assertEquals("Previous review", view.view.summary.getText());
			assertEquals("", view.upDirectory.getText());
			assertArrayEquals("secret".toCharArray(), view.password.getPassword());
			assertFalse(view.apply.isEnabled());
			assertFalse(view.isBusy());
			assertTrue(view.buildUrl.isEnabled());
		});
	}

	@Test
	void failureRetainsReviewAndProvidesDetailsAndRestoresButtons() throws Exception {
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<SchemaMigrationPlanPanel>();
		final var secret = new AtomicReference<char[]>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				secret.set(password);
				throw new IllegalArgumentException("Driver unavailable");
			});
			panel.set(view);
			configure(view);
			view.view.summary.setText("Previous review");
			onFinished(view, finished);
			view.test.doClick(0);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[6], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(view.status.getText().startsWith("Connection failed"));
			assertTrue(view.statusPanel.detailsText().contains("Driver unavailable"));
			assertEquals("Previous review", view.view.summary.getText());
			assertTrue(view.test.isEnabled());
			assertFalse(view.isBusy());
		});
	}

	@Test
	void rejectsDuplicateOperationsAndDropsResultsAfterInputsChange() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var panel = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				calls.incrementAndGet();
				entered.countDown();
				try {
					assertTrue(release.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException failure) {
					throw new RuntimeException(failure);
				}
				return "Stale database";
			});
			panel.set(view);
			configure(view);
			onFinished(view, finished);
			view.test.doClick(0);
			view.testConnection();
			view.buildConnectionUrl(current -> {
				throw new AssertionError("Busy screen must not open URL builder");
			});
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> panel.get().jdbcUrl.setText("jdbc:changed"));
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().status.getText().contains("inputs changed"));
			assertFalse(panel.get().statusPanel.detailsText().contains("Stale database"));
			assertFalse(panel.get().apply.isEnabled());
		});
	}

	@Test
	void builderChangesOnlyUrlAndCancellingOrStaleInputsCannotReplaceIt() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				throw new AssertionError("Builder must not connect");
			});
			configure(view);
			view.upDirectory.setText("up-sql");
			view.view.summary.setText("Previous review");
			view.buildConnectionUrl(current -> {
				assertTrue(view.isBusy());
				return null;
			});
			assertEquals("Previous review", view.view.summary.getText());
			assertEquals("jdbc:example", view.jdbcUrl.getText());
			view.buildConnectionUrl(current -> "jdbc:postgresql://db:5432/sales");
			assertEquals("jdbc:postgresql://db:5432/sales", view.jdbcUrl.getText());
			assertEquals("user", view.username.getText());
			assertArrayEquals("secret".toCharArray(), view.password.getPassword());
			assertEquals("custom.Driver", view.driverClass.getText());
			assertEquals("up-sql", view.upDirectory.getText());
			assertTrue(view.checksumValidation.isSelected());
			assertEquals("", view.view.summary.getText());
			assertFalse(view.apply.isEnabled());
			view.buildConnectionUrl(current -> {
				view.jdbcUrl.setText("jdbc:newer");
				return "jdbc:stale";
			});
			assertEquals("jdbc:newer", view.jdbcUrl.getText());
			assertTrue(view.status.getText().contains("inputs changed"));
			assertFalse(view.isBusy());
		});
	}

	@Test
	void invalidUrlDoesNotConnectOrErasePreviousReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel((connection, password) -> {
				throw new AssertionError("Invalid URL must not connect");
			});
			view.view.summary.setText("Previous review");
			view.test.doClick(0);
			assertTrue(view.status.getText().contains("Enter a JDBC URL"));
			assertEquals("Previous review", view.view.summary.getText());
			assertFalse(view.isBusy());
		});
	}

	private static SchemaMigrationPlanPanel panel(
			java.util.function.BiFunction<PlanningJdbc.Spec, char[], String> tester) {
		return new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> {
			throw new AssertionError("Connection helpers must not read migration history or SQL");
		}, (result, destination) -> {
			throw new AssertionError("Connection helpers must not save artifacts");
		}, tester);
	}

	private static void configure(SchemaMigrationPlanPanel view) {
		view.jdbcUrl.setText("jdbc:example");
		view.username.setText("user");
		view.password.setText("secret");
		view.driverClass.setText("custom.Driver");
	}

	private static void onFinished(SchemaMigrationPlanPanel view, CountDownLatch finished) {
		view.build.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				finished.countDown();
		});
	}
}
