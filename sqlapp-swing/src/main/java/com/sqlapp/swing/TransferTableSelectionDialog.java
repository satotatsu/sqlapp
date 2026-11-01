/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration;

final class TransferTableSelectionDialog {
	static List<String> show(Component owner, Path job, List<String> selected, TransferJobConfiguration.DataMode mode) {
		var result = new AtomicReference<List<String>>();
		var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Edit transfer tables",
				Dialog.ModalityType.APPLICATION_MODAL);
		var panel = new TransferTableSelectionPanel(job, selected, mode, value -> {
			result.set(value);
			dialog.dispose();
		}, dialog::dispose);
		dialog.setContentPane(panel);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (!panel.isBusy())
					dialog.dispose();
			}
		});
		dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),
				"cancelTables");
		dialog.getRootPane().getActionMap().put("cancelTables", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				if (!panel.isBusy())
					dialog.dispose();
			}
		});
		dialog.setSize(1050, 700);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return result.get();
	}
}
