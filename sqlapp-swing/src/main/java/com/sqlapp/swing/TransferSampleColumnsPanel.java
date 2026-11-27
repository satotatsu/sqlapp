/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.*;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferFileImportService;

/** A metadata-only column chooser for an already loaded sample. */
final class TransferSampleColumnsPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final ReportDetailTablePanel panel;
	final JButton go = new JButton("Go to column");

	TransferSampleColumnsPanel(TransferFileImportService.Preview preview, TransferFileImportService.PackageTable mapped,
			java.util.function.IntConsumer navigate) {
		super(new java.awt.BorderLayout());
		var rows = new ArrayList<List<String>>();
		for (int index = 0; index < preview.table().columns().size(); index++) {
			var column = mapped == null ? null : mapped.columns().get(index);
			rows.add(List.of(Integer.toString(index + 1), preview.table().columns().get(index),
					column == null ? "not recorded" : column.targetName(), preview.sourceTypes().get(index),
					column == null ? "not recorded" : column.sourceType(),
					column == null ? "not recorded" : Boolean.toString(column.notNull()),
					column == null ? "not recorded" : Boolean.toString(column.primaryKey())));
		}
		panel = new ReportDetailTablePanel(new ReportReviewService.DetailTable(
				"Sample columns: " + preview.table().table(),
				"Recorded source metadata and requested names, not resolved target metadata. Search or sort this list, select a column and Go to column. No rows or files are reread. Target-only generated PK columns are not packaged fields.",
				List.of("Source order", "Source column", "Requested target column", "Source value type",
						"Source Schema type", "Source NOT NULL", "Source PK"),
				rows));
		panel.setNumericColumns(List.of(0));
		go.setEnabled(false);
		panel.table.addPropertyChangeListener("enabled",
				event -> go.setEnabled(panel.table.isEnabled() && panel.table.getSelectedRow() >= 0));
		panel.table.getSelectionModel().addListSelectionListener(
				event -> go.setEnabled(panel.table.isEnabled() && panel.table.getSelectedRow() >= 0));
		go.addActionListener(event -> {
			if (panel.table.isEnabled() && panel.table.getSelectedRow() >= 0)
				navigate.accept(panel.table.convertRowIndexToModel(panel.table.getSelectedRow()) + 1);
		});
		var controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		controls.add(go);
		panel.setScopeControls(controls);
		add(panel);
	}

	static void show(java.awt.Component owner, TransferFileImportService.Preview preview,
			TransferFileImportService.PackageTable mapped, java.util.function.IntConsumer navigate) {
		var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Sample columns",
				java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		var panel = new TransferSampleColumnsPanel(preview, mapped, column -> {
			navigate.accept(column);
			dialog.dispose();
		});
		var close = new JButton("Close");
		close.addActionListener(event -> dialog.dispose());
		dialog.add(panel);
		dialog.add(close, java.awt.BorderLayout.SOUTH);
		dialog.setSize(1000, 600);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}
}
