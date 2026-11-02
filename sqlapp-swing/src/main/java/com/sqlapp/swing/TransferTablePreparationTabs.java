/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.List;
import java.util.Objects;
import javax.swing.JTabbedPane;
import com.sqlapp.data.db.command.migration.transfer.*;

/**
 * Recorded table intent from the shared planner; never infer state from SQL.
 */
final class TransferTablePreparationTabs {
	private ReportDetailTablePanel panel;
	private java.util.function.Consumer<String> statementNavigator;
	javax.swing.JButton showSql;

	void setStatementNavigator(java.util.function.Consumer<String> navigator) {
		statementNavigator = navigator;
	}

	void clear(JTabbedPane tabs) {
		if (panel != null)
			tabs.remove(panel);
		panel = null;
		showSql = null;
	}

	void show(JTabbedPane tabs, Object value) {
		clear(tabs);
		TransferSchemaPreparation.Plan plan;
		if (value instanceof DirectTransferDesktopService.Reviewed review)
			plan = review.review().prepared().schemaPreparation();
		else if (value instanceof DirectTransferDesktopService.Executed execution)
			plan = execution.outcome().prepared().schemaPreparation();
		else if (value instanceof FileTransferDesktopService.Reviewed review)
			plan = review.review().schemaPreparation();
		else
			return;
		if (plan.tables().isEmpty())
			return;
		panel = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Table preparation",
				"Recorded review intent, not execution status. Existing tables in schema-only create mode are skipped without comparison. Difference targets may require manual work; inspect Warnings and Schema SQL. Check Schema statements for recorded SQL execution status.",
				List.of("Task", "Target catalog", "Target schema", "Target table", "Reviewed preparation"),
				rows(plan)));
		panel.table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
		int[] widths = { 140, 120, 140, 240, 360 };
		for (int column = 0; column < widths.length; column++) {
			panel.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
			panel.table.getColumnModel().getColumn(column).setWidth(widths[column]);
		}
		showSql = new javax.swing.JButton("Show table SQL");
		var controls = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		controls.add(showSql);
		panel.setScopeControls(controls);
		var tasksWithSql = plan.statements().stream().map(TransferSchemaPreparation.Statement::taskId)
				.collect(java.util.stream.Collectors.toSet());
		var currentPanel = panel;
		var currentButton = showSql;
		Runnable refresh = () -> {
			if (panel != currentPanel) {
				currentButton.setEnabled(false);
				return;
			}
			int row = panel.table.getSelectedRow();
			boolean available = row >= 0 && statementNavigator != null && tasksWithSql
					.contains(panel.table.getValueAt(row, panel.table.convertColumnIndexToView(0)).toString());
			showSql.setEnabled(available);
			showSql.setToolTipText(available ? "Open the exact task's recorded SQL without changing the plan."
					: "Select a table with recorded SQL. No SQL does not prove schema equivalence; inspect Warnings.");
		};
		panel.table.getSelectionModel().addListSelectionListener(event -> refresh.run());
		showSql.addActionListener(event -> {
			refresh.run();
			if (currentButton.isEnabled())
				statementNavigator.accept(panel.table
						.getValueAt(panel.table.getSelectedRow(), panel.table.convertColumnIndexToView(0)).toString());
		});
		refresh.run();
		tabs.addTab("Table preparation (" + plan.tables().size() + ")", panel);
	}

	static List<List<String>> rows(TransferSchemaPreparation.Plan plan) {
		return plan.tables().stream().map(table -> List.of(table.taskId(), Objects.toString(table.targetCatalog(), ""),
				Objects.toString(table.targetSchema(), ""), table.targetTable(), switch (table.action()) {
				case CREATE -> "Create missing table";
				case USE_EXISTING -> "Use existing table for data transfer";
				case SKIP_EXISTING -> "Skip existing table; no comparison";
				case APPLY_DIFFERENCES -> "Review/apply selected-table differences";
				})).toList();
	}
}
