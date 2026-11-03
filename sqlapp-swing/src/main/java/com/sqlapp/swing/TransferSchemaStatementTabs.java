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

	private record StatusChoice(String status, long count) {
		@Override
		public String toString() {
			return (status == null ? "All statuses" : status) + " (" + count + ")";
		}
	}

	javax.swing.JComboBox<StatusChoice> statusSelector;
	javax.swing.JButton showUncertain;

	void clear(JTabbedPane tabs) {
		if (panel != null)
			tabs.remove(panel);
		panel = null;
		taskSelector = null;
		statusSelector = null;
		showUncertain = null;
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
		statusSelector = new javax.swing.JComboBox<>();
		statusSelector.addItem(new StatusChoice(null, rows.size()));
		rows.stream().map(row -> row.get(2)).distinct().forEach(status -> statusSelector
				.addItem(new StatusChoice(status, rows.stream().filter(row -> row.get(2).equals(status)).count())));
		var statusLabel = new javax.swing.JLabel("Recorded status");
		statusLabel.setLabelFor(statusSelector);
		statusSelector.setToolTipText("Counts cover all recorded statements, before task or text filtering.");
		scope.add(statusLabel);
		scope.add(statusSelector);
		showUncertain = new javax.swing.JButton("Show uncertain attempt");
		showUncertain.setEnabled(rows.stream().anyMatch(row -> row.get(2).equals("Uncertain attempt")));
		showUncertain
				.setToolTipText("Show the uncertain SQL. It may already have taken effect; this does not retry it.");
		scope.add(showUncertain);
		panel.setScopeControls(scope);
		var currentPanel = panel;
		var currentSelector = taskSelector;
		var currentStatus = statusSelector;
		var currentButton = showUncertain;
		Runnable filter = () -> {
			if (panel != currentPanel || taskSelector != currentSelector || statusSelector != currentStatus)
				return;
			var scopes = new java.util.HashMap<Integer, String>();
			var task = ((TaskChoice) currentSelector.getSelectedItem()).id();
			var status = ((StatusChoice) currentStatus.getSelectedItem()).status();
			if (task != null)
				scopes.put(1, task);
			if (status != null)
				scopes.put(2, status);
			panel.setExactFilters(scopes);
		};
		taskSelector.addActionListener(event -> filter.run());
		statusSelector.addActionListener(event -> filter.run());
		showUncertain.addActionListener(event -> {
			if (panel != currentPanel || showUncertain != currentButton || !currentButton.isEnabled()
					|| !panel.table.isEnabled())
				return;
			panel.filter.setText("");
			taskSelector.setSelectedIndex(0);
			for (int index = 1; index < statusSelector.getItemCount(); index++)
				if ("Uncertain attempt".equals(statusSelector.getItemAt(index).status())) {
					statusSelector.setSelectedIndex(index);
					if (panel.table.getRowCount() > 0) {
						panel.table.setRowSelectionInterval(0, 0);
						panel.table.scrollRectToVisible(panel.table.getCellRect(0, 0, true));
					}
					break;
				}
		});
		tabs.addTab("Schema statements (" + rows.size() + ")", panel);
	}

	boolean showTask(JTabbedPane tabs, String taskId) {
		if (panel == null || tabs.indexOfComponent(panel) < 0)
			return false;
		for (int index = 1; index < taskSelector.getItemCount(); index++)
			if (taskSelector.getItemAt(index).id().equals(taskId)) {
				panel.filter.setText("");
				statusSelector.setSelectedIndex(0);
				taskSelector.setSelectedIndex(index);
				if (panel.table.getRowCount() > 0) {
					int selected = 0;
					for (int row = 0; row < panel.table.getRowCount(); row++)
						if ("Uncertain attempt".equals(
								panel.table.getModel().getValueAt(panel.table.convertRowIndexToModel(row), 2))) {
							selected = row;
							break;
						}
					panel.table.setRowSelectionInterval(selected, selected);
					panel.table.scrollRectToVisible(panel.table.getCellRect(selected, 0, true));
				}
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
