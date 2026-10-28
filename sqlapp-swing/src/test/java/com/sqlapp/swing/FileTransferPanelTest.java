/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;

class FileTransferPanelTest {
	@TempDir
	Path directory;
	private static final String HASH = "sha256:" + "a".repeat(64);

	@Test
	void exportRunsOnlyOnActionDisablesInputsWipesSecretsAndSelectsManifestWithoutImporting() throws Exception {
		final var finished = new CountDownLatch(1);
		final var secret = new AtomicReference<char[]>();
		final var calls = new AtomicInteger();
		final var reference = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				assertEquals(FileTransferPanel.Action.EXPORT, action);
				secret.set(password);
				calls.incrementAndGet();
				return exported();
			}, prepared -> {
				throw new AssertionError("No discovery requested");
			});
			reference.set(panel);
			assertEquals(0, calls.get());
			panel.source.jdbcUrl.setText("jdbc:source");
			panel.source.password.setText("secret");
			panel.job.setText("job.yaml");
			onEnabled(panel.export, finished);
			panel.export.doClick(0);
			assertTrue(panel.isBusy());
			assertFalse(panel.source.jdbcUrl.isEnabled());
			assertFalse(panel.job.isEnabled());
			assertEquals(0, panel.source.password.getPassword().length);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(1, calls.get());
		assertArrayEquals(new char[6], secret.get());
		SwingUtilities.invokeAndWait(() -> {
			final var panel = reference.get();
			assertEquals(exported().file().toString(), panel.manifest.getText());
			assertFalse(panel.apply.isEnabled());
			assertFalse(panel.result.editor.isEditable());
			assertTrue(panel.result.editor.getText().contains("manifest.json"));
			assertTrue(panel.result.editor.getLineCount() > 5);
			assertFalse(panel.isBusy());
			assertTrue(panel.status.getText().contains("Warnings: 1"));
			assertEquals(0, panel.resultTabs.getSelectedIndex());
			assertFalse(panel.summary.isEditable());
			assertTrue(panel.summary.getText().contains("No rows have been imported"));
			assertTrue(panel.summary.getText().contains("PUBLIC.ITEMS: 0 rows"));
			assertTrue(panel.summary.getText().contains("Test export warning"));
			assertTrue(panel.nextImport.isVisible());
			assertTrue(panel.nextImport.isEnabled());
			assertTrue(panel.resultTabs.indexOfComponent(panel.gradle) >= 0);
			assertFalse(panel.gradle.editor.isEditable());
			assertTrue(panel.gradle.editor.getText().contains("tasks.named('exportTransferFile')"));
			assertTrue(panel.gradle.editor.getText().contains("expectedManifestFingerprint.set('" + HASH + "')"));
			assertFalse(panel.gradle.editor.getText().contains("jdbc:source"));
			panel.editSettings(path -> {
				assertTrue(panel.isBusy());
				return null;
			});
			assertTrue(panel.nextImport.isEnabled());
			panel.editSettings(path -> new TransferJobConfigurationIO.Snapshot(path, "formatVersion: 1", HASH,
					new TransferJobConfiguration(1, "source", "target", List.of("ITEMS"),
							TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING, null, null, null, null, null,
							TransferJobConfiguration.DataMode.FILE, exported().manifest().files())));
			assertFalse(panel.nextImport.isEnabled());
			assertEquals(-1, panel.resultTabs.indexOfComponent(panel.gradle));
			panel.job.setText("another-job.yaml");
			assertTrue(panel.summary.getText().startsWith("Inputs changed."));
			assertEquals(-1, panel.resultTabs.indexOfComponent(panel.gradle));
			assertFalse(panel.nextImport.isVisible());
			assertFalse(panel.nextImport.isEnabled());
		});
	}

	@Test
	void packageInspectionNeedsNoUrlOrPasswordAndCannotAuthorizeImport() throws Exception {
		final var done = new CountDownLatch(1);
		final var ref = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				assertEquals(FileTransferPanel.Action.INSPECT, action);
				assertNull(spec);
				assertEquals(0, password.length);
				assertFalse(SwingUtilities.isEventDispatchThread());
				return new FileTransferDesktopService.Inspected(exported());
			}, prepared -> null);
			ref.set(panel);
			panel.step.setSelectedIndex(1);
			panel.manifest.setText(directory.toString());
			panel.target.password.setText("not-needed");
			onEnabled(panel.inspectPackage, done);
			panel.inspectPackage.doClick(0);
			assertTrue(panel.isBusy());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertFalse(panel.apply.isEnabled());
			assertEquals("", panel.target.jdbcUrl.getText());
			assertArrayEquals("not-needed".toCharArray(), panel.target.password.getPassword());
			assertTrue(panel.summary.getText().contains("No database connection was opened"));
			assertTrue(panel.summary.getText().contains("Test export warning"));
			assertTrue(panel.summary.getText().contains("have not been reviewed"));
			assertEquals(0, panel.resultTabs.getSelectedIndex());
			assertTrue(panel.gradle.editor.getText().contains("expectedManifestFingerprint"));
		});
	}

	@Test
	void optionalOfflineValueValidationShowsItsScopeAndSavedTargetSettings() throws Exception {
		final var done = new CountDownLatch(1);
		final var ref = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				assertEquals(FileTransferPanel.Action.INSPECT_DATA, action);
				assertNull(spec);
				assertEquals(0, password.length);
				final var configuration = new TransferJobConfiguration(1, "source", "target", List.of("PUBLIC.ITEMS"),
						TransferJobConfiguration.SchemaMode.CREATE_MISSING, "PUBLIC", "ARCHIVE", null, 100, false,
						TransferJobConfiguration.DataMode.FILE, exported().manifest().files());
				return new FileTransferDesktopService.Inspected(exported(), true, configuration);
			}, prepared -> null);
			ref.set(panel);
			panel.step.setSelectedIndex(1);
			panel.manifest.setText(directory.toString());
			assertFalse(panel.inspectValues.isSelected());
			panel.inspectValues.setSelected(true);
			assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class, panel.inspectPackage));
			assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class, panel.review));
			assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class, panel.apply));
			onEnabled(panel.inspectPackage, done);
			panel.inspectPackage.doClick(0);
			assertFalse(panel.inspectValues.isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertFalse(panel.apply.isEnabled());
			assertTrue(panel.summary.getText().contains("CSV values and recorded row counts checked"));
			assertTrue(panel.summary.getText().contains("Target compatibility has not been reviewed"));
			assertTrue(panel.summary.getText().contains("Saved target schema: ARCHIVE"));
			assertTrue(panel.summary.getText().contains("Verification after import: disabled"));
			panel.inspectValues.setSelected(false);
			assertEquals(-1, panel.resultTabs.indexOfComponent(panel.gradle));
			assertTrue(panel.summary.getText().startsWith("Inputs changed."));
		});
	}

	@Test
	void inspectionDiscardsAnEarlierTargetReview() throws Exception {
		final var ready = new CountDownLatch(1);
		final var ref = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema,
					prior) -> action == FileTransferPanel.Action.REVIEW ? reviewed()
							: new FileTransferDesktopService.Inspected(exported()),
					prepared -> null);
			ref.set(panel);
			configureImport(panel);
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertTrue(panel.apply.isEnabled());
			onEnabled(panel.inspectPackage, done);
			panel.inspectPackage.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> assertFalse(ref.get().apply.isEnabled()));
	}

	@Test
	void changedSelectionDiscardsBackgroundInspection() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var done = new CountDownLatch(1);
		final var ref = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				entered.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return new FileTransferDesktopService.Inspected(exported());
			}, prepared -> null);
			ref.set(panel);
			panel.step.setSelectedIndex(1);
			panel.manifest.setText("manifest.json");
			onEnabled(panel.inspectPackage, done);
			panel.inspectPackage.doClick(0);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> ref.get().manifest.setText("another.json"));
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(ref.get().apply.isEnabled());
			assertEquals(-1, ref.get().resultTabs.indexOfComponent(ref.get().gradle));
			assertTrue(ref.get().status.getText().contains("Inputs changed"));
		});
	}

	@Test
	void missingTargetSchemaDuringCsvReviewAppearsInSummaryAndCannotAuthorizeImport() throws Exception {
		final var source = new PlanningJdbc.Spec("jdbc:hsqldb:mem:csv_schema_source_" + java.util.UUID.randomUUID(),
				"SA", "");
		final var target = new PlanningJdbc.Spec("jdbc:hsqldb:mem:csv_schema_target_" + java.util.UUID.randomUUID(),
				"SA", "");
		try (var connection = java.sql.DriverManager.getConnection(source.jdbcUrl(), "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		}
		final var job = java.nio.file.Files.writeString(directory.resolve("missing-schema.yaml"),
				"formatVersion: 1\nsource: source\ntarget: target\ntables: [ITEMS]\nschemaMode: CREATE_MISSING\ntargetSchema: ARCHIVE\ndataMode: FILE\nfiles: {directory: packages, nullValue: __NULL__}\n");
		final var exported = (TransferFilePackageIO.Snapshot) new FileTransferDesktopService()
				.perform(FileTransferPanel.Action.EXPORT, job, null, source, new char[0], "", null);
		final var ready = new CountDownLatch(1);
		final var ref = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel();
			ref.set(panel);
			panel.step.setSelectedIndex(1);
			panel.target.jdbcUrl.setText(target.jdbcUrl());
			panel.target.username.setText("SA");
			panel.manifest.setText(exported.file().getParent().toString());
			onEnabled(panel.review, ready);
			panel.review.doClick(0);
		});
		assertTrue(ready.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertFalse(panel.apply.isEnabled());
			assertEquals(0, panel.resultTabs.getSelectedIndex());
			assertTrue(panel.summary.getText().contains("Target schema 'ARCHIVE'"), panel.summary.getText());
			assertTrue(panel.summary.getText().contains("'PUBLIC'"));
			assertFalse(panel.summary.getText().contains("jdbc:"));
		});
	}

	@Test
	void droppingPackageFolderSelectsItAndInvalidatesReviewWithoutCallingBackend() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				throw new AssertionError("Selecting a package must not connect");
			}, prepared -> null);
			panel.step.setSelectedIndex(1);
			assertTrue(FileDropHandlerTest.drop(panel.manifest, directory));
			assertEquals(directory.toString(), panel.manifest.getText());
			assertFalse(panel.apply.isEnabled());
		});
	}

	@Test
	void changedInputsDuringExportCannotPublishStaleGradleInstructions() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var reference = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				entered.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return exported();
			}, prepared -> null);
			reference.set(panel);
			panel.source.jdbcUrl.setText("jdbc:source");
			panel.job.setText("old-job.yaml");
			onEnabled(panel.export, finished);
			panel.export.doClick(0);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> reference.get().job.setText("new-job.yaml"));
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("", reference.get().manifest.getText());
			assertEquals(-1, reference.get().resultTabs.indexOfComponent(reference.get().gradle));
			assertEquals("", reference.get().gradle.editor.getText());
			assertFalse(reference.get().nextImport.isEnabled());
		});
	}

	@Test
	void exportedPackageHandoffSelectsTargetWithoutConnectingOrCopyingSourceCredentials() throws Exception {
		final var finished = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var reference = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				assertEquals(FileTransferPanel.Action.EXPORT, action);
				calls.incrementAndGet();
				return exported();
			}, prepared -> null);
			reference.set(panel);
			assertFalse(panel.nextImport.isVisible());
			panel.source.jdbcUrl.setText("jdbc:source");
			panel.source.username.setText("source-user");
			panel.source.password.setText("source-secret");
			panel.target.jdbcUrl.setText("jdbc:target");
			panel.target.username.setText("target-user");
			panel.target.password.setText("target-secret");
			panel.job.setText("transfer.yaml");
			onEnabled(panel.export, finished);
			panel.export.doClick(0);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = reference.get();
			panel.nextImport.doClick(0);
			assertEquals(1, panel.step.getSelectedIndex());
			assertEquals(exported().file().toString(), panel.manifest.getText());
			assertEquals("jdbc:target", panel.target.jdbcUrl.getText());
			assertEquals("target-user", panel.target.username.getText());
			assertArrayEquals("target-secret".toCharArray(), panel.target.password.getPassword());
			assertFalse(panel.apply.isEnabled());
			assertFalse(panel.nextImport.isVisible());
			assertTrue(panel.summary.getText().startsWith("Ready for target review"));
			assertTrue(panel.summary.getText().contains("target-only import settings"));
			assertFalse(panel.summary.getText().contains("optional re-export"));
			assertTrue(panel.gradle.editor.getText().contains("TARGET_JDBC_URL"));
			assertFalse(panel.gradle.editor.getText().contains("SOURCE_JDBC"));
		});
		assertEquals(1, calls.get());
	}

	@Test
	void importRequiresReviewConfirmationAndUnchangedInputsAndCannotReuseConsumedReview() throws Exception {
		final var done = new CountDownLatch(1);
		final var calls = new AtomicInteger();
		final var reference = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				assertFalse(SwingUtilities.isEventDispatchThread());
				if (action == FileTransferPanel.Action.REVIEW)
					return reviewed();
				assertEquals(FileTransferPanel.Action.IMPORT, action);
				calls.incrementAndGet();
				assertNotNull(prior);
				return new TransferFileImportService.Outcome(HASH, HASH, TransferJobExecutionService.Phase.COMPLETE,
						null, true, Map.of(), Map.of(), null, directory.resolve("import-result.json"));
			}, prepared -> null);
			reference.set(panel);
			configureImport(panel);
			assertFalse(panel.apply.isEnabled());
			panel.importReviewed(() -> {
				throw new AssertionError("Unreviewed import must not confirm");
			});
			onEnabled(panel.review, done);
			panel.review.doClick(0);
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = reference.get();
			assertTrue(panel.apply.isEnabled());
			panel.importReviewed(() -> false);
			assertTrue(panel.gradle.editor.getText().contains("TARGET_JDBC_URL"));
			assertFalse(panel.gradle.editor.getText().contains("SOURCE_JDBC"));
			assertEquals(0, calls.get());
			assertTrue(panel.apply.isEnabled());
			panel.importReviewed(() -> {
				assertTrue(panel.isBusy());
				panel.target.username.setText("changed");
				return true;
			});
			assertEquals(0, calls.get());
			assertFalse(panel.apply.isEnabled());
			assertEquals(-1, panel.resultTabs.indexOfComponent(panel.gradle));
		});
		final var reviewedAgain = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(reference.get().review, reviewedAgain);
			reference.get().review.doClick(0);
		});
		assertTrue(reviewedAgain.await(5, TimeUnit.SECONDS));
		final var imported = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = reference.get();
			panel.status.addPropertyChangeListener("text", event -> {
				if (event.getNewValue().toString().startsWith("CSV import finished"))
					imported.countDown();
			});
			panel.importReviewed(() -> true);
			assertFalse(panel.apply.isEnabled());
			assertEquals(-1, panel.resultTabs.indexOfComponent(panel.gradle));
		});
		assertTrue(imported.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(reference.get().apply.isEnabled());
			assertTrue(reference.get().result.editor.getText().contains("COMPLETE"),
					reference.get().statusPanel.detailsText() + reference.get().result.editor.getText());
		});
		assertEquals(1, calls.get());
	}

	@Test
	void staleReviewCannotEnableImportOrReplaceCurrentResult() throws Exception {
		final var entered = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var reference = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				entered.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return reviewed();
			}, prepared -> null);
			reference.set(panel);
			configureImport(panel);
			onEnabled(panel.review, finished);
			panel.review.doClick(0);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> reference.get().manifest.setText("other.json"));
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(reference.get().apply.isEnabled());
			assertTrue(reference.get().status.getText().contains("Inputs changed"));
		});
	}

	@Test
	void discoveryHandsOffToExistingPickerAndSelectingSavedJobNeverExportsAutomatically() throws Exception {
		final var finished = new CountDownLatch(1);
		final var reference = new AtomicReference<FileTransferPanel>();
		final var calls = new AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				assertEquals(FileTransferPanel.Action.DISCOVER, action);
				calls.incrementAndGet();
				return new DataMigrationDraftService.Prepared(directory, null);
			}, prepared -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				return directory.resolve("transfer.yaml");
			});
			reference.set(panel);
			panel.source.jdbcUrl.setText("jdbc:source");
			onEnabled(panel.create, finished);
			panel.discover(directory);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(
				() -> assertEquals(directory.resolve("transfer.yaml").toString(), reference.get().job.getText()));
		assertEquals(1, calls.get());
	}

	@Test
	void reviewedSchemaSqlIsReadOnlyAndRemovedWhenInputsChange() throws Exception {
		final var finished = new CountDownLatch(1);
		final var reference = new AtomicReference<FileTransferPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new FileTransferPanel((action, input, workspace, spec, password, schema, prior) -> {
				final var original = reviewed();
				return new FileTransferDesktopService.Reviewed(new TransferFileImportService.Review(
						original.review().manifestFile(), HASH, original.review().targetIdentity(), HASH,
						new TransferSchemaPreparation.Plan(HASH, List.of(
								new TransferSchemaPreparation.Statement("task", "CREATE TABLE ITEMS(ID INTEGER)")))),
						original.manifest());
			}, prepared -> null);
			reference.set(panel);
			configureImport(panel);
			onEnabled(panel.review, finished);
			panel.review.doClick(0);
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = reference.get();
			assertTrue(panel.apply.isEnabled());
			assertEquals(0, panel.resultTabs.getSelectedIndex());
			assertTrue(panel.summary.getText().contains("Missing-table DDL statements: 1"));
			assertTrue(panel.summary.getText().contains("No tables or rows have been created"));
			assertFalse(panel.schemaSql.editor.isEditable());
			assertTrue(panel.schemaSql.editor.getText().contains("CREATE TABLE ITEMS"));
			panel.manifest.setText("changed.json");
			assertFalse(panel.apply.isEnabled());
			assertEquals(-1, panel.resultTabs.indexOfComponent(panel.schemaSql));
			assertEquals("", panel.schemaSql.editor.getText());
		});
	}

	private void configureImport(FileTransferPanel panel) {
		panel.step.setSelectedIndex(1);
		panel.target.jdbcUrl.setText("jdbc:target");
		panel.target.password.setText("secret");
		panel.manifest.setText(directory.resolve("manifest.json").toString());
		panel.workspace.setText(directory.toString());
	}

	private TransferFilePackageIO.Snapshot exported() {
		final var manifest = new TransferFilePackageIO.Manifest(1, HASH, HASH,
				new TransferJobConfiguration.FileTransfer("packages", null, null, "__NULL__"),
				List.of(new TransferFilePackageIO.TableFile("task", null, "PUBLIC", "ITEMS", List.of("ID"),
						"table-1.csv", 0, 0, HASH)));
		return new TransferFilePackageIO.Snapshot(directory.resolve("manifest.json"), HASH,
				new TransferFilePackageIO.Manifest(manifest.formatVersion(), manifest.jobFingerprint(),
						manifest.schemaFingerprint(), manifest.files(), manifest.tables(),
						List.of(new TransferFilePackageIO.Warning("task", "ID", "Test export warning"))));
	}

	private FileTransferDesktopService.Reviewed reviewed() {
		return new FileTransferDesktopService.Reviewed(new TransferFileImportService.Review(exported().file(), HASH,
				new DatabaseIdentity("HSQL", "2.7", HASH), HASH), exported().manifest());
	}

	private static void onEnabled(JButton button, CountDownLatch done) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}
}
