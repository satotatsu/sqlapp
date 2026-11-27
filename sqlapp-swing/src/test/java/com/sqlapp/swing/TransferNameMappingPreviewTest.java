/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;

class TransferNameMappingPreviewTest {
	@TempDir
	Path directory;

	@Test
	void previewsExactlyTheSavedFormatIncludingHiddenOverridesAndCommitsTheCurrentCell() throws Exception {
		var yaml = new AtomicReference<String>();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "PUBLIC.注文", "履歴" });
			panel.columns.addRow(new String[] { "PUBLIC.注文", "名前", "顧客" });
			panel.columns.addRow(new String[] { "PUBLIC.注文", "数量", "数量" });
			assertTrue(panel.columnGrid.editCellAt(0, 2));
			((JTextField) panel.columnGrid.getEditorComponent()).setText("顧客名");
			panel.columnTools.filter.setText("hidden");
			panel.previewYaml.doClick(0);
			assertEquals(3, panel.tabs.getSelectedIndex());
			assertFalse(panel.preview.text.isEditable());
			assertEquals(SyntaxConstants.SYNTAX_STYLE_YAML, panel.preview.text.getSyntaxEditingStyle());
			assertEquals(panel.formattedMapping(), panel.preview.text.getText());
			yaml.set(panel.preview.text.getText());
			var mapped = new TransferNameMappingIO().validate(yaml.get()).tables().get("PUBLIC.注文");
			assertEquals(java.util.Map.of("名前", "顧客名"), mapped.columns());
			panel.preview.navigation.query.setText("顧客名");
			panel.preview.navigation.next.doClick(0);
			assertEquals("顧客名", panel.preview.text.getSelectedText());
		});
		var io = new TransferNameMappingIO();
		var written = io.write(directory.resolve("names.yaml"), yaml.get(), null);
		assertEquals(yaml.get(), written.text());
	}

	@Test
	void editingInvalidatesTheSnapshotAndRefreshUsesTheNewValues() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "OLD" });
			panel.previewYaml.doClick(0);
			assertTrue(panel.preview.copy.isEnabled());
			panel.tables.setValueAt("NEW", 0, 1);
			assertEquals("", panel.preview.text.getText());
			assertFalse(panel.preview.copy.isEnabled());
			assertTrue(panel.preview.status.getText().contains("Names changed"));
			panel.preview.refresh.doClick(0);
			assertEquals("NEW",
					new TransferNameMappingIO().validate(panel.preview.text.getText()).tables().get("A").targetTable());
			panel.preview.text.undoLastAction();
			assertEquals(panel.formattedMapping(), panel.preview.text.getText());
		});
	}

	@Test
	void invalidInputOpensChecksAndNeverLeavesAValidOldPreviewAvailable() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "VALID" });
			panel.previewYaml.doClick(0);
			panel.tables.setValueAt("schema.table", 0, 1);
			panel.previewYaml.doClick(0);
			assertEquals(2, panel.tabs.getSelectedIndex());
			assertEquals(1, panel.issues.size());
			assertEquals("", panel.preview.text.getText());
			assertFalse(panel.preview.copy.isEnabled());
		});
	}

	@Test
	void copyFailureAllowsRetryButInvalidatedContentCannotBeCopied() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var attempts = new AtomicInteger();
			var copied = new AtomicReference<String>();
			var preview = new TransferNameMappingPreview(text -> {
				if (attempts.getAndIncrement() == 0)
					throw new IllegalStateException("busy");
				copied.set(text);
			});
			String yaml = "formatVersion: 1\ntables: {}\n";
			preview.showSnapshot(yaml);
			preview.copy.doClick(0);
			assertNull(copied.get());
			assertTrue(preview.status.getText().startsWith("Copy failed"));
			preview.copy.doClick(0);
			assertEquals(yaml, copied.get());
			preview.changed();
			preview.copy.doClick(0);
			assertEquals(2, attempts.get());
			assertFalse(preview.copy.isEnabled());
			assertEquals("", preview.text.getText());
		});
	}

	@Test
	void busyAndEmptyStatesCannotCopyAndRestoringControlsDoesNotInventASnapshot() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var copied = new AtomicReference<String>();
			var preview = new TransferNameMappingPreview(copied::set);
			preview.refreshEnabled(true);
			assertFalse(preview.copy.isEnabled());
			preview.showSnapshot("yaml");
			preview.refreshEnabled(false);
			preview.copy.doClick(0);
			assertNull(copied.get());
			assertFalse(preview.navigation.query.isEnabled());
			preview.refreshEnabled(true);
			preview.copy.doClick(0);
			assertEquals("yaml", copied.get());
			preview.invalidate("Refresh needed");
			preview.refreshEnabled(false);
			preview.refreshEnabled(true);
			assertFalse(preview.copy.isEnabled());
			assertTrue(preview.refresh.isEnabled());
		});
	}
}
