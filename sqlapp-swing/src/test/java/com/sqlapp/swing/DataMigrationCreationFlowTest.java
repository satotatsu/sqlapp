/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.*;

class DataMigrationCreationFlowTest {
	@TempDir
	Path directory;

	@Test
	void savedSelectionAutomaticallyInspectsButNeverExecutes() throws Exception {
		final var calls = new AtomicInteger();
		final var finished = new CountDownLatch(2);
		final var secret = new AtomicReference<char[]>();
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		final Path job = directory.resolve("migration.yaml");
		java.nio.file.Files.writeString(job, "schemaFile: source-schema.xml");
		final String fingerprint = "sha256:" + "a".repeat(64);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new DataMigrationPlanPanel((request, source, password, target, targetPassword) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertEquals(job, request.configurationFile());
				assertEquals(fingerprint, request.expectedConfigurationFingerprint());
				assertArrayEquals("secret".toCharArray(), password);
				calls.incrementAndGet();
				final var report = new BulkMigrationOperationalReport(3, Instant.now(), "job", "plan", true, 0, 0, 0,
						List.of(), List.of(), null, null, List.of(), null);
				return new DataMigrationPlanningService.Result(job, report,
						new ReportReviewService().operational(report));
			}, (result, path) -> {
			}, (result, source, password, target, targetPassword, control) -> {
				throw new AssertionError("Creation must not execute");
			}, (result, path) -> {
			}, (source, password, schema, parent) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				secret.set(password);
				assertArrayEquals("secret".toCharArray(), password);
				return new DataMigrationDraftService.Prepared(parent, null);
			}, prepared -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				return new BulkMigrationJobConfigurationEditorIO.Snapshot(job, "yaml", fingerprint);
			});
			panel.set(view);
			configure(view);
			view.build.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					finished.countDown();
			});
			view.createMigration(directory);
			assertFalse(view.newMigration.isEnabled());
		});
		assertTrue(finished.await(10, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		assertArrayEquals(new char[6], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(job.toString(), panel.get().configurationFile.getText());
			assertTrue(panel.get().newMigration.isEnabled());
		});
	}

	@Test
	void changedInputsDiscardDiscoveryBeforeOpeningSelection() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var secret = new AtomicReference<char[]>();
		final var panel = new AtomicReference<DataMigrationPlanPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new DataMigrationPlanPanel((request, source, password, target, targetPassword) -> {
				throw new AssertionError("Stale discovery must not inspect");
			}, (result, path) -> {
			}, (result, source, password, target, targetPassword, control) -> {
				throw new AssertionError("Must not execute");
			}, (result, path) -> {
			}, (source, password, schema, parent) -> {
				secret.set(password);
				entered.countDown();
				try {
					assertTrue(release.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException ex) {
					throw new RuntimeException(ex);
				}
				return new DataMigrationDraftService.Prepared(parent, null);
			}, prepared -> {
				throw new AssertionError("Stale discovery must not show selection");
			});
			panel.set(view);
			configure(view);
			view.configurationFile.setText("previous.yaml");
			view.build.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					finished.countDown();
			});
			view.createMigration(directory);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> panel.get().sourceSchema.setText("other"));
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertArrayEquals(new char[6], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("previous.yaml", panel.get().configurationFile.getText());
			assertTrue(panel.get().status.getText().contains("changed"));
		});
	}

	private static void configure(DataMigrationPlanPanel panel) {
		panel.source.jdbcUrl.setText("jdbc:source");
		panel.target.jdbcUrl.setText("jdbc:target");
		panel.source.password.setText("secret");
	}

	@Test
	void cancellationAndDiscoveryFailureRetainPreviousSettingsAndReview() throws Exception {
		for (boolean fail : new boolean[] { false, true }) {
			final var finished = new CountDownLatch(1);
			final var panel = new AtomicReference<DataMigrationPlanPanel>();
			SwingUtilities.invokeAndWait(() -> {
				final var view = new DataMigrationPlanPanel((request, source, password, target, targetPassword) -> {
					throw new AssertionError("Cancelled or failed creation must not inspect");
				}, (result, path) -> {
				}, (result, source, password, target, targetPassword, control) -> {
					throw new AssertionError("Must not execute");
				}, (result, path) -> {
				}, (source, password, schema, parent) -> {
					if (fail)
						throw new IllegalArgumentException("Source is unavailable");
					return new DataMigrationDraftService.Prepared(parent, null);
				}, prepared -> null);
				panel.set(view);
				configure(view);
				view.configurationFile.setText("previous.yaml");
				view.expectedFingerprint.setText("previous-fingerprint");
				view.view.summary.setText("Previous review");
				view.build.addPropertyChangeListener("enabled", event -> {
					if (Boolean.TRUE.equals(event.getNewValue()))
						finished.countDown();
				});
				view.createMigration(directory);
			});
			assertTrue(finished.await(5, TimeUnit.SECONDS));
			SwingUtilities.invokeAndWait(() -> {
				assertEquals("previous.yaml", panel.get().configurationFile.getText());
				assertEquals("previous-fingerprint", panel.get().expectedFingerprint.getText());
				assertEquals("Previous review", panel.get().view.summary.getText());
				assertTrue(panel.get().newMigration.isEnabled());
				assertTrue(panel.get().status.getText().contains(fail ? "could not be created" : "cancelled"));
			});
		}
	}

	@Test
	void missingConnectionIdentifiesSourceOrTargetWithoutDiscoveringOrReplacingReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = new DataMigrationPlanPanel((request, source, password, target, targetPassword) -> {
				throw new AssertionError("Invalid connections must not inspect");
			}, (result, path) -> {
			}, (result, source, password, target, targetPassword, control) -> {
				throw new AssertionError("Must not execute");
			}, (result, path) -> {
			}, (source, password, schema, parent) -> {
				throw new AssertionError("Invalid connections must not discover");
			}, prepared -> null);
			view.configurationFile.setText("previous.yaml");
			view.view.summary.setText("Previous review");
			view.createMigration(directory);
			assertTrue(view.status.getText().startsWith("Source connection:"));
			assertEquals("Previous review", view.view.summary.getText());
			view.source.jdbcUrl.setText("jdbc:source");
			view.view.summary.setText("Previous review");
			view.createMigration(directory);
			assertTrue(view.status.getText().startsWith("Target connection:"));
			assertEquals("previous.yaml", view.configurationFile.getText());
			assertEquals("Previous review", view.view.summary.getText());
			assertFalse(view.isBusy());
		});
	}

	@Test
	void tableSelectionCanBeCancelledWithButtonOrEscapeWithoutSaving() throws Exception {
		final var cancelled = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			final var schema = new BulkMigrationJobDraftBuilder.SchemaSnapshot(directory.resolve("source-schema.xml"),
					"fingerprint", List.of());
			final var view = new JobDraftPanel(schema, directory, saved -> {
				throw new AssertionError("Cancel must not save");
			}, cancelled::incrementAndGet);
			view.cancel.doClick(0);
			view.getActionMap().get("cancelMigration").actionPerformed(new java.awt.event.ActionEvent(view, 0, ""));
		});
		assertEquals(2, cancelled.get());
		try (var files = java.nio.file.Files.list(directory)) {
			assertEquals(0, files.count());
		}
	}
}
