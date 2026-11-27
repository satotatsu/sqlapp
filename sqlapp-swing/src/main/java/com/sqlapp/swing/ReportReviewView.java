/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/** Shared presentation for generated plans and saved migration reports. */
final class ReportReviewView extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextArea summary = readOnlyText();
	final RSyntaxTextArea details = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_JSON, false);
	private final ReadOnlyCodePanel json = new ReadOnlyCodePanel(details);
	final EditorNavigationBar navigation = json.navigation;
	final EditorPositionLabel position = json.position;
	final JTable tasks = new JTable();
	final ReportDetailTablePanel taskTable = new ReportDetailTablePanel(tasks, null, "Filter tasks / migrations",
			"rows");
	final JTabbedPane tabs = new JTabbedPane();
	final JComboBox<String> comparisonScope = new JComboBox<>(
			new String[] { "All tables", "Target differs", "Staging changed", "Target differs and staging changed" });
	private final JPanel comparisonControls = new JPanel(new FlowLayout(FlowLayout.LEFT));

	ReportReviewView() {
		super(new BorderLayout());
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		summary.setRows(7);
		tasks.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		comparisonControls.add(new JLabel("Show"));
		comparisonControls.add(comparisonScope);
		comparisonControls.setVisible(false);
		taskTable.setScopeControls(comparisonControls);
		comparisonScope.addActionListener(event -> filterComparison());
		tabs.addTab("Tasks / migrations", taskTable);
		tabs.addTab("Raw JSON", json);
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(summary), tabs);
		split.setResizeWeight(0.3);
		add(split, BorderLayout.CENTER);
	}

	void clear() {
		comparisonControls.setVisible(false);
		comparisonScope.setSelectedIndex(0);
		summary.setText("");
		resetDetails("");
		tasks.setModel(new DefaultTableModel());
		resetDetailTabs();
	}

	void showReview(final ReportReviewService.Review review) {
		resetDetailTabs();
		summary.setText(review.summary());
		summary.setCaretPosition(0);
		resetDetails(review.details());
		showTable(tasks, review.columns(), review.rows());
		taskTable.setNumericColumns(review.numericColumns());
		comparisonControls.setVisible(
				review.columns().contains("Target vs staging") && review.columns().contains("Staging vs recorded"));
		comparisonScope.setSelectedIndex(0);
		filterComparison();
		for (final var section : review.tables()) {
			var panel = new ReportDetailTablePanel(section);
			int change = section.columns().indexOf("Change");
			if (section.title().equals("Changes since previous comparison") && change >= 0)
				panel.setScopeControls(new StagingComparisonHistoryFilters(panel, change));
			tabs.insertTab(section.title(), null, panel, null, tabs.getTabCount() - 1);
		}
	}

	private void filterComparison() {
		if (!comparisonControls.isVisible())
			return;
		var filters = new java.util.HashMap<Integer, String>();
		int selected = comparisonScope.getSelectedIndex();
		if (selected == 1 || selected == 3)
			filters.put(tasks.getColumn("Target vs staging").getModelIndex(), "Different");
		if (selected == 2 || selected == 3)
			filters.put(tasks.getColumn("Staging vs recorded").getModelIndex(), "Changed");
		taskTable.setExactFilters(filters);
	}

	private void resetDetailTabs() {
		tabs.setSelectedIndex(0);
		while (tabs.getTabCount() > 2)
			tabs.removeTabAt(1);
	}

	private void resetDetails(final String text) {
		json.showText(text);
	}

	private static void showTable(final JTable table, final java.util.List<String> columns,
			final java.util.List<java.util.List<String>> rows) {
		final var model = new DefaultTableModel(columns.toArray(), 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
		rows.forEach(row -> model.addRow(row.toArray()));
		table.setModel(model);
		for (int column = 0; column < table.getColumnCount(); column++) {
			table.getColumnModel().getColumn(column).setPreferredWidth(160);
		}
	}

	private static JTextArea readOnlyText() {
		final var area = new JTextArea();
		area.setEditable(false);
		return area;
	}
}
