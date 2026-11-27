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

	private record PreparationChoice(TransferSchemaPreparation.TableAction action, long count) {
		@Override
		public String toString() {
			return (action == null ? "All preparations" : actionLabel(action)) + " (" + count + ")";
		}
	}

	javax.swing.JComboBox<PreparationChoice> preparationSelector;
	javax.swing.JLabel preparationCounts;

	void setStatementNavigator(java.util.function.Consumer<String> navigator) {
		statementNavigator = navigator;
	}

	void clear(JTabbedPane tabs) {
		if (panel != null)
			tabs.remove(panel);
		panel = null;
		showSql = null;
		preparationSelector = null;
		preparationCounts = null;
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
		preparationSelector = new javax.swing.JComboBox<>();
		preparationSelector.addItem(new PreparationChoice(null, plan.tables().size()));
		var counts = plan.tables().stream().collect(java.util.stream.Collectors.groupingBy(
				TransferSchemaPreparation.TablePreparation::action, java.util.stream.Collectors.counting()));
		for (var action : TransferSchemaPreparation.TableAction.values())
			if (counts.containsKey(action))
				preparationSelector.addItem(new PreparationChoice(action, counts.get(action)));
		var label = new javax.swing.JLabel("Reviewed preparation");
		label.setLabelFor(preparationSelector);
		preparationSelector
				.setToolTipText("Filter recorded review intent only. This does not change which tables run.");
		controls.add(label);
		controls.add(preparationSelector);
		controls.add(showSql);
		preparationCounts = new javax.swing.JLabel("All reviewed tables — Create: "
				+ counts.getOrDefault(TransferSchemaPreparation.TableAction.CREATE, 0L) + "; use existing: "
				+ counts.getOrDefault(TransferSchemaPreparation.TableAction.USE_EXISTING, 0L) + "; skip: "
				+ counts.getOrDefault(TransferSchemaPreparation.TableAction.SKIP_EXISTING, 0L) + "; differences: "
				+ counts.getOrDefault(TransferSchemaPreparation.TableAction.APPLY_DIFFERENCES, 0L));
		preparationCounts.setToolTipText("Counts before search/filtering; reviewed intent, not execution success.");
		var scopes = new javax.swing.JPanel(new java.awt.BorderLayout());
		scopes.add(preparationCounts, java.awt.BorderLayout.NORTH);
		scopes.add(controls, java.awt.BorderLayout.CENTER);
		panel.setScopeControls(scopes);
		var tasksWithSql = plan.statements().stream().map(TransferSchemaPreparation.Statement::taskId)
				.collect(java.util.stream.Collectors.toSet());
		var currentPanel = panel;
		var currentButton = showSql;
		var currentSelector = preparationSelector;
		preparationSelector.addActionListener(event -> {
			if (panel != currentPanel || preparationSelector != currentSelector)
				return;
			var action = ((PreparationChoice) currentSelector.getSelectedItem()).action();
			panel.setExactFilter(4, action == null ? null : actionLabel(action));
		});
		Runnable refresh = () -> {
			if (panel != currentPanel) {
				currentButton.setEnabled(false);
				return;
			}
			int row = panel.table.getSelectedRow();
			boolean available = row >= 0 && panel.table.isEnabled() && panel.table.getSelectedRowCount() == 1
					&& statementNavigator != null && tasksWithSql
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
		return plan.tables().stream()
				.map(table -> List.of(table.taskId(), Objects.toString(table.targetCatalog(), ""),
						Objects.toString(table.targetSchema(), ""), table.targetTable(), actionLabel(table.action())))
				.toList();
	}

	private static String actionLabel(TransferSchemaPreparation.TableAction action) {
		return switch (action) {
		case CREATE -> "Create missing table";
		case USE_EXISTING -> "Use existing table for data transfer";
		case SKIP_EXISTING -> "Skip existing table; no comparison";
		case APPLY_DIFFERENCES -> "Review/apply selected-table differences";
		};
	}
}
