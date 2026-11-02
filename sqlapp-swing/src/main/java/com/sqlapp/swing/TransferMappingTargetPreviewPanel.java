/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.*;
import com.sqlapp.data.db.dialect.*;

/** Optional offline builder selection; never stored in the name mapping. */
final class TransferMappingTargetPreviewPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JComboBox<String> product = new JComboBox<>(new String[] { "No target preview", "HSQL Database Engine",
			"PostgreSQL", "MySQL", "Oracle", "Microsoft SQL Server", "SQLite" });
	final JTextField version = new JTextField(8);

	TransferMappingTargetPreviewPanel(Runnable changed) {
		super(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		var label = new JLabel("Optional target preview");
		label.setLabelFor(product);
		add(label);
		add(product);
		var versionLabel = new JLabel("Version");
		versionLabel.setLabelFor(version);
		add(versionLabel);
		add(version);
		version.setToolTipText(
				"Explicit target version: major, major.minor or major.minor.revision. Preview only; no connection.");
		product.addActionListener(event -> changed.run());
		version.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent event) {
				changed.run();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent event) {
				changed.run();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent event) {
				changed.run();
			}
		});
	}

	Dialect dialect() {
		if (product.getSelectedIndex() == 0) {
			if (!version.getText().isBlank())
				throw new IllegalArgumentException("Choose a target preview product or clear its version.");
			return null;
		}
		String text = version.getText().trim();
		if (!text.matches("[0-9]+(?:\\.[0-9]+){0,2}"))
			throw new IllegalArgumentException(
					"Enter the target preview version as major, major.minor or major.minor.revision.");
		String[] parts = text.split("\\.");
		try {
			var dialect = DialectResolver.getInstance().getDialect((String) product.getSelectedItem(),
					Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
					parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
			if (dialect == null || dialect.getClass() == Dialect.class)
				throw new IllegalArgumentException(
						"No target SQL builder is available for this preview product/version.");
			return dialect;
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException(
					"Target preview version components must fit a non-negative Java integer.", failure);
		}
	}
}
