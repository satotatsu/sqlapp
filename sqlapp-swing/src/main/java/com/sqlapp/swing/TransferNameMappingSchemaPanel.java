/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService;

/**
 * Captured-Schema evidence only; target database state is intentionally
 * unknown.
 */
final class TransferNameMappingSchemaPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final DefaultTableModel model = new DefaultTableModel(
			new String[] { "Source table", "Source column", "Target table", "Target column", "Source model type",
					"Source PK", "Source nullable", "Target definition preview", "Preview issue" },
			0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	final ReportDetailTablePanel details = new ReportDetailTablePanel(new JTable(model), null, "Find mapping",
			"mapped columns");
	final JTextArea evidence = new JTextArea(4, 1);
	final JButton editColumn = new JButton("Edit selected column name");
	final JCheckBox previewIssuesOnly = new JCheckBox("Preview issues only");
	final JLabel previewCount = new JLabel();
	final JButton previousIssue = new JButton("Previous issue");
	final JButton nextIssue = new JButton("Next issue");
	final JLabel issuePosition = new JLabel();
	private final JPanel issueNavigation = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private boolean issueRefreshPending;
	private TransferNameMappingInspectionService.Result capturedResult;
	private Runnable captureChanged = () -> {
	};

	void setCaptureObserver(Runnable observer) {
		captureChanged = java.util.Objects.requireNonNull(observer);
	}

	boolean hasCapturedResult() {
		return captured && capturedResult != null;
	}

	TransferNameMappingInspectionService.Result reportSnapshot() {
		if (!hasCapturedResult())
			throw new IllegalArgumentException("Use Check Schema before saving a preview report.");
		return capturedResult;
	}

	java.util.List<com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO.ColumnRef> visibleReportColumns() {
		reportSnapshot();
		var refs = new java.util.ArrayList<com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO.ColumnRef>();
		for (int row = 0; row < details.table.getRowCount(); row++) {
			int index = details.table.convertRowIndexToModel(row);
			refs.add(new com.sqlapp.data.db.command.migration.transfer.TransferMappingPreviewReportIO.ColumnRef(
					model.getValueAt(index, 0).toString(), model.getValueAt(index, 1).toString()));
		}
		return java.util.List.copyOf(refs);
	}

	private record TableChoice(String source, String target) {
		@Override
		public String toString() {
			return source == null ? "All tables" : source + " → " + target;
		}
	}

	final JComboBox<TableChoice> sourceTable = new JComboBox<>();
	private final JPanel tableScope = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private boolean changingScope;

	record ColumnSelection(String sourceTable, String sourceColumn, String targetColumn, String sourceType) {
	}

	private java.util.function.Consumer<ColumnSelection> columnNavigator;
	private final java.util.List<ColumnSelection> rowSelections = new java.util.ArrayList<>();

	void setColumnNavigator(java.util.function.Consumer<ColumnSelection> navigator) {
		columnNavigator = navigator;
		refreshNavigation();
	}

	private void refreshNavigation() {
		editColumn.setVisible(columnNavigator != null);
		editColumn.setEnabled(controlsEnabled && captured && details.table.isEnabled() && columnNavigator != null
				&& details.table.getSelectedRowCount() == 1);
		scheduleIssueRefresh();
	}

	private void scheduleIssueRefresh() {
		if (issueRefreshPending)
			return;
		issueRefreshPending = true;
		SwingUtilities.invokeLater(() -> {
			issueRefreshPending = false;
			refreshIssueNavigation();
		});
	}

	private java.util.List<Integer> visibleIssueRows() {
		var rows = new java.util.ArrayList<Integer>();
		if (captured && hasTargetPreview)
			for (int row = 0; row < details.table.getRowCount(); row++)
				if (!java.util.Objects.toString(model.getValueAt(details.table.convertRowIndexToModel(row), 8), "")
						.isBlank())
					rows.add(row);
		return rows;
	}

	private void refreshIssueNavigation() {
		var rows = visibleIssueRows();
		boolean visible = captured && hasTargetPreview && hasPreviewIssues;
		issueNavigation.setVisible(visible);
		boolean enabled = visible && controlsEnabled && details.table.isEnabled() && !rows.isEmpty();
		previousIssue.setEnabled(enabled);
		nextIssue.setEnabled(enabled);
		int position = rows.indexOf(details.table.getSelectedRow());
		issuePosition.setText(!visible ? ""
				: rows.isEmpty() ? "No preview issues in current view"
						: position < 0 ? rows.size() + " preview issues in current view"
								: "Issue " + (position + 1) + " / " + rows.size() + " in current view");
	}

	private void navigateIssue(int direction) {
		if (!controlsEnabled || !captured || !hasTargetPreview || !details.table.isEnabled())
			return;
		var rows = visibleIssueRows();
		if (rows.isEmpty()) {
			refreshIssueNavigation();
			return;
		}
		int selected = details.table.getSelectedRow();
		int target = direction > 0 ? rows.getFirst() : rows.getLast();
		if (direction > 0) {
			for (int row : rows)
				if (row > selected) {
					target = row;
					break;
				}
		} else if (selected >= 0) {
			for (int index = rows.size() - 1; index >= 0; index--)
				if (rows.get(index) < selected) {
					target = rows.get(index);
					break;
				}
		}
		details.table.setRowSelectionInterval(target, target);
		var rectangle = details.table.getCellRect(target, 0, true);
		rectangle.x = details.table.getVisibleRect().x;
		rectangle.width = 1;
		details.table.scrollRectToVisible(rectangle);
		refreshIssueNavigation();
	}

	private boolean captured;
	private boolean controlsEnabled = true;
	private boolean hasTargetPreview;
	private boolean hasPreviewIssues;
	private boolean multipleTables;

	void refreshEnabled(boolean enabled) {
		controlsEnabled = enabled;
		details.refreshEnabled(enabled);
		previewIssuesOnly.setEnabled(enabled && captured && hasTargetPreview && hasPreviewIssues);
		sourceTable.setEnabled(enabled && captured && multipleTables);
		refreshNavigation();
		refreshIssueNavigation();
	}

	private final javax.swing.table.TableColumn definitionColumn;
	private final javax.swing.table.TableColumn issueColumn;

	TransferNameMappingSchemaPanel() {
		super(new BorderLayout(4, 4));
		add(details, BorderLayout.CENTER);
		details.setDetailPriority(8, java.util.List.of(8, 7));
		var actions = new JPanel(new BorderLayout());
		var previewActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		previewActions.add(editColumn);
		previewActions.add(previewIssuesOnly);
		previewActions.add(previewCount);
		actions.add(previewActions, BorderLayout.CENTER);
		issueNavigation.add(previousIssue);
		issueNavigation.add(nextIssue);
		issueNavigation.add(issuePosition);
		issueNavigation.setVisible(false);
		actions.add(issueNavigation, BorderLayout.SOUTH);
		previousIssue.setMnemonic(java.awt.event.KeyEvent.VK_P);
		nextIssue.setMnemonic(java.awt.event.KeyEvent.VK_N);
		previousIssue.setToolTipText(
				"Previous visible preview issue in the current sort order; wraps at the start. Filters stay unchanged.");
		nextIssue.setToolTipText(
				"Next visible preview issue in the current sort order; wraps at the end. Filters stay unchanged.");
		previousIssue.addActionListener(event -> navigateIssue(-1));
		nextIssue.addActionListener(event -> navigateIssue(1));
		previousIssue.setEnabled(false);
		nextIssue.setEnabled(false);
		details.table.getRowSorter().addRowSorterListener(event -> scheduleIssueRefresh());
		var tableLabel = new JLabel("Source → target table");
		tableLabel.setLabelFor(sourceTable);
		sourceTable.addItem(new TableChoice(null, null));
		sourceTable.setPrototypeDisplayValue(new TableChoice("CATALOG.SCHEMA.SOURCE", "TARGET"));
		tableScope.add(tableLabel);
		tableScope.add(sourceTable);
		tableScope.setVisible(false);
		sourceTable.setEnabled(false);
		actions.add(tableScope, BorderLayout.NORTH);
		sourceTable.addActionListener(event -> {
			if (!changingScope)
				details.setExactFilter(0, ((TableChoice) sourceTable.getSelectedItem()).source());
		});
		previewIssuesOnly.setEnabled(false);
		previewIssuesOnly.setVisible(false);
		previewCount.setVisible(false);
		previewIssuesOnly.setToolTipText(
				"Show rows with a recorded target-builder preview issue; search applies within these rows.");
		previewIssuesOnly.addActionListener(event -> details.setNonBlankFilter(8, previewIssuesOnly.isSelected()));
		details.setScopeControls(actions);
		editColumn.setToolTipText(
				"Open the verified source column in the name editor (Enter or double-click a row). No type or value conversion is applied.");
		var enter = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0);
		var keys = details.table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		var originalEnter = keys.get(enter);
		keys.put(enter, "editCheckedColumn");
		details.table.getActionMap().put("editCheckedColumn", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				if (columnNavigator != null)
					editColumn.doClick(0);
				else if (originalEnter != null) {
					var original = details.table.getActionMap().get(originalEnter);
					if (original != null && original.isEnabled())
						original.actionPerformed(event);
				}
			}
		});
		details.table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event) {
				if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2
						&& details.table.rowAtPoint(event.getPoint()) == details.table.getSelectedRow()
						&& details.table.rowAtPoint(event.getPoint()) >= 0
						&& details.table.columnAtPoint(event.getPoint()) >= 0)
					editColumn.doClick(0);
			}
		});
		details.table.getSelectionModel().addListSelectionListener(event -> refreshNavigation());
		editColumn.addActionListener(event -> {
			refreshNavigation();
			if (editColumn.isEnabled()) {
				int row = details.table.convertRowIndexToModel(details.table.getSelectedRow());
				try {
					columnNavigator.accept(rowSelections.get(row));
				} catch (IllegalArgumentException failure) {
					clear("Cannot open column editor: " + failure.getMessage());
				}
			}
		});
		refreshNavigation();
		int[] widths = { 170, 150, 170, 150, 110, 80, 80, 360, 360 };
		for (int column = 0; column < widths.length; column++)
			details.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
		definitionColumn = details.table.getColumnModel().getColumn(7);
		issueColumn = details.table.getColumnModel().getColumn(8);
		details.table.removeColumn(definitionColumn);
		details.table.removeColumn(issueColumn);
		evidence.setEditable(false);
		evidence.setLineWrap(true);
		evidence.setWrapStyleWord(true);
		add(new JScrollPane(evidence), BorderLayout.SOUTH);
		evidence.setText("Choose a source Schema XML and use Check Schema. No database is accessed or modified.");
	}

	void showResult(TransferNameMappingInspectionService.Result result) {
		captured = false;
		model.setRowCount(0);
		rowSelections.clear();
		if (result.targetDialect() != null) {
			if (details.table.convertColumnIndexToView(7) < 0)
				details.table.addColumn(definitionColumn);
			if (details.table.convertColumnIndexToView(8) < 0)
				details.table.addColumn(issueColumn);
			details.table.moveColumn(details.table.convertColumnIndexToView(7), 5);
			details.table.moveColumn(details.table.convertColumnIndexToView(8), 6);
		} else {
			if (details.table.convertColumnIndexToView(7) >= 0)
				details.table.removeColumn(definitionColumn);
			if (details.table.convertColumnIndexToView(8) >= 0)
				details.table.removeColumn(issueColumn);
		}
		for (var table : result.tables())
			for (var column : table.columns()) {
				rowSelections.add(new ColumnSelection(table.mappingSource(), column.source(), column.target(),
						column.sourceType()));
				model.addRow(new Object[] { table.source(), column.source(), table.target(), column.target(),
						column.sourceType(), column.primaryKey() ? "Yes" : "No", column.nullable() ? "Yes" : "No",
						column.targetDefinition() == null
								? column.previewIssue() == null ? "Not requested" : "Unavailable"
								: column.targetDefinition(),
						column.previewIssue() == null ? "" : column.previewIssue() });
			}
		long unavailable = result.tables().stream().flatMap(table -> table.columns().stream())
				.filter(column -> column.previewIssue() != null && !column.previewIssue().isBlank()).count();
		long available = result.tables().stream().flatMap(table -> table.columns().stream())
				.filter(column -> (column.previewIssue() == null || column.previewIssue().isBlank())
						&& column.targetDefinition() != null && !column.targetDefinition().isBlank())
				.count();
		hasTargetPreview = result.targetDialect() != null;
		hasPreviewIssues = unavailable > 0;
		multipleTables = result.tables().size() > 1;
		previewIssuesOnly.setSelected(false);
		previewIssuesOnly.setVisible(result.targetDialect() != null);
		previewIssuesOnly.setEnabled(result.targetDialect() != null && unavailable > 0);
		previewCount.setVisible(result.targetDialect() != null);
		previewCount.setText("All column previews: " + available + " available; " + unavailable + " unavailable");
		changingScope = true;
		try {
			sourceTable.removeAllItems();
			sourceTable.addItem(new TableChoice(null, null));
			result.tables().forEach(table -> sourceTable.addItem(new TableChoice(table.source(), table.target())));
			sourceTable.setSelectedIndex(0);
		} finally {
			changingScope = false;
		}
		tableScope.setVisible(result.tables().size() > 1);
		sourceTable.setEnabled(result.tables().size() > 1);
		details.setExactFilter(0, null);
		details.setNonBlankFilter(8, false);
		evidence.setText("Captured Schema mapping checked: " + result.tables().size() + " tables; "
				+ result.warnings().size() + " warnings. Database Review is still required.\n" + "Schema: "
				+ result.schemaFile() + "\n" + result.schemaFingerprint() + "\nMapping: " + result.mappingFingerprint()
				+ (result.warnings().isEmpty() ? "" : "\n" + String.join("\n", result.warnings())));
		if (result.targetDialect() != null)
			evidence.append("\nTarget builder: " + result.targetDialect() + "; unavailable column previews: "
					+ result.tables().stream().flatMap(table -> table.columns().stream())
							.filter(column -> column.previewIssue() != null && !column.previewIssue().isBlank()).count()
					+ ". Offline column definitions only; table factories may add or change syntax. Existing target columns, generated target-only keys and full CREATE/ALTER SQL are not represented. No value conversion or verification was performed.");
		evidence.setCaretPosition(0);
		captured = true;
		capturedResult = result;
		refreshEnabled(controlsEnabled);
		captureChanged.run();
	}

	void clear(String message) {
		captured = false;
		model.setRowCount(0);
		rowSelections.clear();
		previewIssuesOnly.setSelected(false);
		previewIssuesOnly.setEnabled(false);
		previewIssuesOnly.setVisible(false);
		previewCount.setText("");
		previewCount.setVisible(false);
		changingScope = true;
		try {
			sourceTable.removeAllItems();
			sourceTable.addItem(new TableChoice(null, null));
		} finally {
			changingScope = false;
		}
		sourceTable.setEnabled(false);
		tableScope.setVisible(false);
		details.setExactFilter(0, null);
		details.setNonBlankFilter(8, false);
		evidence.setText(message);
		captured = false;
		capturedResult = null;
		hasTargetPreview = false;
		hasPreviewIssues = false;
		multipleTables = false;
		refreshEnabled(controlsEnabled);
		captureChanged.run();
	}

	void changed() {
		if (captured)
			clear("Names, source Schema path or target preview changed. Use Check Schema again; database Review is still required.");
	}
}
