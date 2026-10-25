/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.migration.schema.MigrationSqlEditorIO;
import com.sqlapp.data.db.command.migration.schema.MigrationSqlInspection;

class SqlBoundaryPreviewTest {
	@Test
	void inspectsUnsavedSqlOffEdtShowsSelectedPartAndInvalidatesOnTextOrDialectChanges() throws Exception {
		final var ref = new AtomicReference<SqlEditorPanel>();
		final var finished = new CountDownLatch(1);
		final String source = "-- comment\nSELECT ';';\nSELECT 2;";
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SqlEditorPanel(new MigrationSqlEditorIO(), path -> false,
					(text, product, version) -> {
						assertFalse(SwingUtilities.isEventDispatchThread());
						return new MigrationSqlInspection().inspect(text, product, version);
					});
			ref.set(panel);
			panel.text.setText(source);
			panel.product.setSelectedItem("HSQL Database Engine");
			panel.version.setText("2.7");
			onFinished(panel, finished);
			panel.inspect.doClick();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			final var panel = ref.get();
			assertEquals(source, panel.text.getText());
			assertTrue(panel.isDirty());
			assertFalse(panel.use.isEnabled());
			assertSame(panel.splitPreview, panel.workspace.getSelectedComponent());
			assertFalse(panel.position.isVisible());
			panel.navigation.query.setText("SELECT");
			panel.navigation.count.doClick(0);
			assertEquals("2 matches (non-overlapping)", panel.navigation.status.getText());
			assertSame(panel.splitPreview, panel.workspace.getSelectedComponent());
			assertEquals(3, panel.splitPreview.parts.getRowCount());
			panel.splitPreview.parts.setRowSelectionInterval(2, 2);
			assertTrue(panel.splitPreview.sql.getText().contains("SELECT 2"));
			assertFalse(panel.splitPreview.sql.isEditable());
			assertTrue(panel.splitPreview.summary.getText().contains("SQL statements / batches: 2"));
			panel.splitPreview.findSource.doClick();
			assertTrue(panel.position.isVisible());
			assertTrue(panel.position.getText().contains("Selected"));
			assertEquals(0, panel.workspace.getSelectedIndex());
			assertEquals(panel.splitPreview.sql.getText(), panel.text.getSelectedText());
			assertEquals(source, panel.text.getText());
			panel.navigation.query.setText("SELECT 2");
			panel.navigation.next.doClick();
			assertEquals(0, panel.workspace.getSelectedIndex());
			assertEquals("SELECT 2", panel.text.getSelectedText());
			panel.version.setText("2.6");
			assertTrue(panel.status.getText().contains("again"));
			assertEquals(0, panel.splitPreview.parts.getRowCount());
			panel.text.append("\nSELECT 3;");
			assertTrue(panel.splitPreview.sql.getText().isEmpty());
		});
	}

	@Test
	void missingDialectFailsWithoutTreatingHighlightingAsValidation() throws Exception {
		final var ref = new AtomicReference<SqlEditorPanel>();
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SqlEditorPanel(path -> false);
			ref.set(panel);
			panel.text.setText("SELECT 1;");
			onFinished(panel, finished);
			panel.inspect.doClick();
		});
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(ref.get().status.getText().contains("Select a database product"));
			assertEquals("SELECT 1;", ref.get().text.getText());
			assertEquals(0, ref.get().splitPreview.parts.getRowCount());
			assertTrue(ref.get().text.isEditable());
		});
	}

	@Test
	void discardsLateBoundaryPreviewAfterProgrammaticBufferChange() throws Exception {
		final var ref = new AtomicReference<SqlEditorPanel>();
		final var release = new CountDownLatch(1);
		final var finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SqlEditorPanel(new MigrationSqlEditorIO(), path -> false,
					(text, product, version) -> {
						try {
							if (!release.await(5, TimeUnit.SECONDS))
								throw new IllegalStateException("timeout");
						} catch (InterruptedException exception) {
							Thread.currentThread().interrupt();
							throw new IllegalStateException(exception);
						}
						return new MigrationSqlInspection.Result("HSQL", "2.7", "Hsql",
								List.of(new MigrationSqlInspection.Part(1, false, "old")));
					});
			ref.set(panel);
			panel.text.setText("old");
			onFinished(panel, finished);
			panel.inspect.doClick();
			final String busyMessage = panel.status.getText();
			panel.findPartInSource("old");
			assertNull(panel.text.getSelectedText());
			assertEquals(busyMessage, panel.status.getText());
			panel.text.setText("newer");
		});
		release.countDown();
		assertTrue(finished.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("newer", ref.get().text.getText());
			assertEquals(0, ref.get().splitPreview.parts.getRowCount());
			assertTrue(ref.get().status.getText().contains("changed"));
		});
	}

	private static void onFinished(SqlEditorPanel panel, CountDownLatch signal) {
		panel.open.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				signal.countDown();
		});
	}

	@Test
	void sourceLookupPreservesMultilineTextHistoryAndSearchSettingsAndRejectsAmbiguousOrMissingText() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SqlEditorPanel(path -> fail("Source lookup must not select a plan"));
			final String source = "-- 顧客\r\nSELECT '[a].*',\r\n  'value';\r\nSELECT 2; SELECT 2;";
			panel.text.setText(source);
			panel.navigation.query.setText("existing search");
			panel.navigation.matchCase.setSelected(false);
			final boolean undo = panel.text.canUndo();
			panel.workspace.setSelectedIndex(1);
			panel.findPartInSource("SELECT '[a].*',\r\n  'value'");
			assertEquals("SELECT '[a].*',\r\n  'value'", panel.text.getSelectedText());
			assertEquals(0, panel.workspace.getSelectedIndex());
			assertEquals("existing search", panel.navigation.query.getText());
			assertFalse(panel.navigation.matchCase.isSelected());
			final int start = panel.text.getSelectionStart(), end = panel.text.getSelectionEnd();
			panel.workspace.setSelectedIndex(1);
			panel.findPartInSource("SELECT 2");
			assertTrue(panel.status.getText().contains("multiple places"));
			assertEquals(1, panel.workspace.getSelectedIndex());
			panel.findPartInSource("select '[a].*'");
			assertTrue(panel.status.getText().contains("no exact match"));
			assertEquals(start, panel.text.getSelectionStart());
			assertEquals(end, panel.text.getSelectionEnd());
			assertEquals(source, panel.text.getText());
			assertEquals(undo, panel.text.canUndo());
			assertTrue(panel.isDirty());
		});
	}
}
