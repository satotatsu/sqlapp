/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.sqlapp.data.db.command.migration.transfer.TransferNameRules;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.Result;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Preview selected names; resolved overrides use the existing mapping format.
 */
final class TransferNameRulesPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JComboBox<TransferNameRules.NameCase> letterCase = new JComboBox<>(TransferNameRules.NameCase.values());
	final JCheckBox spaces = new JCheckBox("Replace spaces with underscores");
	final JButton preview = new JButton("Preview selected names");
	final JButton apply = new JButton("Apply preview");
	final JButton cancel = new JButton("Cancel");
	final JButton checkSchema = new JButton("Check proposed Schema");
	final TransferNameMappingSchemaPanel schemaCheck = new TransferNameMappingSchemaPanel();
	final JTabbedPane tabs = new JTabbedPane();
	final JLabel status = new JLabel(
			"Selected rows only. Preview changes before applying; Check Schema and database Review remain required.");
	final DefaultTableModel model = new DefaultTableModel(
			new String[] { "Source identity", "Current target name", "Proposed target name" }, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private java.util.List<TransferNameMappingGrid.NameChange> changes;
	private final Runnable closed;
	private final TransferNameMappingGrid grid;
	private final Function<java.util.List<TransferNameMappingGrid.NameChange>, Callable<Result>> inspector;
	private boolean busy;
	private long revision;

	boolean isBusy() {
		return busy;
	}

	void requestClose() {
		if (!busy)
			closed.run();
	}

	TransferNameRulesPanel(TransferNameMappingGrid grid, Runnable closed) {
		this(grid, closed, null);
	}

	TransferNameRulesPanel(TransferNameMappingGrid grid, Runnable closed,
			Function<java.util.List<TransferNameMappingGrid.NameChange>, Callable<Result>> inspector) {
		super(new BorderLayout(6, 6));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		this.grid = grid;
		this.closed = closed;
		this.inspector = inspector;
		var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		var label = new JLabel("Target name case");
		label.setLabelFor(letterCase);
		controls.add(label);
		controls.add(letterCase);
		controls.add(spaces);
		controls.add(preview);
		add(controls, BorderLayout.NORTH);
		var table = new JTable(model);
		table.setFillsViewportHeight(true);
		tabs.addTab("Proposed names", new JScrollPane(table));
		tabs.addTab("Schema check", schemaCheck);
		add(tabs, BorderLayout.CENTER);
		var bottom = new JPanel(new BorderLayout());
		bottom.add(status, BorderLayout.NORTH);
		var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(checkSchema);
		actions.add(apply);
		actions.add(cancel);
		bottom.add(actions, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);
		apply.setEnabled(false);
		Runnable invalidated = () -> {
			revision++;
			changes = null;
			model.setRowCount(0);
			apply.setEnabled(false);
			schemaCheck.clear("Rules changed. Preview again before checking the proposed Schema.");
			refreshControls();
			status.setText("Rules changed. Preview the selected names again; no mapping was changed.");
		};
		letterCase.addActionListener(event -> invalidated.run());
		spaces.addActionListener(event -> invalidated.run());
		cancel.addActionListener(event -> requestClose());
		checkSchema.addActionListener(event -> inspectProposed());
		preview.addActionListener(event -> {
			try {
				changes = grid.previewRules(new TransferNameRules(
						(TransferNameRules.NameCase) letterCase.getSelectedItem(), spaces.isSelected()));
				revision++;
				schemaCheck.clear("Proposed names have not been checked against the captured Schema yet.");
				tabs.setSelectedIndex(0);
				model.setRowCount(0);
				int changed = 0;
				for (var change : changes) {
					String source = change.before().get(0).toString();
					if (change.before().size() > 2)
						source += " / " + change.before().get(1);
					model.addRow(new Object[] { source, change.currentName(), change.proposedName() });
					if (change.changed())
						changed++;
				}
				apply.setEnabled(changed > 0);
				status.setText(changed + " of " + changes.size()
						+ " selected names will change. Check proposed Schema before applying; database Review remains required.");
				refreshControls();
			} catch (IllegalArgumentException failure) {
				invalidated.run();
				status.setText(failure.getMessage());
			}
		});
		apply.addActionListener(event -> {
			if (changes == null)
				return;
			try {
				grid.applyRules(changes);
				closed.run();
			} catch (IllegalArgumentException failure) {
				invalidated.run();
				status.setText(failure.getMessage());
			}
		});
		refreshControls();
	}

	private void refreshControls() {
		letterCase.setEnabled(!busy);
		spaces.setEnabled(!busy);
		preview.setEnabled(!busy);
		cancel.setEnabled(!busy);
		apply.setEnabled(
				!busy && changes != null && changes.stream().anyMatch(TransferNameMappingGrid.NameChange::changed));
		checkSchema.setEnabled(!busy && inspector != null && changes != null);
	}

	private void inspectionFailed(Exception failure) {
		var cause = failure instanceof java.util.concurrent.ExecutionException ? failure.getCause() : failure;
		schemaCheck.clear("Proposed Schema check failed: "
				+ (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
		status.setText(
				"Proposed check failed. Inspect Schema check; you may apply as a draft, then correct before database Review.");
		tabs.setSelectedIndex(1);
	}

	private void inspectProposed() {
		if (busy || changes == null || inspector == null)
			return;
		final Callable<Result> task;
		final var inspected = changes;
		final long token = revision;
		try {
			task = java.util.Objects.requireNonNull(inspector.apply(inspected));
		} catch (RuntimeException failure) {
			inspectionFailed(failure);
			return;
		}
		busy = true;
		refreshControls();
		tabs.setSelectedIndex(1);
		schemaCheck.clear("Checking proposed mapping against captured Schema without modifying the editor…");
		status.setText("Checking proposed names. No database is accessed or modified.");
		new SwingWorker<Result, Void>() {
			@Override
			protected Result doInBackground() throws Exception {
				return task.call();
			}

			@Override
			protected void done() {
				try {
					var result = get();
					if (token != revision)
						return;
					grid.checkRulePreview(inspected);
					schemaCheck.showResult(result);
					status.setText(
							"Proposed mapping checked against captured Schema. Nothing applied yet; database Review remains required.");
				} catch (Exception failure) {
					if (token == revision)
						inspectionFailed(failure);
				} finally {
					busy = false;
					refreshControls();
				}
			}
		}.execute();
	}
}
