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
			assertFalse(panel.isBusy());
			assertTrue(panel.status.getText().contains("Warnings: 1"));
		});
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
			assertEquals(0, calls.get());
			assertTrue(panel.apply.isEnabled());
			panel.importReviewed(() -> {
				assertTrue(panel.isBusy());
				panel.target.username.setText("changed");
				return true;
			});
			assertEquals(0, calls.get());
			assertFalse(panel.apply.isEnabled());
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
			assertSame(panel.schemaSql, panel.resultTabs.getSelectedComponent());
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
