/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.*;

class TransferPreviewIssueNavigationTest {
	@Test
	void startsFromFirstSelectedRowAndKeepsHorizontalScrollPosition() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			panel.showResult(result());
			panel.setSize(940, 500);
			panel.addNotify();
			layout(panel);
			var viewport = (JViewport) panel.details.table.getParent();
			viewport.setViewPosition(new java.awt.Point(250, 0));
			int horizontal = viewport.getViewPosition().x;
			assertTrue(horizontal > 0);
			panel.details.table.setRowSelectionInterval(0, 2);
			panel.nextIssue.doClick(0);
			assertEquals("Z", sourceColumn(panel));
			assertEquals(1, panel.details.table.getSelectedRowCount());
			assertEquals(horizontal, viewport.getViewPosition().x);
			panel.removeNotify();
		});
	}

	private void layout(java.awt.Container parent) {
		parent.doLayout();
		for (var child : parent.getComponents())
			if (child instanceof java.awt.Container nested)
				layout(nested);
	}

	private Result result() {
		return new Result(
				Path.of("schema.xml"), "schema", "mapping", List.of(
						new Table("PUBLIC.A", "A_COPY",
								List.of(new Column("ID", "UNAVAILABLE_NAME", "INT", true, false, "INT NOT NULL", null),
										new Column("Z", "Z", "OTHER", false, true, null, "Reason A Z"),
										new Column("B", "B", "OTHER", false, true, null, "Reason A B")),
								"A"),
						new Table("PUBLIC.AA", "AA_COPY",
								List.of(new Column("ID", "ID", "OTHER", false, true, null, "Reason AA ID"),
										new Column("GOOD", "GOOD", "INT", false, true, "INT", null)),
								"AA")),
				List.of(), "TargetBuilder");
	}

	private String sourceColumn(TransferNameMappingSchemaPanel panel) {
		return panel.model
				.getValueAt(panel.details.table.convertRowIndexToModel(panel.details.table.getSelectedRow()), 1)
				.toString();
	}

	@Test
	void cyclesVisibleIssuesInBothDirectionsAndSkipsOrdinaryRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			panel.showResult(result());
			panel.previousIssue.doClick(0);
			assertEquals("ID", sourceColumn(panel));
			assertTrue(panel.issuePosition.getText().contains("Issue 3 / 3"));
			panel.nextIssue.doClick(0);
			assertEquals("Z", sourceColumn(panel));
			panel.nextIssue.doClick(0);
			assertEquals("B", sourceColumn(panel));
			panel.nextIssue.doClick(0);
			assertEquals("ID", sourceColumn(panel));
			panel.nextIssue.doClick(0);
			assertEquals("Z", sourceColumn(panel));
			panel.previousIssue.doClick(0);
			assertEquals("ID", sourceColumn(panel));
			assertTrue(panel.details.selected.getText().startsWith("Preview issue: Reason AA ID"));
			assertFalse(panel.previewIssuesOnly.isSelected());
			assertEquals(0, panel.sourceTable.getSelectedIndex());
			assertEquals(5, panel.model.getRowCount());
		});
	}

	@Test
	void followsCurrentSortOrderAndKeepsVerifiedEditorIdentityAfterColumnReordering() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var selected = new java.util.concurrent.atomic.AtomicReference<TransferNameMappingSchemaPanel.ColumnSelection>();
			var panel = new TransferNameMappingSchemaPanel();
			panel.setColumnNavigator(selected::set);
			panel.showResult(result());
			panel.details.table.getRowSorter().toggleSortOrder(1);
			panel.details.table.moveColumn(1, 0);
			var sorting = List.copyOf(panel.details.table.getRowSorter().getSortKeys());
			panel.nextIssue.doClick(0);
			assertEquals("B", sourceColumn(panel));
			panel.editColumn.doClick(0);
			assertEquals("A", selected.get().sourceTable());
			assertEquals("B", selected.get().sourceColumn());
			panel.nextIssue.doClick(0);
			assertEquals("ID", sourceColumn(panel));
			panel.nextIssue.doClick(0);
			assertEquals("Z", sourceColumn(panel));
			assertEquals(sorting, panel.details.table.getRowSorter().getSortKeys());
		});
	}

	@Test
	void respectsTableSearchAndIssueFiltersAndDisablesAnEmptyView() throws Exception {
		var holder = new TransferNameMappingSchemaPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			holder[0] = panel;
			panel.showResult(result());
			panel.sourceTable.setSelectedIndex(1);
			panel.previewIssuesOnly.doClick(0);
			panel.details.filter.setText("Reason A B");
		});
		SwingUtilities.invokeAndWait(() -> {
			var panel = holder[0];
			assertTrue(panel.nextIssue.isEnabled());
			panel.nextIssue.doClick(0);
			assertEquals("B", sourceColumn(panel));
			assertTrue(panel.issuePosition.getText().contains("1 / 1"));
			panel.sourceTable.setSelectedIndex(2);
		});
		SwingUtilities.invokeAndWait(() -> {
			var panel = holder[0];
			assertFalse(panel.nextIssue.isEnabled());
			assertFalse(panel.previousIssue.isEnabled());
			assertEquals("No preview issues in current view", panel.issuePosition.getText());
			assertEquals("Reason A B", panel.details.filter.getText());
			assertEquals(-1, panel.details.table.getSelectedRow());
			panel.details.clear.doClick(0);
		});
		SwingUtilities.invokeAndWait(() -> {
			var panel = holder[0];
			panel.nextIssue.doClick(0);
			assertEquals("ID", sourceColumn(panel));
			assertTrue(panel.previewIssuesOnly.isSelected());
			assertEquals(2, panel.sourceTable.getSelectedIndex());
			assertTrue(panel.details.selected.getText().startsWith("Preview issue: Reason AA ID"));
		});
	}

	@Test
	void busyInvalidatedAndReplacedChecksCannotNavigateOldIssues() throws Exception {
		var holder = new TransferNameMappingSchemaPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			holder[0] = panel;
			panel.showResult(result());
			panel.nextIssue.doClick(0);
			int selected = panel.details.table.getSelectedRow();
			panel.refreshEnabled(false);
			assertFalse(panel.nextIssue.isEnabled());
			panel.nextIssue.setEnabled(true);
			panel.nextIssue.doClick(0);
			assertEquals(selected, panel.details.table.getSelectedRow());
			panel.changed();
			panel.refreshEnabled(true);
			assertFalse(panel.nextIssue.isEnabled());
			assertEquals("", panel.issuePosition.getText());
			assertEquals(0, panel.model.getRowCount());
			panel.showResult(new Result(Path.of("new.xml"), "new", "new",
					List.of(new Table("NEW", "NEW",
							List.of(new Column("NEW_COLUMN", "NEW_COLUMN", "OTHER", false, true, null, "New reason")))),
					List.of(), "TargetBuilder"));
			assertEquals(-1, panel.details.table.getSelectedRow());
			panel.nextIssue.doClick(0);
			assertEquals("NEW_COLUMN", sourceColumn(panel));
			assertTrue(panel.issuePosition.getText().contains("1 / 1"));
		});
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("NEW_COLUMN", sourceColumn(holder[0]));
			assertFalse(holder[0].details.selected.getText().contains("Reason A"));
		});
	}
}
