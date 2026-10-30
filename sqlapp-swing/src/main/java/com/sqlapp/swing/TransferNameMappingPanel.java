/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SourceColumn;

/** Offline name editor over the shared mapping format. */
final class TransferNameMappingPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final DefaultTableModel tables = model("Source table", "Target table (blank: unchanged)");
	final DefaultTableModel columns = new DefaultTableModel(
			new String[] { "Source table", "Source column", "Target column", "Source type (snapshot)" }, 0) {
		@Override
		public Class<?> getColumnClass(int column) {
			return String.class;
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return column < 3;
		}
	};
	final JTable tableGrid = new JTable(tables);
	final JTable columnGrid = new JTable(columns);
	final JTabbedPane tabs = new JTabbedPane();
	final TransferNameMappingGrid tableTools = new TransferNameMappingGrid(tableGrid, tables, 0, 1, true);
	final TransferNameMappingGrid columnTools = new TransferNameMappingGrid(columnGrid, columns, 1, 2, false);
	final JButton checkNames = new JButton("Check names");
	final JButton previewYaml = new JButton("Preview YAML");
	final TransferNameMappingPreview preview = new TransferNameMappingPreview();
	final JButton goToIssue = new JButton("Go to selected field");
	final DefaultListModel<TransferNameMappingInputChecks.Issue> issues = new DefaultListModel<>();
	final JList<TransferNameMappingInputChecks.Issue> issueList = new JList<>(issues);
	final JLabel checkStatus = new JLabel("Check names to locate input problems. Database review is still required.");
	private boolean checked;

	TransferNameMappingPanel() {
		super(new BorderLayout(8, 8));
		var header = new JPanel(new BorderLayout());
		header.add(
				new JLabel(
						"Names only. Unlisted names stay unchanged. Save the job and review again before execution."),
				BorderLayout.CENTER);
		var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		actions.add(checkNames);
		actions.add(previewYaml);
		header.add(actions, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);
		tabs.addTab("Tables", tableTools);
		tabs.addTab("Columns", columnTools);
		var checks = new JPanel(new BorderLayout(4, 4));
		checks.add(checkStatus, BorderLayout.NORTH);
		checks.add(new JScrollPane(issueList), BorderLayout.CENTER);
		checks.add(goToIssue, BorderLayout.SOUTH);
		tabs.addTab("Input checks", checks);
		tabs.addTab("YAML preview", preview);
		previewYaml.addActionListener(event -> showPreview());
		preview.refresh.addActionListener(event -> showPreview());
		checkNames.addActionListener(event -> checkInputs(true));
		goToIssue.setEnabled(false);
		issueList.addListSelectionListener(
				event -> goToIssue.setEnabled(issueList.isEnabled() && issueList.getSelectedValue() != null));
		goToIssue.addActionListener(event -> showSelectedIssue());
		issueList.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event) {
				if (event.getClickCount() == 2)
					showSelectedIssue();
			}
		});
		issueList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "showIssue");
		issueList.getActionMap().put("showIssue", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				showSelectedIssue();
			}
		});
		javax.swing.event.TableModelListener invalidated = event -> {
			preview.changed();
			if (checked) {
				issues.clear();
				checked = false;
				checkStatus.setText(
						"Names changed. Check again to refresh input problems; database review is still required.");
			}
		};
		tables.addTableModelListener(invalidated);
		columns.addTableModelListener(invalidated);
		add(tabs, BorderLayout.CENTER);
		add(new JLabel(
				"Use the saved job's source table identities. Database name resolution and collisions are checked during review."),
				BorderLayout.SOUTH);
	}

	String formattedMapping() {
		return new TransferNameMappingIO().format(configuration());
	}

	private void showPreview() {
		try {
			preview.showSnapshot(formattedMapping());
			tabs.setSelectedIndex(3);
		} catch (RuntimeException failure) {
			preview.invalidate("Preview unavailable: "
					+ (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
			if (issues.isEmpty())
				tabs.setSelectedIndex(3);
		}
	}

	private static DefaultTableModel model(String... names) {
		return new DefaultTableModel(names, 0) {
			@Override
			public Class<?> getColumnClass(int column) {
				return String.class;
			}
		};
	}

	void load(TransferNameMappingIO.Configuration configuration) {
		tables.setRowCount(0);
		columns.setRowCount(0);
		configuration.tables().forEach((source, mapping) -> {
			tables.addRow(new String[] { source, mapping.targetTable() });
			mapping.columns().forEach((from, to) -> columns.addRow(new String[] { source, from, to }));
		});
	}

	/**
	 * Append missing columns and enrich types without replacing any authored name.
	 */
	void includeColumns(Map<String, java.util.List<SourceColumn>> discovered) {
		// Finish edits and validate the current form before changing any rows.
		configuration();
		var positions = new HashMap<String, Map<String, Integer>>();
		for (int row = 0; row < columns.getRowCount(); row++)
			positions.computeIfAbsent(value(columns, row, 0), key -> new HashMap<>()).put(value(columns, row, 1), row);
		discovered.forEach((table, sourceColumns) -> {
			var existingColumns = positions.computeIfAbsent(table, key -> new HashMap<>());
			for (var source : sourceColumns) {
				var existing = existingColumns.get(source.name());
				if (existing != null)
					columns.setValueAt(source.type(), existing, 3);
				else {
					existingColumns.put(source.name(), columns.getRowCount());
					columns.addRow(new String[] { table, source.name(), source.name(), source.type() });
				}
			}
		});
		columnTools.refreshCounts();
		tabs.setSelectedIndex(1);
	}

	TransferNameMappingIO.Configuration configuration() {
		var inputIssues = checkInputs(false);
		if (!inputIssues.isEmpty()) {
			tabs.setSelectedIndex(2);
			throw new IllegalArgumentException(inputIssues.getFirst().toString());
		}
		var targets = new LinkedHashMap<String, String>();
		var mappedColumns = new LinkedHashMap<String, Map<String, String>>();
		for (int i = 0; i < tables.getRowCount(); i++) {
			var source = value(tables, i, 0);
			var target = value(tables, i, 1);
			if (targets.containsKey(source))
				throw new IllegalArgumentException("Duplicate source table: " + source);
			targets.put(source, target.isBlank() ? null : target);
		}
		for (int i = 0; i < columns.getRowCount(); i++) {
			var source = value(columns, i, 0);
			var from = value(columns, i, 1);
			var to = value(columns, i, 2);
			var mapping = mappedColumns.computeIfAbsent(source, key -> new LinkedHashMap<>());
			if (mapping.putIfAbsent(from, to) != null)
				throw new IllegalArgumentException("Duplicate source column: " + source + "." + from);
			targets.putIfAbsent(source, null);
		}
		var result = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
		// Displayed same-name columns need no override in the shared YAML.
		mappedColumns.values().forEach(mapping -> new TransferNameMappingIO.TableMapping(null, mapping));
		mappedColumns.values()
				.forEach(mapping -> mapping.entrySet().removeIf(entry -> entry.getKey().equals(entry.getValue())));
		targets.forEach((source, target) -> result.put(source,
				new TransferNameMappingIO.TableMapping(target, mappedColumns.get(source))));
		var configuration = new TransferNameMappingIO.Configuration(1, result);
		var io = new TransferNameMappingIO();
		return io.validate(io.format(configuration));
	}

	private java.util.List<TransferNameMappingInputChecks.Issue> checkInputs(boolean show) {
		for (var grid : java.util.List.of(tableGrid, columnGrid))
			if (grid.isEditing() && !grid.getCellEditor().stopCellEditing())
				throw new IllegalArgumentException("Finish editing the current name.");
		var found = TransferNameMappingInputChecks.inspect(tables, columns);
		issues.clear();
		found.forEach(issues::addElement);
		checked = true;
		checkStatus.setText(found.isEmpty() ? "No input problems found. Database review is still required."
				: found.size() + " input problems. Select one to locate the field; database review is still required.");
		if (!found.isEmpty())
			issueList.setSelectedIndex(0);
		if (show)
			tabs.setSelectedIndex(2);
		return found;
	}

	private void showSelectedIssue() {
		var issue = issueList.getSelectedValue();
		if (issue == null)
			return;
		var grid = issue.tab() == 0 ? tableGrid : columnGrid;
		var tools = issue.tab() == 0 ? tableTools : columnTools;
		tools.clear.doClick(0);
		tabs.setSelectedIndex(issue.tab());
		int row = grid.convertRowIndexToView(issue.row());
		int column = grid.convertColumnIndexToView(issue.column());
		grid.changeSelection(row, column, false, false);
		grid.scrollRectToVisible(grid.getCellRect(row, column, true));
		grid.editCellAt(row, column);
		if (grid.getEditorComponent() != null)
			grid.getEditorComponent().requestFocusInWindow();
	}

	private static String value(DefaultTableModel model, int row, int column) {
		var value = model.getValueAt(row, column);
		return value == null ? "" : value.toString();
	}
}
