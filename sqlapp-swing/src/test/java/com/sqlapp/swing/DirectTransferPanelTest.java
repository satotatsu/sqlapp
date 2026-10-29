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
	@Test
	void dataImpactReviewKeepsSqlVisibleButDisablesExecutionUntilPermissionIsSavedAndReviewed() throws Exception {
		var base = reviewed();
		var original = base.review().prepared();
		var configuration = new TransferJobConfiguration(1, "source", "target", List.of("PUBLIC.ITEMS"),
				TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES, null, null, null, null, null,
				TransferJobConfiguration.DataMode.SCHEMA_ONLY, null);
		var job = new TransferJobConfigurationIO.Snapshot(original.job().file(), "difference", HASH, configuration);
		var warnings = List
				.of(TransferSchemaChangeChecks.DATA_IMPACT + " Column removal requires explicit permission.");
		var schema = new TransferSchemaPreparation.Plan(HASH,
				List.of(new TransferSchemaPreparation.Statement("items", "ALTER TABLE ITEMS DROP COLUMN EXTRA")),
				warnings);
		var prepared = new TransferJobPreparationService.Prepared(directory, job, original.sourceSchemaFile(), null,
				HASH, List.of("items"), warnings, schema);
		var review = new DirectTransferDesktopService.Reviewed(
				new TransferJobExecutionService.Review(job.file(), prepared, base.review().sourceIdentity(),
						base.review().targetIdentity()),
				base.report(), "-- Review before use\nALTER TABLE ITEMS DROP COLUMN EXTRA\n;\n");
		var ready = new CountDownLatch(1);
		var ref = new AtomicReference<DirectTransferPanel>();
		var calls = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new DirectTransferPanel((action, request, from, to) -> {
				calls.incrementAndGet();
				return review;
			}, ignored -> null);
			ref.set(panel);
			configure(panel);
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			assertFalse(panel.execute.isEnabled());
			assertTrue(panel.schemaDraft.save.isEnabled());
			assertTrue(panel.summary.getText().contains("EXECUTION BLOCKED"));
			assertTrue(panel.summary.getText().contains("CREATE/ALTER/DROP"));
			assertTrue(panel.schemaSql.editor.getText().contains("DROP COLUMN EXTRA"));
			panel.executeReviewed(() -> {
				fail("A blocked plan must not open execution confirmation");
				return true;
			});
			assertEquals(1, calls.get());
			assertTrue(panel.status.getText().contains("Execution blocked"));
		});
	}

	@Test
	void schemaOnlyReviewAndExecutionClearlyExcludeRowCopyAndVerification() throws Exception {
		var base = reviewed();
		var config = new TransferJobConfiguration(1, "source", "target", List.of("PUBLIC.ITEMS"),
				TransferJobConfiguration.SchemaMode.CREATE_MISSING, null, null, null, null, null,
				TransferJobConfiguration.DataMode.SCHEMA_ONLY, null);
		var original = base.review().prepared();
		var saved = new TransferJobConfigurationIO.Snapshot(original.job().file(), "schema-only", HASH, config);
		var prepared = new TransferJobPreparationService.Prepared(directory, saved, original.sourceSchemaFile(), null,
				HASH, original.taskIds(), original.warnings(), original.schemaPreparation());
		var review = new DirectTransferDesktopService.Reviewed(
				new TransferJobExecutionService.Review(saved.file(), prepared, base.review().sourceIdentity(),
						base.review().targetIdentity()),
				base.report(), "-- Review before use\nCREATE TABLE ITEMS(ID INTEGER)\n;\n");
		var outcome = new TransferJobExecutionService.Outcome(prepared, TransferJobExecutionService.Phase.COMPLETE,
				null, null, List.of(), Map.of(), null,
				new TransferSchemaPreparation.Progress(original.schemaPreparation().statements(), 1, null));
		var ref = new AtomicReference<DirectTransferPanel>();
		var ready = new CountDownLatch(1);
		var finished = new CountDownLatch(1);
		var executed = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new DirectTransferPanel((action, request, from, to) -> {
				if (action == DirectTransferPanel.Action.REVIEW)
					return review;
				executed.set(true);
				return new DirectTransferDesktopService.Executed(outcome, directory.resolve("result.json"),
						review.schemaSqlDraft());
			}, ignored -> null);
			ref.set(panel);
			configure(panel);
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			assertTrue(panel.summary.getText().startsWith("SCHEMA ONLY:"));
			assertFalse(panel.summary.getText().contains("Chunk rows:"));
			assertTrue(panel.gradle.editor.getText().contains("SCHEMA_ONLY"));
			panel.review.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()) && executed.get())
					finished.countDown();
			});
			panel.executeReviewed(() -> true);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			var text = ref.get().summary.getText();
			assertTrue(text.contains("Row copy: not requested"));
			assertTrue(text.contains("DDL statements completed: 1 / 1"));
			assertFalse(text.contains("Row verification was disabled"));
			assertFalse(text.contains("Completed tables: 0"));
			assertTrue(ref.get().schemaSql.editor.getText().startsWith("-- RECORDED EXECUTION: COMPLETE"));
			assertTrue(ref.get().schemaSql.editor.getText().contains("FULL planned SQL"));
			assertTrue(ref.get().schemaDraft.save.isEnabled());
		});
	}

	@Test
	void savedSchemaDraftOpensEditorAndInputChangesClearTheHandoffWithoutExecutingTransfer() throws Exception {
		var base = reviewed();
		var original = base.review().prepared();
		String draft = "-- 変更SQL: review before use\nCREATE TABLE ITEMS(ID INTEGER)\n;\n";
		var prepared = new TransferJobPreparationService.Prepared(directory, original.job(),
				original.sourceSchemaFile(), original.dataJob(), original.planFingerprint(), original.taskIds(),
				original.warnings(), original.schemaPreparation());
		var review = new DirectTransferDesktopService.Reviewed(
				new TransferJobExecutionService.Review(base.review().definitionFile(), prepared,
						base.review().sourceIdentity(), base.review().targetIdentity()),
				base.report(), draft);
		var ref = new AtomicReference<DirectTransferPanel>();
		var ready = new CountDownLatch(1);
		var saved = new CountDownLatch(1);
		var calls = new AtomicInteger();
		var opened = new AtomicReference<Path>();
		var file = directory.resolve("draft.sql");
		SwingUtilities.invokeAndWait(() -> {
			var panel = new DirectTransferPanel((action, request, from, to) -> {
				assertEquals(DirectTransferPanel.Action.REVIEW, action);
				calls.incrementAndGet();
				return review;
			}, ignored -> null);
			ref.set(panel);
			configure(panel);
			panel.setSqlEditor(path -> {
				opened.set(path);
				return true;
			});
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			assertEquals(draft, panel.schemaSql.editor.getText());
			onEnabled(panel.review, saved);
			panel.schemaDraft.saveTo(file);
			assertTrue(panel.isBusy());
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		assertEquals(draft, java.nio.file.Files.readString(file));
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			assertTrue(panel.execute.isEnabled());
			assertTrue(panel.schemaDraft.edit.isEnabled());
			panel.schemaDraft.edit.doClick(0);
			assertEquals(file.toAbsolutePath(), opened.get());
			panel.target.jdbcUrl.setText("jdbc:changed");
			assertFalse(panel.schemaDraft.save.isEnabled());
			assertFalse(panel.schemaDraft.edit.isEnabled());
		});
		assertEquals(1, calls.get());
	}

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
			assertTrue(java.util.stream.IntStream.range(0, panel.results.getTabCount())
					.anyMatch(index -> panel.results.getTitleAt(index).equals("Warnings (1)")));
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
			assertTrue(ref.get().summary.getText().startsWith("Transfer completed."));
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

	@Test
	void nameAdjustmentsAreHighlightedWithoutBlockingOrAutomaticallyExecutingReview() throws Exception {
		var base = reviewed();
		var original = base.review().prepared();
		var warnings = List
				.of(TransferSchemaChangeChecks.NAME_ADJUSTMENT + " PrimaryKey on ITEMS is generated as sqlapp_fixture");
		var prepared = new TransferJobPreparationService.Prepared(original.directory(), original.job(),
				original.sourceSchemaFile(), original.dataJob(), original.planFingerprint(), original.taskIds(),
				warnings,
				new TransferSchemaPreparation.Plan(HASH, original.schemaPreparation().statements(), warnings));
		var review = new DirectTransferDesktopService.Reviewed(
				new TransferJobExecutionService.Review(base.review().definitionFile(), prepared,
						base.review().sourceIdentity(), base.review().targetIdentity()),
				base.report());
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var ready = new CountDownLatch(1);
		var ref = new AtomicReference<DirectTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new DirectTransferPanel((action, request, from, to) -> {
				assertEquals(DirectTransferPanel.Action.REVIEW, action);
				calls.incrementAndGet();
				return review;
			}, ignored -> null);
			ref.set(panel);
			configure(panel);
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			assertTrue(panel.summary.getText().contains("Object-name adjustment warnings: 1"));
			assertTrue(panel.result.editor.getText().contains(TransferSchemaChangeChecks.NAME_ADJUSTMENT));
			assertTrue(panel.execute.isEnabled());
		});
		assertEquals(1, calls.get());
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
