/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationArtifactProvenance;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport;

class DataMigrationExecutionPanelTest {
	@TempDir
	Path directory;

	@Test
	void consumesPlanRunsOffEdtRetainsOutcomeAndSavesAfterInputChanges() throws Exception {
		final Path job = Files.writeString(directory.resolve("job.yaml"), "{}");
		final var report = new BulkMigrationOperationalReport(3, Instant.now(), "job", "plan", true, 0, 0, 0, List.of(),
				List.of(), null, null, List.of(), null,
				new BulkMigrationArtifactProvenance("sha256:" + "a".repeat(64), null, null));
		final var reviewed = new DataMigrationPlanningService.Result(job, report,
				new ReportReviewService().operational(report),
				new DataMigrationPlanningService.ExecutionSnapshot(null, null, null));
		final var audit = new BulkMigrationJobExecutionReport(1, Instant.now(), "job", "plan", 0, 0, List.of(), null);
		final var outcome = new DataMigrationExecutionService.Outcome(audit, null,
				new ReportReviewService.Review("Finished", List.of(), List.of(), "audit"), null);
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		final var planned = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var activeControl = new AtomicReference<DataMigrationExecutionControl>();
		final var copying = new CountDownLatch(1);
		final var secrets = new AtomicReference<char[][]>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new DataMigrationPlanPanel(
					(request, source, sourcePassword, target, targetPassword) -> reviewed, (result, destination) -> {
					}, (result, source, sourcePassword, target, targetPassword, control) -> {
						assertFalse(SwingUtilities.isEventDispatchThread());
						assertSame(reviewed, result);
						assertEquals("jdbc:source", source.jdbcUrl());
						assertEquals("jdbc:target", target.jdbcUrl());
						calls.incrementAndGet();
						activeControl.set(control);
						control.configurePause(true);
						control.jobListener().onTaskStarted("items", 0, 1);
						control.chunkListener().onChunkCompleted(
								new com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress("items", 2, 5, 10, 15));
						copying.countDown();
						secrets.set(new char[][] { sourcePassword, targetPassword });
						try {
							if (!release.await(5, TimeUnit.SECONDS))
								throw new IllegalStateException("Timeout");
						} catch (InterruptedException exception) {
							Thread.currentThread().interrupt();
							throw new IllegalStateException(exception);
						}
						return outcome;
					}, (result, destination) -> {
						assertFalse(SwingUtilities.isEventDispatchThread());
						assertSame(outcome, result);
					});
			panel.set(view);
			view.configurationFile.setText(job.toString());
			view.source.jdbcUrl.setText("jdbc:source");
			view.target.jdbcUrl.setText("jdbc:target");
			onEnabled(view.build, planned);
			view.build.doClick();
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertTrue(view.apply.isEnabled());
			onEnabled(view.build, finished);
			final char[] first = "one".toCharArray(), second = "two".toCharArray();
			view.executeReviewed(first, second);
			assertArrayEquals(new char[3], first);
			assertArrayEquals(new char[3], second);
			assertTrue(view.isExecuting());
			assertFalse(view.apply.isEnabled());
			assertFalse(view.source.jdbcUrl.isEnabled());
			view.executeReviewed(new char[0], new char[0]);
			// Programmatic changes must not discard a completed database operation.
			view.target.username.setText("changed");
		});
		assertTrue(copying.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			view.refreshProgress();
			assertTrue(view.activity.isIndeterminate());
			assertTrue(view.progress.getText().contains("15 checkpoint rows; 3 chunks"));
			assertTrue(view.pause.isEnabled());
			view.pause.doClick();
			assertTrue(activeControl.get().pauseRequested());
			assertFalse(view.pause.isEnabled());
			assertTrue(view.progress.getText().contains("Pause requested"));
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		for (final char[] secret : secrets.get())
			assertArrayEquals(new char[secret.length], secret);
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertFalse(view.isExecuting());
			assertFalse(view.activity.isIndeterminate());
			assertTrue(view.progress.getText().contains("Last checkpoint: items: 15 rows; 3 chunks"));
			assertFalse(view.pause.isEnabled());
			assertFalse(view.apply.isEnabled());
			assertEquals("Finished", view.view.summary.getText());
			assertTrue(view.saveExecution.isEnabled());
			view.configurationFile.setText("changed.yaml");
			onEnabled(view.saveExecution, saved);
			view.saveExecutionTo(directory.resolve("audit.json"));
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
	}

	@Test
	void executionWithoutReviewDoesNotRunAndClearsPasswords() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DataMigrationPlanPanel(
					(request, source, sourcePassword, target, targetPassword) -> null, (result, destination) -> {
					}, (result, source, sourcePassword, target, targetPassword, control) -> {
						fail("Execution requires a reviewed plan");
						return null;
					}, (result, destination) -> {
					});
			final char[] source = "one".toCharArray(), target = "two".toCharArray();
			panel.executeReviewed(source, target);
			assertArrayEquals(new char[3], source);
			assertArrayEquals(new char[3], target);
			assertFalse(panel.isExecuting());
		});
	}

	private static void onEnabled(final javax.swing.JButton button, final CountDownLatch latch) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				latch.countDown();
		});
	}
}
