/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import com.sqlapp.data.db.command.migration.transfer.TransferNameRules;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.Result;
import java.util.concurrent.Callable;
import java.util.function.Function;

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
	final JButton naming = new JButton("Naming rules…");
	final JButton undoRules = new JButton("Undo last rules");
	private java.util.List<NameChange> undo;
	private Function<java.util.List<NameChange>, Callable<Result>> ruleInspector;

	void setRuleInspector(Function<java.util.List<NameChange>, Callable<Result>> inspector) {
		ruleInspector = inspector;
	}

	void checkRulePreview(java.util.List<NameChange> changes) {
		finishEdit();
		checkRows(changes);
	}

	record NameChange(int row, java.util.List<Object> before, String currentName, String proposedName) {
		NameChange {
			before = Collections.unmodifiableList(new ArrayList<>(before));
		}

		boolean changed() {
			return !currentName.equals(proposedName);
		}
	}

	private final JTable grid;
	private final DefaultTableModel model;
	private final int sourceColumn;
	private final int targetColumn;
	private final boolean blankUnchanged;
	private final TableRowSorter<DefaultTableModel> sorter;
	private boolean refreshPending;
	private String sourceTable;

	void showSourceTable(String sourceTable) {
		finishEdit();
		this.sourceTable = sourceTable;
		refreshFilter();
	}

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
		buttons.add(naming);
		buttons.add(undoRules);
		add(buttons, BorderLayout.SOUTH);
		remove.setToolTipText(
				"Removes editor rows only. Tables and columns are still transferred using the saved job selection.");
		reset.setToolTipText("Restore source names for selected rows; keep source metadata and other overrides.");
		naming.setToolTipText("Preview case/space rules for selected target names only.");
		undoRules.setToolTipText(
				"Restore the last naming batch only if its affected rows have not changed since applying it.");
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
			var values = new String[model.getColumnCount()];
			values[0] = sourceTable;
			model.addRow(values);
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
		naming.addActionListener(event -> {
			var dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Naming rules for selected rows",
					Dialog.ModalityType.APPLICATION_MODAL);
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			var panel = new TransferNameRulesPanel(this, dialog::dispose, ruleInspector);
			dialog.setContentPane(panel);
			dialog.addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosing(java.awt.event.WindowEvent event) {
					panel.requestClose();
				}
			});
			dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),
					"cancelRules");
			dialog.getRootPane().getActionMap().put("cancelRules", new AbstractAction() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent event) {
					panel.requestClose();
				}
			});
			dialog.setSize(940, 650);
			dialog.setLocationRelativeTo(this);
			dialog.setVisible(true);
		});
		undoRules.addActionListener(event -> {
			try {
				undoRules();
			} catch (IllegalArgumentException failure) {
				JOptionPane.showMessageDialog(this, failure.getMessage(), "Names changed", JOptionPane.WARNING_MESSAGE);
			}
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
		if (grid.isEditing() && !grid.getCellEditor().stopCellEditing())
			throw new IllegalArgumentException("Finish editing the current name first.");
	}

	java.util.List<NameChange> previewRules(TransferNameRules rules) {
		finishEdit();
		var changes = new ArrayList<NameChange>();
		for (int row : selectedModelRows()) {
			new TransferNameMappingIO.Configuration(1,
					Map.of(value(row, 0), new TransferNameMappingIO.TableMapping(null, Map.of())));
			String current = value(row, targetColumn);
			if (blankUnchanged && current.isBlank()) {
				String source = value(row, sourceColumn);
				current = source.substring(source.lastIndexOf('.') + 1);
			}
			String proposed = rules.apply(current);
			if (!blankUnchanged)
				new TransferNameMappingIO.TableMapping(null, Map.of(value(row, sourceColumn), proposed));
			validateName(proposed);
			changes.add(new NameChange(row, rowValues(row), current, proposed));
		}
		if (changes.isEmpty())
			throw new IllegalArgumentException("Select table or column rows first.");
		return java.util.List.copyOf(changes);
	}

	private java.util.List<Object> rowValues(int row) {
		var values = new ArrayList<Object>();
		for (int column = 0; column < model.getColumnCount(); column++)
			values.add(model.getValueAt(row, column));
		return values;
	}

	private void validateName(String name) {
		new TransferNameMappingIO.TableMapping(blankUnchanged ? name : null,
				blankUnchanged ? Map.of() : Map.of("source", name));
	}

	private void checkRows(java.util.List<NameChange> changes) {
		var seen = new HashSet<Integer>();
		for (var change : changes) {
			if (change.row() < 0 || change.row() >= model.getRowCount() || !seen.add(change.row())
					|| !change.before().equals(rowValues(change.row())))
				throw new IllegalArgumentException(
						"Mapping rows changed. Refresh the naming preview before applying or undoing rules.");
		}
	}

	void applyRules(java.util.List<NameChange> changes) {
		finishEdit();
		checkRows(changes);
		for (var change : changes)
			validateName(change.proposedName());
		var applied = changes.stream().filter(NameChange::changed).toList();
		for (var change : applied) {
			model.setValueAt(change.proposedName(), change.row(), targetColumn);
		}
		if (!applied.isEmpty()) {
			// Keep original raw values too, so a blank table target can be restored as
			// blank.
			undo = applied.stream().map(change -> new NameChange(change.row(), rowValues(change.row()),
					change.currentName(),
					change.before().get(targetColumn) == null ? "" : change.before().get(targetColumn).toString()))
					.toList();
		}
		refreshCounts();
	}

	void undoRules() {
		finishEdit();
		if (undo == null)
			return;
		checkRows(undo);
		for (var change : undo)
			model.setValueAt(change.proposedName(), change.row(), targetColumn);
		undo = null;
		refreshCounts();
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
				if (sourceTable != null && !sourceTable.equals(entry.getStringValue(0)))
					return false;
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
		naming.setEnabled(reset.isEnabled());
		undoRules.setEnabled(grid.isEnabled() && undo != null);
	}
}
