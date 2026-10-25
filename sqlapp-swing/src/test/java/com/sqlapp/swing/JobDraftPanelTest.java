/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class JobDraftPanelTest {
	@TempDir
	Path directory;

	@Test
	void inspectsEligibilityAndCreatesUnsavedDraftOffEdtWithSelectedOptions() throws Exception {
		final var schema = new Schema("PUBLIC");
		final var table = new Table("ITEMS");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.setPrimaryKey("PK_ITEMS", table.getColumns().get("ID"));
		schema.getTables().add(table);
		schema.getTables().add(new Table("NO_KEY"));
		final Path file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		final var panel = new AtomicReference<JobDraftPanel>();
		final var result = new AtomicReference<String>();
		final var inspected = new CountDownLatch(1);
		final var created = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobDraftPanel(new BulkMigrationJobDraftBuilder() {
				@Override
				public SchemaSnapshot inspect(Path file) throws java.io.IOException {
					assertFalse(SwingUtilities.isEventDispatchThread());
					return super.inspect(file);
				}

				@Override
				public String draft(SchemaSnapshot schema, java.util.List<String> selected, String job, int chunk,
						boolean verify) throws java.io.IOException {
					assertFalse(SwingUtilities.isEventDispatchThread());
					return super.draft(schema, selected, job, chunk, verify);
				}
			}, yaml -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				result.set(yaml);
			});
			panel.set(view);
			view.schemaFile.setText(file.toString());
			onFinished(view, inspected);
			view.inspectSchema();
			assertFalse(view.schemaFile.isEnabled());
		});
		assertTrue(inspected.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var view = panel.get();
			assertEquals(2, view.model.getRowCount());
			assertFalse(view.create.isEnabled());
			assertFalse(view.model.isCellEditable(1, 0));
			assertTrue(view.fingerprint.getText().startsWith("sha256:"));
			view.select.doClick();
			assertTrue(view.create.isEnabled());
			view.jobId.setText("gui-copy");
			view.chunkSize.setValue(250);
			view.verify.setSelected(false);
			onFinished(view, created);
			view.createDraft();
		});
		assertTrue(created.await(5, TimeUnit.SECONDS));
		final var configuration = new BulkMigrationJobConfigurationEditorIO().validate(result.get());
		assertEquals(1, configuration.getTasks().size());
		assertEquals(250, configuration.getTasks().getFirst().getChunkSize());
		assertEquals("gui-copy", configuration.getJobId());
		assertNull(configuration.getVerification());
		SwingUtilities.invokeAndWait(() -> {
			final var editor = new JobConfigurationEditorPanel(snapshot -> fail("Draft must be saved first"));
			editor.acceptDraft(result.get());
			assertTrue(editor.isDirty());
			assertFalse(editor.save.isEnabled());
			assertTrue(editor.saveAs.isEnabled());
			assertFalse(editor.use.isEnabled());
		});
	}

	@Test
	void staleSchemaInspectionCannotReplaceNewerPathOrEnableCreation() throws Exception {
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		final var panel = new AtomicReference<JobDraftPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var view = new JobDraftPanel(new BulkMigrationJobDraftBuilder() {
				@Override
				public SchemaSnapshot inspect(Path file) {
					try {
						if (!release.await(5, TimeUnit.SECONDS))
							throw new IllegalStateException("Timeout");
					} catch (InterruptedException failure) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(failure);
					}
					return new SchemaSnapshot(file, "sha256:" + "a".repeat(64), java.util.List.of());
				}
			}, yaml -> fail("No inspected selection"));
			panel.set(view);
			view.schemaFile.setText("old.xml");
			onFinished(view, finished);
			view.inspectSchema();
			view.schemaFile.setText("new.xml");
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("new.xml", panel.get().schemaFile.getText());
			assertEquals("", panel.get().fingerprint.getText());
			assertFalse(panel.get().create.isEnabled());
			assertEquals(0, panel.get().model.getRowCount());
		});
	}

	private static void onFinished(JobDraftPanel view, CountDownLatch done) {
		view.inspect.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				done.countDown();
		});
	}

	@Test
	void schemaReadFailureProvidesCompleteCauseAndInputChangesClearOldDetails() throws Exception {
		final var ref = new AtomicReference<JobDraftPanel>();
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new JobDraftPanel(new BulkMigrationJobDraftBuilder() {
				@Override
				public SchemaSnapshot inspect(Path path) throws java.io.IOException {
					throw new java.io.IOException("Schema read failed",
							new IllegalArgumentException("Invalid XML header"));
				}
			}, ignored -> fail("No draft from a failed Schema read"));
			ref.set(panel);
			panel.schemaFile.setText("invalid.xml");
			onFinished(panel, finished);
			panel.inspectSchema();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertFalse(panel.create.isEnabled());
			assertTrue(panel.statusPanel.detailsText().contains("Invalid XML header"));
			panel.schemaFile.setText("new.xml");
			assertEquals(panel.status.getText(), panel.statusPanel.detailsText());
		});
	}
}
