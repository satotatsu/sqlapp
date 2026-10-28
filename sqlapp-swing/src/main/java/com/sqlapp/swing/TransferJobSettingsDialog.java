/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.*;

final class TransferJobSettingsDialog {
	static TransferJobConfigurationIO.Snapshot show(Component owner, Path file,
			TransferJobConfiguration.DataMode mode) {
		final var saved = new AtomicReference<TransferJobConfigurationIO.Snapshot>();
		final var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Shared transfer job settings",
				Dialog.ModalityType.APPLICATION_MODAL);
		final var panel = new TransferJobSettingsPanel(mode, value -> {
			saved.set(value);
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
		dialog.setSize(850, 430);
		dialog.setLocationRelativeTo(owner);
		panel.load(file);
		dialog.setVisible(true);
		return saved.get();
	}
}
