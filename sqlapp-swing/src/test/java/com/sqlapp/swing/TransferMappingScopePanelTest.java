/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class TransferMappingScopePanelTest {
	@TempDir
	Path directory;

	private Path schema() throws Exception {
		var schema = new Schema("PUBLIC");
		for (var name : List.of("KEEP", "OLD")) {
			var table = new Table(name);
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			table.getColumns().add(new Column("NOTE").setDataType(DataType.VARCHAR));
			schema.getTables().add(table);
		}
		var file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		return file;
	}

	private TransferNameMappingIO.Configuration mapping() {
		return new TransferNameMappingIO().validate(
				"formatVersion: 1\ntables:\n  PUBLIC.KEEP: {targetTable: CURRENT, columns: {ID: KEY_ID}}\n  OLD: {targetTable: LEGACY, columns: {NOTE: LEGACY_NOTE}}\n");
	}

	@Test
	void removesExcludedTableAndColumnRowsEvenWhenFilteredButPreservesRetainedTypes() throws Exception {
		var mapping = mapping();
		var review = new TransferNameMappingScopeService().review(schema(), List.of("KEEP"), mapping);
		SwingUtilities.invokeAndWait(() -> {
			var editor = new TransferNameMappingPanel();
			editor.load(mapping);
			editor.includeColumns(Map.of("PUBLIC.KEEP",
					List.of(new com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SourceColumn(
							"ID", "INT")),
					"OLD",
					List.of(new com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SourceColumn(
							"ID", "INT"))));
			editor.columnTools.filter.setText("KEY_ID");
			editor.columnGrid.getRowSorter().toggleSortOrder(1);
			editor.previewYaml.doClick(0);
			editor.removeExcludedMappings(review);
			assertEquals(review.retained(), editor.configuration());
			assertEquals(1, editor.tables.getRowCount());
			assertEquals(1, editor.columns.getRowCount());
			assertEquals("INT", editor.columns.getValueAt(0, 3));
			assertFalse(editor.preview.copy.isEnabled());
			assertEquals("", editor.preview.text.getText());
		});
	}

	@Test
	void changesAfterReviewRejectTheWholeRemovalBeforeAnyRowsDisappear() throws Exception {
		var mapping = mapping();
		var review = new TransferNameMappingScopeService().review(schema(), List.of("KEEP"), mapping);
		SwingUtilities.invokeAndWait(() -> {
			var editor = new TransferNameMappingPanel();
			editor.load(mapping);
			editor.tables.setValueAt("EDITED", 0, 1);
			assertThrows(IllegalArgumentException.class, () -> editor.removeExcludedMappings(review));
			assertEquals(2, editor.tables.getRowCount());
			assertEquals(2, editor.columns.getRowCount());
			assertEquals("EDITED", editor.tables.getValueAt(0, 1));
		});
	}

	@Test
	void scopePreviewIsReadOnlyAndOnlyExplicitRemovalInvokesTheAction() throws Exception {
		var review = new TransferNameMappingScopeService().review(schema(), List.of("KEEP"), mapping());
		SwingUtilities.invokeAndWait(() -> {
			var removed = new AtomicInteger();
			var cancelled = new AtomicInteger();
			var panel = new TransferMappingScopePanel(review, removed::incrementAndGet, cancelled::incrementAndGet);
			assertEquals(1, panel.model.getRowCount());
			assertEquals(1, panel.model.getValueAt(0, 3));
			assertFalse(panel.model.isCellEditable(0, 0));
			assertEquals(0, removed.get());
			panel.cancel.doClick(0);
			assertEquals(1, cancelled.get());
			assertEquals(0, removed.get());
			panel.remove.doClick(0);
			panel.remove.doClick(0);
			assertEquals(1, removed.get());
		});
	}

	@Test
	void wrappedExplanationLeavesSpaceForTheTableAndActionsAtDialogSize() throws Exception {
		var review = new TransferNameMappingScopeService().review(schema(), List.of("KEEP"), mapping());
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferMappingScopePanel(review, () -> {
			}, () -> {
			});
			panel.setSize(940, 480);
			layout(panel);
			var tableScroll = (javax.swing.JScrollPane) ((java.awt.BorderLayout) panel.getLayout())
					.getLayoutComponent(java.awt.BorderLayout.CENTER);
			assertTrue(tableScroll.getHeight() > 100);
			var bounds = javax.swing.SwingUtilities.convertRectangle(panel.remove.getParent(), panel.remove.getBounds(),
					panel);
			assertTrue(bounds.y >= 0 && bounds.y + bounds.height <= panel.getHeight());
		});
	}

	private static void layout(java.awt.Container parent) {
		parent.doLayout();
		for (var child : parent.getComponents())
			if (child instanceof java.awt.Container container)
				layout(container);
	}
}
