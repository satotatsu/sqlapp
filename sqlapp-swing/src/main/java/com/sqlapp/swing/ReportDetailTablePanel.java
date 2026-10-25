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
	final JButton copyRows = new JButton("Copy visible rows");
	final JButton copySelected = new JButton("Copy selected rows");
	final JLabel copyStatus = new JLabel();
	final JLabel count = new JLabel();
	final JTable table;
	final JTextArea selected = new JTextArea();
	private TableRowSorter<DefaultTableModel> sorter;
	private DefaultTableModel observedModel;
	private final javax.swing.event.TableModelListener modelListener = event -> {
		final var source = event.getSource();
		// JTable must finish updating its row mapping before inspecting visible rows.
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (source == observedModel) {
				refreshCounts();
				refreshSelection();
				resetCopyFeedback();
			}
		});
	};
	private final String rowNoun;
	private final java.util.function.Consumer<String> clipboard;

	ReportDetailTablePanel(final ReportReviewService.DetailTable section) {
		this(section, ReportDetailTablePanel::copyToClipboard);
	}

	ReportDetailTablePanel(final ReportReviewService.DetailTable section,
			final java.util.function.Consumer<String> clipboard) {
		this(new JTable(), section.summary(), "Find in recorded details", "recorded rows", clipboard);
		final var model = new DefaultTableModel(section.columns().toArray(), 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
		section.rows().forEach(row -> model.addRow(row.toArray()));
		table.setModel(model);
		setNumericColumns(section.numericColumns());
		for (int column = 0; column < table.getColumnCount(); column++)
			table.getColumnModel().getColumn(column).setPreferredWidth(160);
		refreshFilter();
	}

	ReportDetailTablePanel(final JTable table, final String summary, final String filterLabel, final String rowNoun) {
		this(table, summary, filterLabel, rowNoun, ReportDetailTablePanel::copyToClipboard);
	}

	ReportDetailTablePanel(final JTable table, final String summary, final String filterLabel, final String rowNoun,
			final java.util.function.Consumer<String> clipboard) {
		super(new BorderLayout(4, 4));
		this.table = java.util.Objects.requireNonNull(table);
		this.rowNoun = rowNoun;
		this.clipboard = java.util.Objects.requireNonNull(clipboard);
		final var search = new JPanel(new BorderLayout(6, 0));
		final var label = new JLabel(filterLabel);
		label.setLabelFor(filter);
		search.add(label, BorderLayout.WEST);
		search.add(filter, BorderLayout.CENTER);
		final var actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		actions.add(clear);
		copyRows.setToolTipText("Copy all visible rows with headers in the current row/column order, up to 1 MiB.");
		copySelected.setToolTipText("Copy selected rows with headers in the current row/column order, up to 1 MiB.");
		actions.add(count);
		search.add(actions, BorderLayout.EAST);
		final var header = new JPanel(new BorderLayout(4, 4));
		if (summary != null) {
			final var note = new JTextArea(summary, 3, 0);
			note.setEditable(false);
			note.setLineWrap(true);
			note.setWrapStyleWord(true);
			header.add(new JScrollPane(note), BorderLayout.CENTER);
		}
		final var tools = new JPanel(new BorderLayout(4, 4));
		tools.add(search, BorderLayout.NORTH);
		final var copyActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		copyActions.add(copyRows);
		copyActions.add(copySelected);
		tools.add(copyActions, BorderLayout.CENTER);
		tools.add(copyStatus, BorderLayout.SOUTH);
		copyStatus.setVisible(false);
		header.add(tools, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		table.setAutoCreateRowSorter(false);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		for (int column = 0; column < table.getColumnCount(); column++) {
			table.getColumnModel().getColumn(column).setPreferredWidth(160);
		}
		final var scroll = new JScrollPane(table);
		scroll.setColumnHeaderView(table.getTableHeader());
		selected.setEditable(false);
		selected.setLineWrap(true);
		selected.setWrapStyleWord(true);
		selected.setRows(summary == null ? 3 : 5);
		final var selectedScroll = new JScrollPane(selected);
		selectedScroll.setBorder(BorderFactory.createTitledBorder("Selected row — select text to copy"));
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, selectedScroll);
		split.setResizeWeight(0.65);
		add(split, BorderLayout.CENTER);
		table.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting()) {
				refreshSelection();
				resetCopyFeedback();
			}
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
		copyRows.addActionListener(event -> copyRows(false));
		copySelected.addActionListener(event -> copyRows(true));
		for (final String keys : new String[] { "control F", "meta F" })
			getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(keys), "find");
		getActionMap().put("find", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent event) {
				filter.requestFocusInWindow();
				filter.selectAll();
			}
		});
		table.addPropertyChangeListener("model", event -> resetModel());
		resetModel();
	}

	private void resetModel() {
		if (observedModel != null)
			observedModel.removeTableModelListener(modelListener);
		observedModel = (DefaultTableModel) table.getModel();
		sorter = new TableRowSorter<>((DefaultTableModel) table.getModel());
		sorter.setSortsOnUpdates(true);
		table.setRowSorter(sorter);
		observedModel.addTableModelListener(modelListener);
		sorter.addRowSorterListener(event -> {
			refreshCounts();
			resetCopyFeedback();
		});
		filter.setText("");
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
		clear.setEnabled(!query.isEmpty());
		refreshCounts();
		resetCopyFeedback();
		refreshSelection();
	}

	private void refreshCounts() {
		count.setText(table.getRowCount() + " / " + table.getModel().getRowCount() + " " + rowNoun);
		copyRows.setEnabled(table.getRowCount() > 0 && table.getColumnCount() > 0);
	}

	void setNumericColumns(final java.util.List<Integer> columns) {
		for (final int column : columns) {
			sorter.setComparator(column, (left, right) -> {
				final String a = left.toString(), b = right.toString();
				if (a.equals("not recorded"))
					return b.equals("not recorded") ? 0 : 1;
				if (b.equals("not recorded"))
					return -1;
				return Long.compare(Long.parseLong(a), Long.parseLong(b));
			});
		}
	}

	private static void copyToClipboard(final String text) {
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new java.awt.datatransfer.StringSelection(text), null);
	}

	private void resetCopyFeedback() {
		copyStatus.setText("");
		copyStatus.setToolTipText(null);
		copyStatus.setVisible(false);
		revalidate();
	}

	private void copyRows(final boolean selectedOnly) {
		if (!(selectedOnly ? copySelected : copyRows).isEnabled())
			return;
		try {
			final String text = selectedOnly ? ReportTableClipboard.formatSelected(table)
					: ReportTableClipboard.format(table);
			if (text.isEmpty())
				return;
			clipboard.accept(text);
			copyStatus.setText("Copied "
					+ (selectedOnly ? table.getSelectedRowCount() + " selected" : table.getRowCount() + " visible")
					+ " rows with headers. The saved report is unchanged.");
			copyStatus.setToolTipText(copyStatus.getText());
		} catch (final RuntimeException failure) {
			copyStatus.setText("Copy failed: "
					+ (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
			copyStatus.setToolTipText(EditorStatusPanel.failureDetails(failure));
		}
		copyStatus.setVisible(true);
		revalidate();
		repaint();
	}

	private void refreshSelection() {
		copySelected.setEnabled(table.getSelectedRowCount() > 0 && table.getColumnCount() > 0);
		final int row = table.getSelectedRow();
		if (row < 0) {
			selected.setText("Select a recorded row to inspect its full values.");
		} else {
			final int modelRow = table.convertRowIndexToModel(row);
			final var model = table.getModel();
			final var text = new StringBuilder();
			if (table.getSelectedRowCount() > 1)
				text.append(table.getSelectedRowCount()).append(" rows selected; showing the first selected row.\n\n");
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
