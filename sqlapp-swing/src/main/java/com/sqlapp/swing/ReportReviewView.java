/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.Font;

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
	final JTextArea details = readOnlyText();
	final JTable tasks = new JTable();

	ReportReviewView() {
		super(new BorderLayout());
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		summary.setRows(7);
		details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		tasks.setAutoCreateRowSorter(true);
		tasks.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		final var tableScroll = new JScrollPane(tasks);
		tableScroll.setColumnHeaderView(tasks.getTableHeader());
		final var tabs = new JTabbedPane();
		tabs.addTab("Tasks / migrations", tableScroll);
		tabs.addTab("Parsed report JSON", new JScrollPane(details));
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(summary), tabs);
		split.setResizeWeight(0.3);
		add(split, BorderLayout.CENTER);
	}

	void clear() {
		summary.setText("");
		details.setText("");
		tasks.setModel(new DefaultTableModel());
	}

	void showReview(final ReportReviewService.Review review) {
		summary.setText(review.summary());
		summary.setCaretPosition(0);
		details.setText(review.details());
		details.setCaretPosition(0);
		final var model = new DefaultTableModel(review.columns().toArray(), 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
		review.rows().forEach(row -> model.addRow(row.toArray()));
		tasks.setModel(model);
		for (int column = 0; column < tasks.getColumnCount(); column++) {
			tasks.getColumnModel().getColumn(column).setPreferredWidth(160);
		}
	}

	private static JTextArea readOnlyText() {
		final var area = new JTextArea();
		area.setEditable(false);
		return area;
	}
}
