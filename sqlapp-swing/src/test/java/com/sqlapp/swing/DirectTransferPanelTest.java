/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;

class DirectTransferPanelTest {
	@TempDir
	Path directory;
	private static final String HASH = "sha256:" + "a".repeat(64);

	@Test
	void repeatedFailuresReplaceSummaryAndRemoveStaleReviewDetails() throws Exception {
		final var calls = new AtomicInteger();
		final var ref = new AtomicReference<DirectTransferPanel>();
		final var ready = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DirectTransferPanel((action, request, from, to) -> {
				if (calls.getAndIncrement() == 0)
					return reviewed();
				throw new TransferActionFailure("opening target connection", "Database authentication failed.",
						"Check username/password and test the target connection.", "SQLState: 28P01; vendor code: 0");
			}, prepared -> null);
			ref.set(panel);
			configure(panel);
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		String previous = null;
		for (int attempt = 0; attempt < 2; attempt++) {
			final var done = new CountDownLatch(1);
			SwingUtilities.invokeAndWait(() -> {
				final var panel = ref.get();
				panel.results.setSelectedIndex(1);
				onEnabled(panel.review, done);
				panel.review.doClick(0);
			});
			assertTrue(done.await(5, TimeUnit.SECONDS));
			final var text = new AtomicReference<String>();
			SwingUtilities.invokeAndWait(() -> {
				final var panel = ref.get();
				text.set(panel.summary.getText());
				assertEquals(0, panel.results.getSelectedIndex());
				assertFalse(panel.execute.isEnabled());
				assertTrue(panel.summary.getText().contains("opening target connection"));
				assertTrue(panel.summary.getText().contains("authentication failed"));
				assertTrue(panel.summary.getText().contains("28P01"));
				assertTrue(panel.summary.getText().contains("username/password"));
				assertEquals("", panel.result.editor.getText());
				assertEquals(-1, panel.results.indexOfComponent(panel.schemaSql));
				assertEquals(-1, panel.results.indexOfComponent(panel.gradle));
				assertEquals(panel.summary.getText(), panel.statusPanel.detailsText());
			});
			if (previous != null)
				assertEquals(previous, text.get());
			previous = text.get();
		}
	}

	@Test
	void missingSchemaFromRealReviewAppearsInSummaryWithoutOpeningTheDetailsJson() throws Exception {
		final var source = new PlanningJdbc.Spec("jdbc:hsqldb:mem:missing_schema_source_" + UUID.randomUUID(), "SA",
				"");
		final var target = new PlanningJdbc.Spec("jdbc:hsqldb:mem:missing_schema_target_" + UUID.randomUUID(), "SA",
				"");
		try (var connection = java.sql.DriverManager.getConnection(source.jdbcUrl(), "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		}
		final var file = java.nio.file.Files.writeString(directory.resolve("missing-schema.yaml"),
				"formatVersion: 1\nsource: source\ntarget: target\ntables: [ITEMS]\nschemaMode: CREATE_MISSING\ntargetSchema: ARCHIVE\n");
		final var ready = new CountDownLatch(1);
		final var ref = new AtomicReference<DirectTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DirectTransferPanel();
			ref.set(panel);
			panel.source.jdbcUrl.setText(source.jdbcUrl());
			panel.source.username.setText("SA");
			panel.target.jdbcUrl.setText(target.jdbcUrl());
			panel.target.username.setText("SA");
			panel.job.setText(file.toString());
			panel.workspace.setText(directory.toString());
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertFalse(panel.execute.isEnabled());
			assertEquals(0, panel.results.getSelectedIndex());
			assertTrue(panel.summary.getText().contains("Target schema 'ARCHIVE'"), panel.summary.getText());
			assertTrue(panel.summary.getText().contains("'PUBLIC'"));
			assertFalse(panel.summary.getText().contains("jdbc:"));
		});
	}

	@Test
	void discoverySavesSharedJobWithoutAutomaticReviewOrExecution() throws Exception {
		final var done = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var ref = new AtomicReference<DirectTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DirectTransferPanel((action, request, from, to) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertEquals(DirectTransferPanel.Action.DISCOVER, action);
				assertNull(request.target());
				calls.incrementAndGet();
				return new DataMigrationDraftService.Prepared(directory, null);
			}, prepared -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				return directory.resolve("transfer.yaml");
			});
			ref.set(panel);
			panel.source.jdbcUrl.setText("jdbc:source");
			panel.workspace.setText(directory.toString());
			onEnabled(panel.create, done);
			panel.discover();
			assertTrue(panel.isBusy());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(directory.resolve("transfer.yaml").toString(), ref.get().job.getText());
			assertFalse(ref.get().execute.isEnabled());
		});
	}

	@Test
	void confirmationRequiresFreshReviewAndExecutionConsumesItAndClearsSecrets() throws Exception {
		final var ref = new AtomicReference<DirectTransferPanel>();
		final var ready = new CountDownLatch(1);
		final var copies = new AtomicInteger();
		final var fromCopy = new AtomicReference<char[]>();
		final var toCopy = new AtomicReference<char[]>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DirectTransferPanel((action, request, from, to) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				if (action == DirectTransferPanel.Action.REVIEW)
					return reviewed();
				assertEquals(DirectTransferPanel.Action.EXECUTE, action);
				assertNotNull(request.reviewed());
				copies.incrementAndGet();
				fromCopy.set(from);
				toCopy.set(to);
				return executed(TransferJobExecutionService.Phase.COMPLETE);
			}, prepared -> null);
			ref.set(panel);
			configure(panel);
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
			assertFalse(panel.source.jdbcUrl.isEnabled());
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertTrue(panel.execute.isEnabled());
			assertTrue(panel.status.getText().contains("warnings: 1"));
			assertTrue(panel.summary.getText().contains("No rows have been copied"));
			assertTrue(panel.summary.getText().contains("Compatibility fixture warning"));
			assertFalse(panel.summary.isEditable());
			assertEquals(0, panel.results.getSelectedIndex());
			assertTrue(panel.schemaSql.editor.getText().contains("CREATE TABLE"));
			assertFalse(panel.schemaSql.editor.isEditable());
			assertTrue(panel.results.indexOfComponent(panel.schemaSql) >= 0);
			assertTrue(panel.results.indexOfComponent(panel.gradle) >= 0);
			assertFalse(panel.gradle.editor.isEditable());
			assertTrue(panel.gradle.editor.getText().contains("-PconfigurationFingerprint=" + HASH));
			assertFalse(panel.gradle.editor.getText().contains("jdbc:source"));
			assertFalse(panel.gradle.editor.getText().contains("jdbc:target"));
			panel.executeReviewed(() -> false);
			assertEquals(0, copies.get());
			assertTrue(panel.execute.isEnabled());
			panel.editSettings(path -> {
				assertTrue(panel.isBusy());
				return null;
			});
			assertTrue(panel.execute.isEnabled());
			panel.editSettings(path -> reviewed().review().prepared().job());
			assertFalse(panel.execute.isEnabled());
		});
		final var settingsReview = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(ref.get().review, settingsReview);
			ref.get().review.doClick(0);
		});
		assertTrue(settingsReview.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			panel.executeReviewed(() -> {
				assertTrue(panel.isBusy());
				panel.target.username.setText("changed");
				return true;
			});
			assertEquals(0, copies.get());
			assertFalse(panel.execute.isEnabled());
			assertEquals(-1, panel.results.indexOfComponent(panel.schemaSql));
			assertEquals("", panel.schemaSql.editor.getText());
			assertEquals(-1, panel.results.indexOfComponent(panel.gradle));
			assertEquals("", panel.gradle.editor.getText());
		});
		final var refreshed = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(ref.get().review, refreshed);
			ref.get().review.doClick(0);
		});
		assertTrue(refreshed.await(5, TimeUnit.SECONDS));
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			panel.status.addPropertyChangeListener("text", event -> {
				if (event.getNewValue().toString().startsWith("Direct transfer finished"))
					finished.countDown();
			});
			panel.executeReviewed(() -> true);
			assertTrue(panel.isBusy());
			assertEquals(0, panel.source.password.getPassword().length);
			assertEquals(0, panel.target.password.getPassword().length);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(1, copies.get());
		assertArrayEquals(new char[6], fromCopy.get());
		assertArrayEquals(new char[6], toCopy.get());
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(ref.get().execute.isEnabled());
			assertFalse(ref.get().result.editor.isEditable());
			assertTrue(ref.get().result.editor.getText().contains("COMPLETE"));
			assertTrue(ref.get().result.editor.getLineCount() > 5);
			assertEquals(-1, ref.get().results.indexOfComponent(ref.get().gradle));
		});
	}

	@Test
	void changedInputsDiscardBackgroundReview() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var done = new CountDownLatch(1);
		final var ref = new AtomicReference<DirectTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DirectTransferPanel((action, request, from, to) -> {
				entered.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return reviewed();
			}, prepared -> null);
			ref.set(panel);
			configure(panel);
			onEnabled(panel.review, done);
			panel.review.doClick(0);
			assertFalse(panel.selectDriverClass("driver.Test", true));
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> ref.get().job.setText("other.yaml"));
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(ref.get().execute.isEnabled());
			assertTrue(ref.get().status.getText().contains("Inputs changed"));
		});
	}

	@Test
	void failedExecutionShowsRetainedOutcomeAndCannotRunAgainWithoutReview() throws Exception {
		final var ready = new CountDownLatch(1);
		final var failed = new CountDownLatch(1);
		final var ref = new AtomicReference<DirectTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new DirectTransferPanel((action, request, from, to) -> {
				if (action == DirectTransferPanel.Action.REVIEW)
					return reviewed();
				throw new DirectTransferDesktopService.ExecutionFailure(
						executed(TransferJobExecutionService.Phase.COPY), "Committed work may remain.");
			}, prepared -> null);
			ref.set(panel);
			configure(panel);
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			panel.status.addPropertyChangeListener("text", event -> {
				if (event.getNewValue().toString().startsWith("Direct transfer action failed"))
					failed.countDown();
			});
			panel.executeReviewed(() -> true);
		});
		assertTrue(failed.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(ref.get().execute.isEnabled());
			assertTrue(ref.get().result.editor.getText().contains("COPY"));
			assertTrue(ref.get().result.editor.getText().contains("transfer-result.json"));
		});
	}

	private void configure(DirectTransferPanel panel) {
		panel.source.jdbcUrl.setText("jdbc:source");
		panel.target.jdbcUrl.setText("jdbc:target");
		panel.source.password.setText("source");
		panel.target.password.setText("target");
		panel.job.setText("transfer.yaml");
		panel.workspace.setText(directory.toString());
	}

	private DirectTransferDesktopService.Reviewed reviewed() {
		final var cfg = new TransferJobConfiguration("source", "target", List.of("PUBLIC.ITEMS"),
				TransferJobConfiguration.SchemaMode.CREATE_MISSING);
		final var job = new TransferJobConfigurationIO.Snapshot(directory.resolve("transfer.yaml"), "formatVersion: 1",
				HASH, cfg);
		final var warnings = List.of("Compatibility fixture warning");
		final var prepared = new TransferJobPreparationService.Prepared(directory, job,
				directory.resolve("source-schema.xml"), null, HASH, List.of("task"), warnings,
				new TransferSchemaPreparation.Plan(HASH,
						List.of(new TransferSchemaPreparation.Statement("task", "CREATE TABLE ITEMS(ID INTEGER)")),
						warnings));
		return new DirectTransferDesktopService.Reviewed(new TransferJobExecutionService.Review(job.file(), prepared,
				new DatabaseIdentity("HSQL", "2.7", HASH), new DatabaseIdentity("HSQL", "2.7", HASH)),
				directory.resolve("transfer-review.json"));
	}

	private DirectTransferDesktopService.Executed executed(TransferJobExecutionService.Phase phase) {
		return new DirectTransferDesktopService.Executed(
				new TransferJobExecutionService.Outcome(reviewed().review().prepared(), phase, null, null, List.of(),
						Map.of(), null),
				directory.resolve("transfer-result.json"));
	}

	private static void onEnabled(JButton button, CountDownLatch done) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}
}
