/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.awt.BorderLayout;

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
	final JTable tasks = new JTable();
	final ReportDetailTablePanel taskTable = new ReportDetailTablePanel(tasks, null, "Filter tasks / migrations",
			"rows");
	final JTabbedPane tabs = new JTabbedPane();

	ReportReviewView() {
		super(new BorderLayout());
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		summary.setRows(7);
		tasks.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		tabs.addTab("Tasks / migrations", taskTable);
		tabs.addTab("Parsed report JSON", CodeEditors.scroll(details));
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(summary), tabs);
		split.setResizeWeight(0.3);
		add(split, BorderLayout.CENTER);
	}

	void clear() {
		summary.setText("");
		details.setText("");
		tasks.setModel(new DefaultTableModel());
		resetDetailTabs();
	}

	void showReview(final ReportReviewService.Review review) {
		resetDetailTabs();
		summary.setText(review.summary());
		summary.setCaretPosition(0);
		details.setText(review.details());
		details.setCaretPosition(0);
		showTable(tasks, review.columns(), review.rows());
		taskTable.setNumericColumns(review.numericColumns());
		for (final var section : review.tables()) {
			tabs.insertTab(section.title(), null, new ReportDetailTablePanel(section), null, tabs.getTabCount() - 1);
		}
	}

	private void resetDetailTabs() {
		tabs.setSelectedIndex(0);
		while (tabs.getTabCount() > 2)
			tabs.removeTabAt(1);
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
