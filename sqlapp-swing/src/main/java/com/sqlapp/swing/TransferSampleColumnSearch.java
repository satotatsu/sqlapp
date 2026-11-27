/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.Locale;
import javax.swing.*;

/** Header navigation only; never filters rows or reloads package files. */
final class TransferSampleColumnSearch extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField query = new JTextField(20);
	final JButton next = new JButton("Next column");
	final JButton previous = new JButton("Previous column");
	final JLabel status = new JLabel();
	final JLabel matches = new JLabel();
	final JButton details = new JButton("Column details…");
	final JButton columns = new JButton("Columns…");
	private int lastModel = -1;
	private String lastQuery = "";
	private final JTable table;
	private final boolean detailsAvailable;
	private Runnable columnsAction;

	void setColumnsAction(Runnable action) {
		columnsAction = action;
		columns.setVisible(action != null);
		revalidate();
	}

	void revealColumn(int modelColumn) {
		if (!table.isEnabled())
			return;
		if (modelColumn <= 0 || modelColumn >= table.getModel().getColumnCount()
				|| table.convertColumnIndexToView(modelColumn) < 0)
			throw new IllegalArgumentException("Choose a visible packaged data column.");
		TransferSampleTableLayout.scrollToColumn(table, modelColumn);
		query.setText("");
		lastQuery = "";
		lastModel = modelColumn;
		matches.setText("");
		status.setText("Column " + (table.convertColumnIndexToView(modelColumn) + 1) + " of " + table.getColumnCount());
		status.setToolTipText("Column: " + table.getModel().getColumnName(modelColumn));
		details.setEnabled(detailsAvailable);
	}

	TransferSampleColumnSearch(JTable table) {
		this(table, null);
	}

	TransferSampleColumnSearch(JTable table, java.util.function.IntConsumer showDetails) {
		this(table, showDetails, null);
	}

	TransferSampleColumnSearch(JTable table, java.util.function.IntConsumer showDetails, Runnable showColumns) {
		super(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		this.table = table;
		detailsAvailable = showDetails != null;
		columnsAction = showColumns;
		var label = new JLabel("Find column");
		label.setLabelFor(query);
		add(label);
		add(query);
		add(previous);
		add(next);
		add(status);
		add(matches);
		if (showDetails != null)
			add(details);
		add(columns);
		columns.setVisible(showColumns != null);
		columns.addActionListener(event -> {
			if (table.isEnabled() && columnsAction != null)
				columnsAction.run();
		});
		details.setEnabled(false);
		details.addActionListener(event -> {
			if (table.isEnabled() && lastModel > 0 && lastModel < table.getModel().getColumnCount()
					&& showDetails != null)
				showDetails.accept(lastModel);
		});
		query.setToolTipText(
				"Literal, case-insensitive search of displayed column names, requested target names and source types. Does not filter rows.");
		table.addPropertyChangeListener("enabled", event -> {
			query.setEnabled(table.isEnabled());
			next.setEnabled(table.isEnabled());
			previous.setEnabled(table.isEnabled());
			columns.setEnabled(table.isEnabled());
			details.setEnabled(table.isEnabled() && lastModel > 0 && detailsAvailable);
		});
		query.setEnabled(table.isEnabled());
		next.setEnabled(table.isEnabled());
		previous.setEnabled(table.isEnabled());
		columns.setEnabled(table.isEnabled());
		java.util.function.IntConsumer find = direction -> {
			if (!table.isEnabled())
				return;
			String text = query.getText().toLowerCase(Locale.ROOT);
			if (text.isBlank()) {
				lastModel = -1;
				lastQuery = "";
				status.setText("Enter a column name.");
				status.setToolTipText(null);
				details.setEnabled(false);
				matches.setText("");
				return;
			}
			int count = table.getColumnCount();
			int start = text.equals(lastQuery) && lastModel >= 0 ? table.convertColumnIndexToView(lastModel)
					: direction > 0 ? -1 : count;
			lastQuery = text;
			var matchingViews = new java.util.ArrayList<Integer>();
			var matching = new boolean[count];
			for (int view = 0; view < count; view++) {
				int model = table.convertColumnIndexToModel(view);
				if (model != 0 && table.getModel().getColumnName(model).toLowerCase(Locale.ROOT).contains(text)) {
					matching[view] = true;
					matchingViews.add(view);
				}
			}
			for (int offset = 1; offset <= count; offset++) {
				int view = Math.floorMod(start + direction * offset, count);
				int model = table.convertColumnIndexToModel(view);
				if (!matching[view])
					continue;
				lastModel = model;
				details.setEnabled(showDetails != null);
				TransferSampleTableLayout.scrollToColumn(table, model);
				status.setText("Column " + (view + 1) + " of " + count);
				status.setToolTipText("Column: " + table.getModel().getColumnName(model));
				matches.setText("Match " + (java.util.Collections.binarySearch(matchingViews, view) + 1) + " / "
						+ matchingViews.size());
				return;
			}
			lastModel = -1;
			details.setEnabled(false);
			status.setText("No matching column.");
			status.setToolTipText(null);
			matches.setText("0 matches");
		};
		next.addActionListener(event -> find.accept(1));
		previous.addActionListener(event -> find.accept(-1));
		query.addActionListener(event -> find.accept(1));
		query.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "previousColumn");
		query.getActionMap().put("previousColumn", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				find.accept(-1);
			}
		});
	}
}
