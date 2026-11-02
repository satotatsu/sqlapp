/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTabbedPane;
import com.sqlapp.data.db.command.migration.transfer.*;

/** Searchable statements in original execution order; never a resume plan. */
final class TransferSchemaStatementTabs {
	private ReportDetailTablePanel panel;

	private record TaskChoice(String id) {
		@Override
		public String toString() {
			return id == null ? "All tasks" : id;
		}
	}

	javax.swing.JComboBox<TaskChoice> taskSelector;

	void clear(JTabbedPane tabs) {
		if (panel != null)
			tabs.remove(panel);
		panel = null;
		taskSelector = null;
	}

	void show(JTabbedPane tabs, Object value) {
		clear(tabs);
		final var rows = rows(value);
		if (rows.isEmpty())
			return;
		panel = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Schema statements",
				"Recorded SQL in execution order. Task filtering changes the display only; dependencies may span tasks. Inspect the full Schema SQL before execution. Completed SQL may already be committed; an uncertain attempt may have taken effect. This is not a resume plan or execution approval.",
				List.of("Order", "Table / task", "Recorded status", "SQL"), rows));
		panel.setNumericColumns(List.of(0));
		panel.table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
		int[] widths = { 70, 190, 180, 600 };
		for (int column = 0; column < widths.length; column++) {
			panel.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
			panel.table.getColumnModel().getColumn(column).setWidth(widths[column]);
		}
		taskSelector = new javax.swing.JComboBox<>();
		taskSelector.addItem(new TaskChoice(null));
		rows.stream().map(row -> row.get(1)).distinct().forEach(id -> taskSelector.addItem(new TaskChoice(id)));
		taskSelector.setPrototypeDisplayValue(new TaskChoice("transfer-table-123456789"));
		var scope = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		var label = new javax.swing.JLabel("Table / task");
		label.setLabelFor(taskSelector);
		scope.add(label);
		scope.add(taskSelector);
		panel.setScopeControls(scope);
		var currentPanel = panel;
		var currentSelector = taskSelector;
		taskSelector.addActionListener(event -> {
			if (panel == currentPanel && taskSelector == currentSelector)
				panel.setExactFilter(1, ((TaskChoice) taskSelector.getSelectedItem()).id());
		});
		tabs.addTab("Schema statements (" + rows.size() + ")", panel);
	}

	boolean showTask(JTabbedPane tabs, String taskId) {
		if (panel == null || tabs.indexOfComponent(panel) < 0)
			return false;
		for (int index = 1; index < taskSelector.getItemCount(); index++)
			if (taskSelector.getItemAt(index).id().equals(taskId)) {
				panel.filter.setText("");
				taskSelector.setSelectedIndex(index);
				tabs.setSelectedComponent(panel);
				return true;
			}
		return false;
	}

	static List<List<String>> rows(Object value) {
		List<TransferSchemaPreparation.Statement> statements;
		TransferSchemaPreparation.Progress progress = null;
		if (value instanceof DirectTransferDesktopService.Reviewed review)
			statements = review.review().prepared().schemaPreparation().statements();
		else if (value instanceof FileTransferDesktopService.Reviewed review)
			statements = review.review().schemaPreparation().statements();
		else if (value instanceof DirectTransferDesktopService.Executed execution) {
			progress = execution.outcome().schemaPreparation();
			statements = progress.planned();
		} else if (value instanceof TransferFileImportService.Outcome outcome) {
			progress = outcome.schemaPreparation();
			statements = progress.planned();
		} else
			return List.of();
		return rows(statements, progress);
	}

	static List<List<String>> rows(List<TransferSchemaPreparation.Statement> statements,
			TransferSchemaPreparation.Progress progress) {
		final var rows = new ArrayList<List<String>>();
		for (int index = 0; index < statements.size(); index++) {
			final int order = index + 1;
			final String status = progress == null ? "Reviewed / not executed"
					: order <= progress.completedStatements() ? "Completed"
							: Integer.valueOf(order).equals(progress.attemptedStatement()) ? "Uncertain attempt"
									: "Not executed";
			final var statement = statements.get(index);
			rows.add(List.of(Integer.toString(order), statement.taskId(), status, statement.sql()));
		}
		return List.copyOf(rows);
	}
}
