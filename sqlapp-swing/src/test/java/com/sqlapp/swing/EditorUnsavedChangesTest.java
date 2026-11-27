/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.schema.MigrationSqlEditorIO;

class EditorUnsavedChangesTest {
	@TempDir
	Path directory;

	@Test
	void onlyDiscardAllowsContinuationAndCancelledOrDisabledSaveDoesNotRun() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var writes = new AtomicInteger();
			final var save = new JButton();
			save.addActionListener(event -> writes.incrementAndGet());
			assertFalse(EditorUnsavedChanges.resolve(EditorUnsavedChanges.Choice.CANCEL, save));
			assertFalse(EditorUnsavedChanges.resolve(null, save));
			assertTrue(EditorUnsavedChanges.resolve(EditorUnsavedChanges.Choice.DISCARD, save));
			assertEquals(0, writes.get());
			assertFalse(EditorUnsavedChanges.resolve(EditorUnsavedChanges.Choice.SAVE_AND_STAY, save));
			assertEquals(1, writes.get());
			save.setEnabled(false);
			assertFalse(EditorUnsavedChanges.resolve(EditorUnsavedChanges.Choice.SAVE_AND_STAY, save));
			assertEquals(1, writes.get());
		});
	}

	@Test
	void savingSqlKeepsTheDocumentAndNewerEditsInsteadOfContinuingThePendingAction() throws Exception {
		final Path file = Files.writeString(directory.resolve("1_keep.sql"), "SELECT 1;");
		final var release = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var ref = new AtomicReference<SqlEditorPanel>();
		SwingUtilities.invokeAndWait(() -> ref.set(new SqlEditorPanel(new MigrationSqlEditorIO() {
			@Override
			public Snapshot write(Path file, String text, String expected) throws java.io.IOException {
				try {
					if (!release.await(5, TimeUnit.SECONDS))
						throw new java.io.IOException("timeout");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new java.io.IOException(exception);
				}
				return super.write(file, text, expected);
			}
		}, ignored -> fail("Saving must not select a plan"))));
		final var panel = ref.get();
		finish(panel, () -> panel.loadFrom(file));
		SwingUtilities.invokeAndWait(() -> {
			panel.text.append(" -- saved");
			onFinished(panel, saved);
			assertFalse(EditorUnsavedChanges.resolve(EditorUnsavedChanges.Choice.SAVE_AND_STAY, panel.save));
			assertTrue(panel.isBusy());
			assertFalse(panel.canDiscardChanges());
			panel.text.append(" -- newer");
		});
		release.countDown();
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		assertEquals("SELECT 1; -- saved", Files.readString(file));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("SELECT 1; -- saved -- newer", panel.text.getText());
			assertTrue(panel.isDirty());
			assertTrue(panel.file.getText().contains("1_keep.sql"));
		});
	}

	@Test
	void failedSaveRetainsOriginalFileAndUnsavedSql() throws Exception {
		final Path file = Files.writeString(directory.resolve("1_keep.sql"), "SELECT 1;");
		final var ref = new AtomicReference<SqlEditorPanel>();
		SwingUtilities.invokeAndWait(() -> ref.set(new SqlEditorPanel(ignored -> true)));
		final var panel = ref.get();
		finish(panel, () -> panel.loadFrom(file));
		SwingUtilities.invokeAndWait(() -> panel.text.append(" -- local"));
		Files.writeString(file, "-- external");
		finish(panel,
				() -> assertFalse(EditorUnsavedChanges.resolve(EditorUnsavedChanges.Choice.SAVE_AND_STAY, panel.save)));
		assertEquals("-- external", Files.readString(file));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("SELECT 1; -- local", panel.text.getText());
			assertTrue(panel.isDirty());
			assertTrue(panel.status.getText().startsWith("Operation failed:"));
		});
	}

	private static void finish(SqlEditorPanel panel, Runnable action) throws Exception {
		final var complete = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onFinished(panel, complete);
			action.run();
		});
		assertTrue(complete.await(5, TimeUnit.SECONDS));
	}

	@Test
	void invalidYamlSaveKeepsTheJobOpenAndDoesNotBypassValidation() throws Exception {
		final String valid = "schemaFile: schema.xml\ntasks: []\n";
		final Path file = Files.writeString(directory.resolve("job.yaml"), valid);
		final var ref = new AtomicReference<JobConfigurationEditorPanel>();
		final var opened = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new JobConfigurationEditorPanel(ignored -> fail("Save must not select a plan"));
			ref.set(panel);
			panel.open.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					opened.countDown();
			});
			panel.loadFrom(file);
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		final String invalid = "schemaFile: schema.xml\ntasks: [\n";
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			panel.text.setText(invalid);
			panel.open.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					finished.countDown();
			});
			assertFalse(EditorUnsavedChanges.resolve(EditorUnsavedChanges.Choice.SAVE_AND_STAY, panel.save));
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		assertEquals(valid, Files.readString(file));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertEquals(invalid, panel.text.getText());
			assertTrue(panel.isDirty());
			assertFalse(panel.use.isEnabled());
			assertTrue(panel.status.getText().startsWith("Operation failed:"));
		});
	}

	private static void onFinished(SqlEditorPanel panel, CountDownLatch complete) {
		panel.open.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				complete.countDown();
		});
	}
}
