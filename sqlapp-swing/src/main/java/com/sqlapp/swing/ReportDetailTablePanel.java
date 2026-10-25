/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * Search and inspect recorded evidence without changing the underlying report.
 */
final class ReportDetailTablePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField filter = new JTextField();
	final JButton clear = new JButton("Clear");
	final JLabel count = new JLabel();
	final JTable table = new JTable();
	final JTextArea selected = new JTextArea();
	private final TableRowSorter<DefaultTableModel> sorter;

	ReportDetailTablePanel(final ReportReviewService.DetailTable section) {
		super(new BorderLayout(4, 4));
		final var note = new JTextArea(section.summary(), 3, 0);
		note.setEditable(false);
		note.setLineWrap(true);
		note.setWrapStyleWord(true);
		final var search = new JPanel(new BorderLayout(6, 0));
		final var label = new JLabel("Find in recorded details");
		label.setLabelFor(filter);
		search.add(label, BorderLayout.WEST);
		search.add(filter, BorderLayout.CENTER);
		final var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		actions.add(clear);
		actions.add(count);
		search.add(actions, BorderLayout.EAST);
		final var header = new JPanel(new BorderLayout(4, 4));
		header.add(new JScrollPane(note), BorderLayout.CENTER);
		header.add(search, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		final var model = new DefaultTableModel(section.columns().toArray(), 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
		section.rows().forEach(row -> model.addRow(row.toArray()));
		table.setModel(model);
		sorter = new TableRowSorter<>(model);
		for (final int column : section.numericColumns()) {
			sorter.setComparator(column,
					(left, right) -> Long.compare(Long.parseLong(left.toString()), Long.parseLong(right.toString())));
		}
		table.setRowSorter(sorter);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		for (int column = 0; column < table.getColumnCount(); column++) {
			table.getColumnModel().getColumn(column).setPreferredWidth(160);
		}
		final var scroll = new JScrollPane(table);
		scroll.setColumnHeaderView(table.getTableHeader());
		selected.setEditable(false);
		selected.setLineWrap(true);
		selected.setWrapStyleWord(true);
		selected.setRows(5);
		final var selectedScroll = new JScrollPane(selected);
		selectedScroll.setBorder(BorderFactory.createTitledBorder("Selected row — select text to copy"));
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, selectedScroll);
		split.setResizeWeight(0.65);
		add(split, BorderLayout.CENTER);
		table.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting())
				refreshSelection();
		});
		filter.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				refreshFilter();
			}

			public void removeUpdate(final DocumentEvent event) {
				refreshFilter();
			}

			public void changedUpdate(final DocumentEvent event) {
				refreshFilter();
			}
		});
		clear.addActionListener(event -> {
			filter.setText("");
			filter.requestFocusInWindow();
		});
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control F"), "find");
		getActionMap().put("find", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent event) {
				filter.requestFocusInWindow();
				filter.selectAll();
			}
		});
		refreshFilter();
	}

	private void refreshFilter() {
		final String query = filter.getText().toLowerCase(Locale.ROOT);
		table.clearSelection();
		sorter.setRowFilter(query.isEmpty() ? null : new RowFilter<DefaultTableModel, Integer>() {
			@Override
			public boolean include(final Entry<? extends DefaultTableModel, ? extends Integer> entry) {
				for (int column = 0; column < entry.getValueCount(); column++) {
					if (entry.getStringValue(column).toLowerCase(Locale.ROOT).contains(query))
						return true;
				}
				return false;
			}
		});
		count.setText(table.getRowCount() + " / " + table.getModel().getRowCount() + " recorded rows");
		clear.setEnabled(!query.isEmpty());
		refreshSelection();
	}

	private void refreshSelection() {
		final int row = table.getSelectedRow();
		if (row < 0) {
			selected.setText("Select a recorded row to inspect its full values.");
		} else {
			final int modelRow = table.convertRowIndexToModel(row);
			final var model = table.getModel();
			final var text = new StringBuilder();
			for (int column = 0; column < model.getColumnCount(); column++) {
				if (column > 0)
					text.append('\n');
				text.append(model.getColumnName(column)).append(": ").append(model.getValueAt(modelRow, column));
			}
			selected.setText(text.toString());
		}
		selected.setCaretPosition(0);
	}
}
