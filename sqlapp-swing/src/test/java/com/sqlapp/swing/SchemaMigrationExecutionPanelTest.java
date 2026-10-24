/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReport;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact;
import com.sqlapp.data.db.command.migration.schema.Status;

class SchemaMigrationExecutionPanelTest {
	@TempDir
	Path directory;

	@Test
	void appliesOnlyReviewedSnapshotInBackgroundAndRetainsSavableAuditAfterInputChanges() throws Exception {
		final var reviewed = plan(false);
		final var outcome = outcome(reviewed, true);
		final var planned = new CountDownLatch(1);
		final var executed = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		final char[] password = "execution-secret".toCharArray();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> reviewed,
					(result, file) -> {
					}, (request, connection, snapshot, secret) -> {
						assertFalse(SwingUtilities.isEventDispatchThread());
						calls.incrementAndGet();
						assertSame(reviewed, snapshot);
						assertEquals("jdbc:test", connection.jdbcUrl());
						assertEquals(directory, request.upDirectory());
						assertArrayEquals("execution-secret".toCharArray(), secret);
						await(release);
						return outcome;
					}, (result, file) -> {
						assertFalse(SwingUtilities.isEventDispatchThread());
						assertSame(outcome, result);
						assertEquals(directory.resolve("audit.json"), file);
					});
			view.set(panel);
			startPlan(panel, planned);
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = view.get();
			assertTrue(panel.apply.isEnabled());
			onEnabled(panel.build, executed);
			panel.applyReviewed(password);
			assertTrue(panel.isExecuting());
			assertFalse(panel.build.isEnabled());
			assertFalse(panel.jdbcUrl.isEnabled());
			assertFalse(panel.apply.isEnabled());
			final char[] duplicate = "duplicate".toCharArray();
			panel.applyReviewed(duplicate);
			assertArrayEquals(new char[duplicate.length], duplicate);
		});
		release.countDown();
		assertTrue(executed.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		assertArrayEquals(new char[password.length], password);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = view.get();
			assertFalse(panel.isExecuting());
			assertFalse(panel.apply.isEnabled());
			assertFalse(panel.save.isEnabled());
			assertTrue(panel.saveExecution.isEnabled());
			assertTrue(panel.view.summary.getText().contains("COMPLETED"));
			panel.jdbcUrl.setText("jdbc:changed");
			assertTrue(panel.view.summary.getText().contains("COMPLETED"));
			assertTrue(panel.saveExecution.isEnabled());
			onEnabled(panel.saveExecution, saved);
			panel.saveExecutionTo(directory.resolve("audit.json"));
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		SwingUtilities
				.invokeAndWait(() -> assertTrue(view.get().status.getText().startsWith("Saved execution result:")));
	}

	@Test
	void unreviewedChangedAndBlockedPlansCannotExecute() throws Exception {
		final var planned = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> plan(false),
					(result, file) -> {
					}, (request, connection, reviewed, secret) -> {
						calls.incrementAndGet();
						return outcome(reviewed, true);
					}, (result, file) -> {
					});
			view.set(panel);
			final char[] beforeReview = "secret".toCharArray();
			panel.applyReviewed(beforeReview);
			assertArrayEquals(new char[beforeReview.length], beforeReview);
			startPlan(panel, planned);
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			view.get().username.setText("changed");
			assertFalse(view.get().apply.isEnabled());
			view.get().applyReviewed(new char[0]);
		});
		final var blockedPlan = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var blocked = new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> plan(true),
					(result, file) -> {
					}, (request, connection, reviewed, secret) -> {
						calls.incrementAndGet();
						return outcome(reviewed, false);
					}, (result, file) -> {
					});
			view.set(blocked);
			startPlan(blocked, blockedPlan);
		});
		assertTrue(blockedPlan.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(view.get().apply.isEnabled());
			assertTrue(view.get().apply.getToolTipText().contains("blockers"));
			view.get().applyReviewed(new char[0]);
		});
		assertEquals(0, calls.get());
	}

	@Test
	void failedExecutionEvidenceIsNotDiscardedByInFlightInputChanges() throws Exception {
		final var reviewed = plan(false);
		final var outcome = outcome(reviewed, false);
		final var planned = new CountDownLatch(1);
		final var executed = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, secret, driver) -> reviewed,
					(result, file) -> {
					}, (request, connection, snapshot, secret) -> {
						await(release);
						return outcome;
					}, (result, file) -> {
					});
			view.set(panel);
			startPlan(panel, planned);
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(view.get().build, executed);
			view.get().applyReviewed(new char[0]);
			view.get().jdbcUrl.setText("jdbc:changed-during-execution");
		});
		release.countDown();
		assertTrue(executed.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().view.summary.getText().contains("FAILED"));
			assertTrue(view.get().saveExecution.isEnabled());
			assertFalse(view.get().apply.isEnabled());
			assertFalse(view.get().isExecuting());
		});
	}

	@Test
	void unexpectedExecutionFailureClearsSecretAndRestoresControlsWithoutReenablingApply() throws Exception {
		final var planned = new CountDownLatch(1);
		final var executed = new CountDownLatch(1);
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		final char[] secret = "temporary".toCharArray();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, password, driver) -> plan(false),
					(result, file) -> {
					}, (request, connection, reviewed, password) -> {
						throw new IllegalStateException("Unexpected executor failure");
					}, (result, file) -> {
					});
			view.set(panel);
			startPlan(panel, planned);
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(view.get().build, executed);
			view.get().applyReviewed(secret);
		});
		assertTrue(executed.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[secret.length], secret);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().view.summary.getText().contains("Unexpected executor failure"));
			assertFalse(view.get().saveExecution.isEnabled());
			assertFalse(view.get().apply.isEnabled());
			assertTrue(view.get().jdbcUrl.isEnabled());
			assertFalse(view.get().isExecuting());
		});
	}

	@Test
	void failedAuditSaveRetainsTheExecutionReportForRetry() throws Exception {
		final var planned = new CountDownLatch(1);
		final var executed = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var view = new AtomicReference<SchemaMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaMigrationPlanPanel((request, url, user, password, driver) -> plan(false),
					(result, file) -> {
					}, (request, connection, reviewed, password) -> outcome(reviewed, true), (result, file) -> {
						throw new java.io.IOException("Report destination unavailable");
					});
			view.set(panel);
			startPlan(panel, planned);
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(view.get().build, executed);
			view.get().applyReviewed(new char[0]);
		});
		assertTrue(executed.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(view.get().saveExecution, saved);
			view.get().saveExecutionTo(directory.resolve("audit.json"));
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(view.get().view.summary.getText().contains("Report destination unavailable"));
			assertFalse(view.get().view.details.getText().isEmpty());
			assertTrue(view.get().saveExecution.isEnabled());
			assertFalse(view.get().apply.isEnabled());
		});
	}

	private void startPlan(final SchemaMigrationPlanPanel panel, final CountDownLatch planned) {
		panel.jdbcUrl.setText("jdbc:test");
		panel.username.setText("user");
		panel.upDirectory.setText(directory.toString());
		onEnabled(panel.build, planned);
		panel.build.doClick();
	}

	private static SchemaMigrationPlanningService.Result plan(final boolean blocked) {
		final var plan = new MigrationPlan(false, null, 1L, 0, 0,
				List.of(new MigrationPlan.Entry(1, "create", "1_create.sql", 1, true, true, true, "checksum")),
				blocked ? List.of(new MigrationPlan.HistoryIssue(1, Status.Errored)) : List.of(), null, null, List.of(),
				true, true, false, new MigrationPlan.DatabaseIdentity("HSQLDB", "2.7", "db"));
		final var artifact = MigrationPlanArtifact.of(plan);
		return new SchemaMigrationPlanningService.Result(artifact, new ReportReviewService().schemaPlan(artifact));
	}

	private static SchemaMigrationExecutionService.Outcome outcome(final SchemaMigrationPlanningService.Result reviewed,
			final boolean successful) {
		final long now = System.currentTimeMillis();
		final var report = new MigrationExecutionReport(3, now, now, successful, true,
				reviewed.artifact().plan().databaseIdentity(), reviewed.artifact().planFingerprint(), List.of(1L),
				successful ? List.of(1L) : List.of(), null);
		return new SchemaMigrationExecutionService.Outcome(report, new ReportReviewService().schemaExecution(report),
				successful ? null : "Execution failed");
	}

	private static void onEnabled(final javax.swing.JButton button, final CountDownLatch finished) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue())) {
				finished.countDown();
			}
		});
	}

	private static void await(final CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Test release timed out");
			}
		} catch (final InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
