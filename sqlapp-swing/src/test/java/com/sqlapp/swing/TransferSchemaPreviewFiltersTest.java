/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.*;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;

class TransferSchemaPreviewFiltersTest {
	@Test
	void issueDetailsAppearFirstAfterTableAndIssueFilteringWhileSourceEvidenceRemains() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			panel.showResult(result());
			panel.sourceTable.setSelectedIndex(2);
			panel.previewIssuesOnly.doClick(0);
			panel.details.table.moveColumn(0, 2);
			panel.details.table.getRowSorter().toggleSortOrder(1);
			panel.details.table.setRowSelectionInterval(0, 0);
			assertTrue(panel.details.selected.getText()
					.startsWith("Preview issue: Builder reason AA\nTarget definition preview: Unavailable\n"));
			assertTrue(panel.details.selected.getText().contains("Source table: PUBLIC.AA"));
			assertTrue(panel.details.selected.getText().contains("Source nullable: Yes"));
			panel.changed();
			assertFalse(panel.details.selected.getText().contains("Builder reason AA"));
		});
	}

	@Test
	void restoresApplicableControlsAfterBusyStateWithoutEnablingUnavailableActions() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			panel.setColumnNavigator(column -> {
			});
			panel.showResult(result());
			panel.details.table.setRowSelectionInterval(0, 0);
			panel.refreshEnabled(false);
			assertFalse(panel.sourceTable.isEnabled());
			assertFalse(panel.previewIssuesOnly.isEnabled());
			assertFalse(panel.editColumn.isEnabled());
			panel.refreshEnabled(true);
			assertTrue(panel.sourceTable.isEnabled());
			assertTrue(panel.previewIssuesOnly.isEnabled());
			assertTrue(panel.editColumn.isEnabled());
			panel.clear("Check again");
			panel.sourceTable.setEnabled(true);
			panel.previewIssuesOnly.setEnabled(true);
			panel.editColumn.setEnabled(true);
			panel.refreshEnabled(true);
			assertFalse(panel.sourceTable.isEnabled());
			assertFalse(panel.previewIssuesOnly.isEnabled());
			assertFalse(panel.editColumn.isEnabled());
		});
	}

	private Result result() {
		return new Result(Path.of("schema.xml"), "schema", "mapping", List.of(
				new Table("PUBLIC.A", "A_COPY",
						List.of(new Column("ID", "UNAVAILABLE_NAME", "INT", true, false, "INT NOT NULL", null),
								new Column("BAD", "BAD", "OTHER", false, true, null, "Builder reason A")),
						"A"),
				new Table("PUBLIC.AA", "AA_COPY",
						List.of(new Column("BAD", "BAD_COPY", "OTHER", false, true, null, "Builder reason AA")), "AA")),
				List.of(), "TargetBuilder");
	}

	@Test
	void combinesExactTableIssueEvidenceAndLiteralSearchWithoutChangingTheResult() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			var result = result();
			panel.showResult(result);
			assertEquals("All column previews: 1 available; 2 unavailable", panel.previewCount.getText());
			panel.previewIssuesOnly.doClick(0);
			assertEquals(2, panel.details.table.getRowCount());
			panel.sourceTable.setSelectedIndex(1);
			assertEquals(1, panel.details.table.getRowCount());
			assertEquals("PUBLIC.A", panel.details.table.getValueAt(0, 0));
			panel.details.filter.setText("AA");
			assertEquals(0, panel.details.table.getRowCount());
			panel.details.clear.doClick(0);
			assertEquals(1, panel.details.table.getRowCount());
			assertTrue(panel.previewIssuesOnly.isSelected());
			assertEquals(1, panel.sourceTable.getSelectedIndex());
			panel.previewIssuesOnly.doClick(0);
			assertEquals(2, panel.details.table.getRowCount());
			panel.sourceTable.setSelectedIndex(0);
			assertEquals(3, panel.details.table.getRowCount());
			assertEquals("UNAVAILABLE_NAME", result.tables().getFirst().columns().getFirst().target());
		});
	}

	@Test
	void invalidationAndNewChecksResetScopeCountsAndUnavailableOnlyState() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			panel.showResult(result());
			panel.previewIssuesOnly.doClick(0);
			panel.sourceTable.setSelectedIndex(2);
			panel.changed();
			assertEquals(0, panel.model.getRowCount());
			assertFalse(panel.previewIssuesOnly.isSelected());
			assertFalse(panel.previewCount.isVisible());
			assertFalse(panel.sourceTable.isEnabled());
			assertEquals(1, panel.sourceTable.getItemCount());
			var clean = new Result(Path.of("schema.xml"), "schema", "mapping",
					List.of(new Table("A", "A",
							List.of(new Column("ID", "ID", "INT", true, false, "INT NOT NULL", null)))),
					List.of(), "TargetBuilder");
			panel.showResult(clean);
			assertEquals(1, panel.details.table.getRowCount());
			assertFalse(panel.previewIssuesOnly.isEnabled());
			assertFalse(panel.previewIssuesOnly.isSelected());
			assertTrue(panel.previewCount.getText().contains("1 available; 0 unavailable"));
			panel.showResult(new Result(clean.schemaFile(), "schema", "mapping", clean.tables(), List.of()));
			assertFalse(panel.previewIssuesOnly.isVisible());
			assertFalse(panel.previewCount.isVisible());
			assertEquals(7, panel.details.table.getColumnCount());
		});
	}

	@Test
	void filteredAndSortedIssueStillNavigatesToTheVerifiedEditorAlias() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = new TransferNameMappingPanel();
			editor.load(new TransferNameMappingIO().validate(
					"formatVersion: 1\ntables:\n  A: {columns: {ID: ID_COPY}}\n  AA: {columns: {BAD: BAD_COPY}}\n"));
			var original = editor.configuration();
			editor.schemaCheck.showResult(result());
			editor.schemaCheck.sourceTable.setSelectedIndex(2);
			editor.schemaCheck.previewIssuesOnly.doClick(0);
			var grid = editor.schemaCheck.details.table;
			grid.moveColumn(0, 3);
			grid.getRowSorter().toggleSortOrder(1);
			grid.setRowSelectionInterval(0, 0);
			editor.schemaCheck.editColumn.doClick(0);
			assertEquals(1, editor.tabs.getSelectedIndex());
			assertEquals(1, editor.columnGrid.getRowCount());
			assertEquals("AA", editor.columnGrid.getValueAt(0, editor.columnGrid.convertColumnIndexToView(0)));
			editor.columnGrid.getCellEditor().stopCellEditing();
			assertEquals(original, editor.configuration());
		});
	}
}
