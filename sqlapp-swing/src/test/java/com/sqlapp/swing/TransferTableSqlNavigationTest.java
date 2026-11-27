/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;

class TransferTableSqlNavigationTest {
	private final List<TransferSchemaPreparation.Statement> sql = List.of(
			new TransferSchemaPreparation.Statement("task-1", "CREATE TABLE A(ID INT)"),
			new TransferSchemaPreparation.Statement("task-10", "ALTER TABLE B ADD NOTE VARCHAR(20)"),
			new TransferSchemaPreparation.Statement("task-1", "CREATE INDEX IX ON A(ID)"));

	private FileTransferDesktopService.Reviewed reviewed() {
		var tables = List.of(
				new TransferSchemaPreparation.TablePreparation("task-1", null, "PUBLIC", "A",
						TransferSchemaPreparation.TableAction.CREATE),
				new TransferSchemaPreparation.TablePreparation("task-10", null, "PUBLIC", "B",
						TransferSchemaPreparation.TableAction.APPLY_DIFFERENCES),
				new TransferSchemaPreparation.TablePreparation("no-sql", null, "PUBLIC", "C",
						TransferSchemaPreparation.TableAction.SKIP_EXISTING));
		var plan = new TransferSchemaPreparation.Plan("hash", sql, List.of(), tables);
		return new FileTransferDesktopService.Reviewed(new TransferFileImportService.Review(Path.of("manifest.yaml"),
				"hash", new DatabaseIdentity("DB", "1", "hash"), "hash", plan), null);
	}

	@Test
	void followsSelectedTableDespiteSortingAndColumnReorderingAndUsesExactTaskScope() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var tables = new TransferTablePreparationTabs();
			var statements = new TransferSchemaStatementTabs();
			tables.setStatementNavigator(task -> statements.showTask(tabs, task));
			tables.show(tabs, reviewed());
			statements.show(tabs, reviewed());
			var tablePanel = (ReportDetailTablePanel) tabs.getComponentAt(0);
			var sqlPanel = (ReportDetailTablePanel) tabs.getComponentAt(1);
			assertFalse(tables.showSql.isEnabled());
			tablePanel.table.moveColumn(0, 3);
			tablePanel.table.getRowSorter().toggleSortOrder(3);
			tablePanel.table.getRowSorter().toggleSortOrder(3);
			for (int row = 0; row < tablePanel.table.getRowCount(); row++)
				if (tablePanel.table.getValueAt(row, tablePanel.table.convertColumnIndexToView(0)).equals("task-1"))
					tablePanel.table.setRowSelectionInterval(row, row);
			sqlPanel.filter.setText("no matches");
			tables.showSql.doClick(0);
			assertSame(sqlPanel, tabs.getSelectedComponent());
			assertEquals("", sqlPanel.filter.getText());
			assertEquals(2, sqlPanel.table.getRowCount());
			assertEquals(List.of("1", "3"),
					java.util.stream.IntStream.range(0, 2).mapToObj(row -> sqlPanel.table.getValueAt(row, 0)).toList());
			sqlPanel.filter.setText("ALTER");
			assertEquals(0, sqlPanel.table.getRowCount());
			sqlPanel.clear.doClick(0);
			assertEquals(2, sqlPanel.table.getRowCount());
			statements.taskSelector.setSelectedIndex(0);
			assertEquals(3, sqlPanel.table.getRowCount());
			tables.preparationSelector.setSelectedIndex(3); // Differences
			assertEquals(1, tablePanel.table.getRowCount());
			tablePanel.table.setRowSelectionInterval(0, 0);
			tables.showSql.doClick(0);
			assertEquals(1, sqlPanel.table.getRowCount());
			assertEquals("task-10", sqlPanel.table.getValueAt(0, 1));
			tables.preparationSelector.setSelectedIndex(0);
			tablePanel.table.setRowSelectionInterval(0, 1);
			assertFalse(tables.showSql.isEnabled());
			tablePanel.filter.setText("no-sql");
			tablePanel.table.setRowSelectionInterval(0, 0);
			assertFalse(tables.showSql.isEnabled());
			var oldButton = tables.showSql;
			tables.clear(tabs);
			oldButton.doClick(0);
			assertSame(sqlPanel, tabs.getSelectedComponent());
		});
	}

	@Test
	void statusScopeCombinesWithTaskAndSearchAndUncertainShortcutRevealsRecordedSql() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferSchemaStatementTabs();
			var outcome = new TransferFileImportService.Outcome("hash", "hash",
					TransferJobExecutionService.Phase.SCHEMA_PREPARATION, null, false, Map.of(), Map.of(), null, null,
					new TransferSchemaPreparation.Progress(sql, 1, 2));
			view.show(tabs, outcome);
			var panel = (ReportDetailTablePanel) tabs.getComponentAt(0);
			assertTrue(String.valueOf(view.statusSelector.getItemAt(0)).contains("(3)"));
			assertTrue(view.showTask(tabs, "task-1"));
			view.statusSelector.setSelectedIndex(1); // Completed
			assertEquals(1, panel.table.getRowCount());
			assertEquals("1", panel.table.getValueAt(0, 0));
			panel.filter.setText("ALTER");
			assertEquals(0, panel.table.getRowCount());
			view.showUncertain.doClick(0);
			assertEquals("", panel.filter.getText());
			assertEquals(1, panel.table.getRowCount());
			assertEquals("2", panel.table.getValueAt(0, 0));
			assertEquals("Uncertain attempt", panel.table.getValueAt(0, 2));
			assertEquals(0, panel.table.getSelectedRow());
			assertTrue(panel.selected.getText().contains("ALTER TABLE B"));
			assertTrue(view.showTask(tabs, "task-1"));
			assertEquals(2, panel.table.getRowCount()); // status reset for task navigation
			panel.refreshEnabled(false);
			view.showUncertain.doClick(0);
			assertEquals(2, panel.table.getRowCount());
			var oldButton = view.showUncertain;
			var oldStatus = view.statusSelector;
			view.clear(tabs);
			oldButton.doClick(0);
			oldStatus.setSelectedIndex(0);
			assertEquals(0, tabs.getTabCount());
			view.show(tabs, reviewed());
			assertFalse(view.showUncertain.isEnabled());
			assertEquals(2, view.statusSelector.getItemCount());
		});
	}

	@Test
	void scopedExecutionEvidenceRetainsGlobalOrderAndUncertainAttemptAndCannotNavigateAfterClear() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferSchemaStatementTabs();
			var outcome = new TransferFileImportService.Outcome("hash", "hash",
					TransferJobExecutionService.Phase.SCHEMA_PREPARATION, null, false, Map.of(), Map.of(), null, null,
					new TransferSchemaPreparation.Progress(sql, 1, 2));
			view.show(tabs, outcome);
			var panel = (ReportDetailTablePanel) tabs.getComponentAt(0);
			assertTrue(view.showTask(tabs, "task-10"));
			assertEquals(1, panel.table.getRowCount());
			assertEquals("2", panel.table.getValueAt(0, 0));
			assertEquals("Uncertain attempt", panel.table.getValueAt(0, 2));
			var oldSelector = view.taskSelector;
			view.clear(tabs);
			oldSelector.setSelectedIndex(0);
			assertFalse(view.showTask(tabs, "task-10"));
			assertEquals(0, tabs.getTabCount());
		});
	}
}
