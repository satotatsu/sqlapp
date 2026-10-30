/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

/** Search and edit a mapping without confusing view rows with source rows. */
final class TransferNameMappingGrid extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField filter = new JTextField(20);
	final JCheckBox renamedOnly = new JCheckBox("Renamed only");
	final JLabel count = new JLabel();
	final JButton add = new JButton("Add row");
	final JButton remove = new JButton("Remove selected overrides");
	final JButton reset = new JButton("Reset selected names");
	final JButton clear = new JButton("Clear filter");
	private final JTable grid;
	private final DefaultTableModel model;
	private final int sourceColumn;
	private final int targetColumn;
	private final boolean blankUnchanged;
	private final TableRowSorter<DefaultTableModel> sorter;
	private boolean refreshPending;

	TransferNameMappingGrid(JTable grid, DefaultTableModel model, int sourceColumn, int targetColumn,
			boolean blankUnchanged) {
		super(new BorderLayout(4, 4));
		this.grid = grid;
		this.model = model;
		this.sourceColumn = sourceColumn;
		this.targetColumn = targetColumn;
		this.blankUnchanged = blankUnchanged;
		sorter = new TableRowSorter<>(model);
		sorter.setSortsOnUpdates(true);
		grid.setRowSorter(sorter);
		grid.setFillsViewportHeight(true);
		grid.putClientProperty("terminateEditOnFocusLost", true);
		var search = new JPanel(new FlowLayout(FlowLayout.LEFT));
		var label = new JLabel("Find");
		label.setLabelFor(filter);
		filter.setToolTipText("Literal, case-insensitive search across source names, target names and source types.");
		search.add(label);
		search.add(filter);
		search.add(renamedOnly);
		search.add(clear);
		search.add(count);
		add(search, BorderLayout.NORTH);
		add(new JScrollPane(grid), BorderLayout.CENTER);
		var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttons.add(add);
		buttons.add(reset);
		buttons.add(remove);
		add(buttons, BorderLayout.SOUTH);
		remove.setToolTipText(
				"Removes editor rows only. Tables and columns are still transferred using the saved job selection.");
		reset.setToolTipText("Restore source names for selected rows; keep source metadata and other overrides.");
		filter.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				refreshFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				refreshFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				refreshFilter();
			}
		});
		renamedOnly.addActionListener(event -> refreshFilter());
		clear.addActionListener(event -> {
			finishEdit();
			filter.setText("");
			renamedOnly.setSelected(false);
			refreshFilter();
		});
		add.addActionListener(event -> {
			finishEdit();
			filter.setText("");
			renamedOnly.setSelected(false);
			refreshFilter();
			int row = model.getRowCount();
			model.addRow(new String[model.getColumnCount()]);
			int visible = grid.convertRowIndexToView(row);
			grid.setRowSelectionInterval(visible, visible);
			grid.scrollRectToVisible(grid.getCellRect(visible, 0, true));
			grid.editCellAt(visible, 0);
			if (grid.getEditorComponent() != null)
				grid.getEditorComponent().requestFocusInWindow();
		});
		remove.addActionListener(event -> {
			finishEdit();
			int[] selected = selectedModelRows();
			Arrays.sort(selected);
			for (int i = selected.length - 1; i >= 0; i--)
				model.removeRow(selected[i]);
			refreshCounts();
		});
		reset.addActionListener(event -> {
			finishEdit();
			// Capture every source row before updates can reorder or hide selected rows.
			int[] selected = selectedModelRows();
			for (int row : selected)
				model.setValueAt(blankUnchanged ? null : value(row, sourceColumn), row, targetColumn);
			refreshCounts();
		});
		grid.getSelectionModel().addListSelectionListener(event -> refreshActions());
		sorter.addRowSorterListener(event -> scheduleCounts());
		model.addTableModelListener(event -> scheduleCounts());
		refreshFilter();
	}

	private void scheduleCounts() {
		if (refreshPending)
			return;
		refreshPending = true;
		SwingUtilities.invokeLater(() -> {
			refreshPending = false;
			refreshCounts();
		});
	}

	private void finishEdit() {
		if (grid.isEditing())
			grid.getCellEditor().stopCellEditing();
	}

	private int[] selectedModelRows() {
		return Arrays.stream(grid.getSelectedRows()).map(grid::convertRowIndexToModel).toArray();
	}

	private boolean renamed(int row) {
		String source = value(row, sourceColumn), target = value(row, targetColumn);
		return !source.isBlank() && !target.isBlank() && !source.equals(target);
	}

	private String value(int row, int column) {
		var value = model.getValueAt(row, column);
		return value == null ? "" : value.toString();
	}

	private void refreshFilter() {
		finishEdit();
		String text = filter.getText().toLowerCase(Locale.ROOT);
		boolean changed = renamedOnly.isSelected();
		sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
			@Override
			public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
				if (changed && !renamed(entry.getIdentifier()))
					return false;
				for (int column = 0; column < entry.getValueCount(); column++)
					if (entry.getStringValue(column).toLowerCase(Locale.ROOT).contains(text))
						return true;
				return false;
			}
		});
		refreshCounts();
	}

	void refreshCounts() {
		int renamed = 0;
		for (int row = 0; row < model.getRowCount(); row++)
			if (renamed(row))
				renamed++;
		count.setText(grid.getRowCount() + " / " + model.getRowCount() + " shown; " + renamed + " renamed");
		refreshActions();
	}

	private void refreshActions() {
		reset.setEnabled(grid.isEnabled() && grid.getSelectedRowCount() > 0);
		remove.setEnabled(reset.isEnabled());
	}
}
