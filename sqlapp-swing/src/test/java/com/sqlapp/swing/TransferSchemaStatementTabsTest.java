/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferSchemaStatementTabsTest {
	private final List<TransferSchemaPreparation.Statement> statements = List.of(
			new TransferSchemaPreparation.Statement("parent", "CREATE TABLE P (ID INT)"),
			new TransferSchemaPreparation.Statement("child", "ALTER TABLE C\nADD COLUMN NAME VARCHAR(80)"),
			new TransferSchemaPreparation.Statement("child", "CREATE INDEX IX ON C (NAME)"));

	@Test
	void preservesOrderFullSqlAndDistinguishesUncertainFromUnexecuted() {
		var rows = TransferSchemaStatementTabs.rows(statements,
				new TransferSchemaPreparation.Progress(statements, 1, 2));
		assertEquals(List.of("1", "2", "3"), rows.stream().map(row -> row.get(0)).toList());
		assertEquals(List.of("Completed", "Uncertain attempt", "Not executed"),
				rows.stream().map(row -> row.get(2)).toList());
		assertEquals(statements.get(1).sql(), rows.get(1).get(3));
		assertEquals(List.of("Reviewed / not executed", "Reviewed / not executed", "Reviewed / not executed"),
				TransferSchemaStatementTabs.rows(statements, null).stream().map(row -> row.get(2)).toList());
	}

	@Test
	void tableSearchesFullSqlAndClearsRecordedProgress() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			tabs.addTab("Summary", new JPanel());
			var view = new TransferSchemaStatementTabs();
			var outcome = new TransferFileImportService.Outcome("hash", "hash",
					TransferJobExecutionService.Phase.COMPLETE, null, false, Map.of(), Map.of(), null, null,
					new TransferSchemaPreparation.Progress(statements, 3, null));
			view.show(tabs, outcome);
			assertEquals("Schema statements (3)", tabs.getTitleAt(1));
			var panel = (ReportDetailTablePanel) tabs.getComponentAt(1);
			assertFalse(panel.table.isCellEditable(0, 3));
			panel.filter.setText("ADD COLUMN");
			assertEquals(1, panel.table.getRowCount());
			panel.table.setRowSelectionInterval(0, 0);
			assertTrue(panel.selected.getText().contains(statements.get(1).sql()));
			view.clear(tabs);
			assertEquals(1, tabs.getTabCount());
			view.show(tabs, new Object());
			assertEquals(1, tabs.getTabCount());
		});
	}
}
