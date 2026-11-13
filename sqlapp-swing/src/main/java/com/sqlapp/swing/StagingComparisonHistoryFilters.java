/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.FlowLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Optional history scope composed with the report table's existing text filter.
 */
final class StagingComparisonHistoryFilters extends JPanel {
	private static final long serialVersionUID = 1L;
	final JComboBox<String> scope = new JComboBox<>(new String[] { "All changes", "New mismatch",
			"Mixed status changes", "Match status improved", "Row counts changed" });

	StagingComparisonHistoryFilters(ReportDetailTablePanel panel, int changeColumn) {
		super(new FlowLayout(FlowLayout.LEFT));
		var originalModel = panel.table.getModel();
		add(new JLabel("Show"));
		add(scope);
		scope.setToolTipText("Filter recorded change categories. An improvement can still leave another mismatch.");
		scope.setEnabled(panel.table.isEnabled());
		scope.addActionListener(event -> {
			if (panel.table.getModel() != originalModel || !scope.isEnabled() || !panel.table.isEnabled())
				return;
			panel.setExactFilter(changeColumn, scope.getSelectedIndex() == 0 ? null : (String) scope.getSelectedItem());
		});
		panel.table.addPropertyChangeListener("model", event -> scope.setEnabled(false));
		panel.table.addPropertyChangeListener("enabled",
				event -> scope.setEnabled(panel.table.isEnabled() && panel.table.getModel() == originalModel));
	}
}
