/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingScopeService.Review;

/**
 * Explicit authoring decision; removing names never excludes data or drops
 * tables.
 */
final class TransferMappingScopePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton remove = new JButton("Remove listed groups");
	final JButton cancel = new JButton("Cancel");
	final DefaultTableModel model = new DefaultTableModel(
			new String[] { "Mapping source", "Resolved source table", "Target name", "Column overrides" }, 0) {
		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}

		@Override
		public Class<?> getColumnClass(int column) {
			return column == 3 ? Integer.class : String.class;
		}
	};

	TransferMappingScopePanel(Review review, Runnable removed, Runnable cancelled) {
		super(new BorderLayout(8, 8));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		var description = new JTextArea(
				"These mappings refer to tables outside the current job selection. Remove their complete table/column groups from the editor only.\nSelected-table mappings remain unchanged. Save mapping and job settings afterward; no database objects or data are deleted.",
				3, 1);
		description.setEditable(false);
		description.setLineWrap(true);
		description.setWrapStyleWord(true);
		add(new JScrollPane(description), BorderLayout.NORTH);
		for (var entry : review.excluded())
			model.addRow(new Object[] { entry.sourceKey(), entry.resolvedSource(),
					entry.targetName() == null ? "(unchanged)" : entry.targetName(), entry.columnOverrides() });
		var table = new JTable(model);
		table.setFillsViewportHeight(true);
		table.setAutoCreateRowSorter(true);
		add(new JScrollPane(table), BorderLayout.CENTER);
		var evidence = new JTextArea("Retain " + review.retained().tables().size() + " table mapping groups. Source: "
				+ review.schemaFile() + "\n" + review.schemaFingerprint() + "\nMapping: " + review.mappingFingerprint(),
				3, 1);
		evidence.setEditable(false);
		evidence.setLineWrap(true);
		evidence.setWrapStyleWord(true);
		var bottom = new JPanel(new BorderLayout(4, 4));
		bottom.add(new JScrollPane(evidence), BorderLayout.CENTER);
		var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(remove);
		actions.add(cancel);
		bottom.add(actions, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);
		remove.setEnabled(!review.excluded().isEmpty());
		remove.addActionListener(event -> {
			remove.setEnabled(false);
			removed.run();
		});
		cancel.addActionListener(event -> cancelled.run());
	}
}
