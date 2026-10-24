/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.schema.MigrationPlan;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact;

class SchemaMigrationPlanPanelTest {
	@TempDir
	Path directory;

	@Test
	void connectsOnlyOnActionAndBuildsAndSavesOffTheEdt() throws Exception {
		final var finished = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var secret = new AtomicReference<char[]>();
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		final var expected = result();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, username, password, driver) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				calls.incrementAndGet();
				assertEquals("jdbc:example:database", url);
				assertEquals("reviewer", username);
				assertArrayEquals("test_password".toCharArray(), password);
				assertTrue(request.checksumValidation());
				assertTrue(request.rejectOutOfOrder());
				assertTrue(request.rejectNonTransactional());
				assertFalse(request.requireDownMigration());
				secret.set(password);
				return expected;
			}, (result, destination) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertSame(expected, result);
				assertEquals(directory.resolve("plan.json"), destination);
			});
			view.set(panel);
			assertEquals(0, calls.get());
			assertNull(SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, panel.build));
			assertNull(SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, panel.save));
			configure(panel);
			panel.password.setText("test_password");
			onEnabled(panel.build, finished);
			panel.build.doClick();
			assertFalse(panel.build.isEnabled());
			assertFalse(panel.jdbcUrl.isEnabled());
			assertEquals(0, panel.password.getPassword().length);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		assertArrayEquals(new char[secret.get().length], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			final var panel = view.get();
			assertEquals(expected.review().summary(), panel.view.summary.getText());
			assertTrue(panel.save.isEnabled());
			assertTrue(panel.status.getText().contains("not executed"));
			onEnabled(panel.save, saved);
			panel.saveTo(directory.resolve("plan.json"));
			assertFalse(panel.build.isEnabled());
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().status.getText().startsWith("Saved plan:"));
			view.get().username.setText("different_user");
			assertEquals("", view.get().view.summary.getText());
			assertFalse(view.get().save.isEnabled());
		});
	}

	@Test
	void invalidVersionStopsBeforeCallingPlanner() throws Exception {
		final var finished = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, username, password, driver) -> {
				calls.incrementAndGet();
				return result();
			}, (result, destination) -> {
			});
			view.set(panel);
			configure(panel);
			panel.targetVersion.setText("not-a-version");
			onEnabled(panel.build, finished);
			panel.build.doClick();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(0, calls.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().view.summary.getText().contains("Target version"));
			assertFalse(view.get().save.isEnabled());
			assertTrue(view.get().jdbcUrl.isEnabled());
		});
	}

	@Test
	void mapsAdvancedPoliciesAndClearsResultsWhenPolicyChanges() throws Exception {
		final Path down = Files.createDirectory(directory.resolve("down"));
		final Path setup = Files.createDirectory(directory.resolve("setup"));
		final Path finish = Files.createDirectory(directory.resolve("finish"));
		final var finished = new CountDownLatch(1);
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, password, driver) -> {
				assertEquals(down, request.downDirectory());
				assertEquals(setup, request.setupDirectory());
				assertEquals(finish, request.finalizeDirectory());
				assertEquals(30L, request.targetVersion());
				assertEquals("custom_history", request.historyTable());
				assertTrue(request.requireDownMigration());
				assertTrue(request.repeatableMigrations());
				assertTrue(request.recursive());
				assertEquals("example.Driver", driver);
				return result();
			}, (result, destination) -> {
			});
			view.set(panel);
			configure(panel);
			panel.downDirectory.setText(down.toString());
			panel.setupDirectory.setText(setup.toString());
			panel.finalizeDirectory.setText(finish.toString());
			panel.targetVersion.setText("30");
			panel.historyTable.setText("custom_history");
			panel.driverClass.setText("example.Driver");
			panel.requireDown.doClick();
			panel.repeatables.doClick();
			panel.recursive.doClick();
			onEnabled(panel.build, finished);
			panel.build.doClick();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().save.isEnabled());
			view.get().requireDown.doClick();
			assertFalse(view.get().save.isEnabled());
			assertEquals("", view.get().view.details.getText());
		});
	}

	@Test
	void discardsStaleResultAndClearsPasswordOnPlannerFailure() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		final var secret = new AtomicReference<char[]>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, password, driver) -> {
				secret.set(password);
				try {
					if (!release.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Test release timed out");
					}
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				throw new IllegalArgumentException("Connection failed");
			}, (result, destination) -> {
			});
			view.set(panel);
			configure(panel);
			panel.password.setText("private");
			onEnabled(panel.build, finished);
			panel.build.doClick();
			panel.jdbcUrl.setText("jdbc:changed");
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[secret.get().length], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("", view.get().view.summary.getText());
			assertFalse(view.get().save.isEnabled());
			assertTrue(view.get().build.isEnabled());
		});
	}

	@Test
	void reportsConnectionFailureAndRestoresInputs() throws Exception {
		final var finished = new CountDownLatch(1);
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, password, driver) -> {
				throw new IllegalArgumentException("Driver not found");
			}, (result, destination) -> {
			});
			view.set(panel);
			configure(panel);
			onEnabled(panel.build, finished);
			panel.build.doClick();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().view.summary.getText().contains("Driver not found"));
			assertEquals("", view.get().view.details.getText());
			assertFalse(view.get().save.isEnabled());
			assertTrue(view.get().upDirectory.isEnabled());
		});
	}

	private void configure(final SchemaMigrationPlanPanel panel) {
		panel.jdbcUrl.setText("jdbc:example:database");
		panel.username.setText("reviewer");
		panel.upDirectory.setText(directory.toString());
	}

	private static SchemaMigrationPlanningService.Result result() {
		final var artifact = MigrationPlanArtifact
				.of(new MigrationPlan(false, null, 30L, 0, 0, List.of(), List.of(), null));
		return new SchemaMigrationPlanningService.Result(artifact, new ReportReviewService().schemaPlan(artifact));
	}

	private void onEnabled(final javax.swing.JButton button, final CountDownLatch finished) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue())) {
				finished.countDown();
			}
		});
	}
}
