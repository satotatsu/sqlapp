/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.schema.MigrationSqlEditorIO;

class SqlEditorPanelTest {
	@TempDir
	Path directory;

	@Test
	void editsSavesAndSelectsOnlyCleanSavedSqlWithSharedUndo() throws Exception {
		final Path file = Files.writeString(directory.resolve("1_customers.sql"), "-- 顧客\r\nSELECT 1;\r\n");
		final var selected = new AtomicReference<Path>();
		final var ref = new AtomicReference<SqlEditorPanel>();
		SwingUtilities.invokeAndWait(() -> ref.set(new SqlEditorPanel(new MigrationSqlEditorIO() {
			@Override
			public Snapshot read(Path path) throws java.io.IOException {
				assertFalse(SwingUtilities.isEventDispatchThread());
				return super.read(path);
			}

			@Override
			public Snapshot write(Path path, String text, String expected) throws java.io.IOException {
				assertFalse(SwingUtilities.isEventDispatchThread());
				return super.write(path, text, expected);
			}
		}, path -> {
			assertTrue(SwingUtilities.isEventDispatchThread());
			selected.set(path);
			return true;
		})));
		final var panel = ref.get();
		finish(panel, () -> assertTrue(panel.requestOpen(file)));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(panel.isDirty());
			assertTrue(panel.use.isEnabled());
			panel.navigation.query.setText("SELECT 1");
			panel.navigation.replacement.setText("SELECT 2");
			panel.navigation.replaceAllMatches();
			assertTrue(panel.isDirty());
			assertFalse(panel.use.isEnabled());
			panel.navigation.history.undo();
			assertFalse(panel.isDirty());
			panel.navigation.history.redo();
		});
		finish(panel, () -> panel.saveTo(file));
		assertTrue(Files.readString(file).contains("SELECT 2;\r\n"));
		finish(panel, panel::useSavedDirectory);
		assertEquals(directory, selected.get());
		SwingUtilities.invokeAndWait(() -> {
			panel.navigation.history.undo();
			assertTrue(panel.isDirty());
			assertFalse(panel.use.isEnabled());
			panel.navigation.history.redo();
			assertFalse(panel.isDirty());
		});
		Files.writeString(file, "external");
		finish(panel, panel::useSavedDirectory);
		SwingUtilities.invokeAndWait(() -> assertTrue(panel.status.getText().contains("outside the editor")));
		assertEquals("external", Files.readString(file));
	}

	@Test
	void externalSaveConflictRetainsUnsavedBufferAndDoesNotOverwrite() throws Exception {
		final Path file = Files.writeString(directory.resolve("1_customers.sql"), "SELECT 1;");
		final var ref = new AtomicReference<SqlEditorPanel>();
		SwingUtilities.invokeAndWait(() -> ref.set(new SqlEditorPanel(path -> true)));
		final var panel = ref.get();
		finish(panel, () -> panel.loadFrom(file));
		SwingUtilities.invokeAndWait(() -> panel.text.append(" -- local"));
		Files.writeString(file, "-- external");
		finish(panel, () -> panel.saveTo(file));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.isDirty());
			assertTrue(panel.save.isEnabled());
			assertTrue(panel.text.getText().contains("-- local"));
			assertTrue(panel.status.getText().contains("outside the editor"));
		});
		assertEquals("-- external", Files.readString(file));
	}

	@Test
	void lateOpenDoesNotReplaceNewerBufferAndBusyOpenIsRefused() throws Exception {
		final Path file = Files.writeString(directory.resolve("1_customers.sql"), "SELECT 1;");
		final var release = new CountDownLatch(1);
		final var ref = new AtomicReference<SqlEditorPanel>();
		final var complete = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SqlEditorPanel(new MigrationSqlEditorIO() {
				@Override
				public Snapshot read(Path path) throws java.io.IOException {
					try {
						if (!release.await(5, TimeUnit.SECONDS))
							throw new java.io.IOException("timeout");
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new java.io.IOException(exception);
					}
					return super.read(path);
				}
			}, path -> true);
			ref.set(panel);
			onFinished(panel, complete);
			assertTrue(panel.requestOpen(file));
			assertFalse(panel.requestOpen(file));
			assertFalse(panel.canDiscardChanges());
			panel.text.setText("newer buffer");
		});
		release.countDown();
		assertTrue(complete.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("newer buffer", ref.get().text.getText());
			assertTrue(ref.get().isDirty());
			assertFalse(ref.get().use.isEnabled());
		});
	}

	@Test
	void savingEarlierBufferDoesNotMarkNewerEditsAsSaved() throws Exception {
		final Path file = Files.writeString(directory.resolve("1_customers.sql"), "SELECT 1;");
		final var release = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var ref = new AtomicReference<SqlEditorPanel>();
		SwingUtilities.invokeAndWait(() -> ref.set(new SqlEditorPanel(new MigrationSqlEditorIO() {
			@Override
			public Snapshot write(Path path, String text, String expected) throws java.io.IOException {
				try {
					if (!release.await(5, TimeUnit.SECONDS))
						throw new java.io.IOException("timeout");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new java.io.IOException(exception);
				}
				return super.write(path, text, expected);
			}
		}, path -> true)));
		final var panel = ref.get();
		finish(panel, () -> panel.loadFrom(file));
		SwingUtilities.invokeAndWait(() -> {
			panel.text.append(" -- first");
			onFinished(panel, saved);
			panel.saveTo(file);
			panel.text.append(" -- newer");
		});
		release.countDown();
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		assertEquals("SELECT 1; -- first", Files.readString(file));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.text.getText().endsWith("-- newer"));
			assertTrue(panel.isDirty());
			assertFalse(panel.use.isEnabled());
			assertTrue(panel.status.getText().contains("Newer buffer edits remain unsaved"));
		});
	}

	@Test
	void controlSSavesThroughExistingIoAndRepeatedShortcutDuringSaveDoesNotWriteAgain() throws Exception {
		final Path file = Files.writeString(directory.resolve("1_customers.sql"), "SELECT 1;");
		final var writes = new java.util.concurrent.atomic.AtomicInteger();
		final var release = new CountDownLatch(1);
		final var saved = new CountDownLatch(1);
		final var ref = new AtomicReference<SqlEditorPanel>();
		SwingUtilities.invokeAndWait(() -> ref.set(new SqlEditorPanel(new MigrationSqlEditorIO() {
			@Override
			public Snapshot write(Path path, String text, String expected) throws java.io.IOException {
				writes.incrementAndGet();
				try {
					if (!release.await(5, TimeUnit.SECONDS))
						throw new java.io.IOException("timeout");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new java.io.IOException(exception);
				}
				return super.write(path, text, expected);
			}
		}, path -> true)));
		final var panel = ref.get();
		finish(panel, () -> panel.loadFrom(file));
		SwingUtilities.invokeAndWait(() -> {
			panel.text.append(" -- keyboard");
			onFinished(panel, saved);
			EditorFileShortcutsTest.invoke(panel.text, "control S");
			assertTrue(panel.isBusy());
			EditorFileShortcutsTest.invoke(panel.text, "control S");
		});
		release.countDown();
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		assertEquals(1, writes.get());
		assertEquals("SELECT 1; -- keyboard", Files.readString(file));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(panel.isDirty());
			EditorFileShortcutsTest.invoke(panel.text, "control S");
			assertFalse(panel.isBusy());
		});
		assertEquals(1, writes.get());
	}

	private static void finish(SqlEditorPanel panel, Runnable action) throws Exception {
		final var signal = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onFinished(panel, signal);
			action.run();
		});
		assertTrue(signal.await(5, TimeUnit.SECONDS));
	}

	@Test
	void reloadReadsExternalChangesResetsHistoryAndRetainsBufferOnReadFailure() throws Exception {
		final Path path = Files.writeString(directory.resolve("1_reload.sql"), "SELECT 1;");
		final var ref = new AtomicReference<SqlEditorPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SqlEditorPanel(ignored -> true);
			ref.set(panel);
			assertFalse(panel.reload.isEnabled());
			assertFalse(panel.reloadFromDisk());
		});
		final var panel = ref.get();
		finish(panel, () -> panel.loadFrom(path));
		SwingUtilities.invokeAndWait(() -> {
			panel.product.setSelectedItem("HSQL Database Engine");
			panel.version.setText("2.7");
		});
		finish(panel, panel::inspectBoundaries);
		SwingUtilities.invokeAndWait(() -> assertEquals(1, panel.splitPreview.parts.getRowCount()));
		Files.writeString(path, "-- external\r\nSELECT 2;");
		finish(panel, () -> {
			assertTrue(panel.reload.isEnabled());
			assertTrue(panel.reloadFromDisk());
			assertFalse(panel.reload.isEnabled());
			assertFalse(panel.reloadFromDisk());
		});
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("-- external\r\nSELECT 2;", panel.text.getText());
			assertFalse(panel.isDirty());
			assertFalse(panel.text.canUndo());
			assertEquals(0, panel.splitPreview.parts.getRowCount());
			assertEquals(0, panel.workspace.getSelectedIndex());
		});
		Files.delete(path);
		finish(panel, () -> assertTrue(panel.reloadFromDisk()));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("-- external\r\nSELECT 2;", panel.text.getText());
			assertTrue(panel.status.getText().startsWith("Operation failed:"));
			assertEquals(panel.status.getText(), panel.status.getToolTipText());
			assertTrue(panel.statusPanel.details.isEnabled());
			assertTrue(panel.reload.isEnabled());
			panel.fresh.doClick();
			assertFalse(panel.reload.isEnabled());
			assertEquals(panel.status.getText(), panel.status.getToolTipText());
			assertFalse(panel.status.getToolTipText().startsWith("Operation failed:"));
		});
		assertFalse(Files.exists(path));
	}

	private static void onFinished(SqlEditorPanel panel, CountDownLatch signal) {
		panel.open.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				signal.countDown();
		});
	}
}
