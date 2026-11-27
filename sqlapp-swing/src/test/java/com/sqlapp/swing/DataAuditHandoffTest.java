/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationArtifactProvenance;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport;

class DataAuditHandoffTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(strings = { "COMPLETED", "FAILED", "PAUSED", "BOTH" })
	void opensWrittenAuditWithMatchingReaderAndRetainsLinkAfterFailedSave(final String state) throws Exception {
		final Path job = Files.writeString(directory.resolve("job.yaml"), "{}");
		final Path auditFile = directory.resolve("audit.json");
		final boolean hasFailure = !state.equals("COMPLETED");
		final String failureState = state.equals("PAUSED") ? "PAUSED" : "FAILED";
		final var execution = new BulkMigrationJobExecutionReport(1, Instant.now(), "job", "plan", 0, 0, List.of(),
				null);
		final var failure = new BulkMigrationJobFailureReport(1, Instant.now(), failureState, "job", "plan", "items",
				"example.Failure", "Stopped during copy", List.of(), null);
		final var outcome = new DataMigrationExecutionService.Outcome(
				hasFailure && !state.equals("BOTH") ? null : execution, hasFailure ? failure : null,
				new ReportReviewService.Review("Copy outcome", List.of(), List.of(), "{}"), null);
		final var status = new BulkMigrationOperationalReport(3, Instant.now(), "job", "plan", true, 0, 0, 0, List.of(),
				List.of(), null, null, List.of(), null,
				new BulkMigrationArtifactProvenance("sha256:" + "a".repeat(64), null, null));
		final var reviewed = new DataMigrationPlanningService.Result(job, status,
				new ReportReviewService().operational(status),
				new DataMigrationPlanningService.ExecutionSnapshot(null, null, null));
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		final var reports = new AtomicReference<ReportReviewPanel>();
		final var planned = new CountDownLatch(1);
		final var executed = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var loaded = new CountDownLatch(1);
		final var openedFile = new AtomicReference<Path>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new DataMigrationPlanPanel((request, source, sp, target, tp) -> reviewed,
					(result, destination) -> fail("Not a status save"),
					(result, source, sp, target, tp, control) -> outcome, new DataMigrationExecutionService()::save);
			final var viewer = new ReportReviewPanel();
			panel.set(view);
			reports.set(viewer);
			view.savedReport.setReceiver((file, type) -> {
				openedFile.set(file);
				assertEquals(
						hasFailure ? ReportReviewService.Type.BULK_FAILURE : ReportReviewService.Type.BULK_EXECUTION,
						type);
				return viewer.requestOpen(file, type);
			});
			view.configurationFile.setText(job.toString());
			view.source.jdbcUrl.setText("jdbc:unused-source");
			view.target.jdbcUrl.setText("jdbc:unused-target");
			onEnabled(view.build, planned);
			view.build.doClick(0);
		});
		assertTrue(planned.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(panel.get().build, executed);
			panel.get().executeReviewed(new char[0], new char[0]);
		});
		assertTrue(executed.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(panel.get().saveExecution, saved);
			panel.get().saveExecutionTo(auditFile);
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().savedReport.isEnabled());
			onEnabled(reports.get().load, loaded);
			panel.get().savedReport.doClick(0);
		});
		assertTrue(loaded.await(5, TimeUnit.SECONDS));
		final var failedSave = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(auditFile.toAbsolutePath().normalize(), openedFile.get());
			assertTrue(
					reports.get().summary.getText()
							.contains("Saved outcome: " + (hasFailure ? failureState : "COMPLETED")),
					reports.get().summary.getText());
			panel.get().configurationFile.setText("changed.yaml");
			onEnabled(panel.get().saveExecution, failedSave);
			panel.get().saveExecutionTo(directory.resolve("invalid.txt"));
		});
		assertTrue(failedSave.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().savedReport.isEnabled());
			assertTrue(panel.get().savedReport.getToolTipText().contains(auditFile.toString()));
			assertFalse(panel.get().savedReport.getToolTipText().contains("invalid.txt"));
		});
		assertFalse(Files.exists(directory.resolve("invalid.txt")));
	}

	private static void onEnabled(final JButton button, final CountDownLatch finished) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				finished.countDown();
		});
	}
}
