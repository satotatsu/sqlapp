/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Optional connection form with an explicit preview/apply boundary. */
final class JdbcUrlBuilderPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JComboBox<JdbcUrlBuilder.Database> type = new JComboBox<>(JdbcUrlBuilder.Database.values());
	final JTextField host = new JTextField(30);
	final JTextField port = new JTextField();
	final JTextField database = new JTextField();
	final JTextField preview = new JTextField();
	final JLabel databaseLabel = new JLabel("Database name");
	final JLabel status = new JLabel();
	final JButton use = new JButton("Use URL");
	final JButton cancel = new JButton("Cancel");

	JdbcUrlBuilderPanel(String currentUrl, Consumer<String> ready, Runnable cancelled) {
		super(new BorderLayout(8, 8));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		add(new JLabel("Build a new URL, then review it before applying. No connection is opened."),
				BorderLayout.NORTH);
		final var form = new JPanel(new GridLayout(0, 1, 4, 4));
		row(form, new JLabel("Database type"), type);
		row(form, new JLabel("Host"), host);
		row(form, new JLabel("Port"), port);
		row(form, databaseLabel, database);
		preview.setEditable(false);
		row(form, new JLabel("JDBC URL preview"), preview);
		form.add(new JLabel("<html>Existing URL options are not carried over.<br>"
				+ "TLS, multiple servers, SID and other advanced options: use direct URL input.</html>"));
		add(form, BorderLayout.CENTER);
		final var bottom = new JPanel(new BorderLayout(4, 4));
		bottom.add(status, BorderLayout.NORTH);
		final var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(use);
		actions.add(cancel);
		bottom.add(actions, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);
		final var changed = new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				refresh();
			}

			public void removeUpdate(DocumentEvent event) {
				refresh();
			}

			public void changedUpdate(DocumentEvent event) {
				refresh();
			}
		};
		for (var field : new JTextField[] { host, port, database })
			field.getDocument().addDocumentListener(changed);
		type.setSelectedIndex(-1);
		type.addActionListener(event -> {
			final var selected = (JdbcUrlBuilder.Database) type.getSelectedItem();
			port.setText(selected == null ? "" : Integer.toString(selected.port));
			databaseLabel
					.setText(selected == JdbcUrlBuilder.Database.ORACLE_SERVICE ? "Service name" : "Database name");
			refresh();
		});
		for (var value : JdbcUrlBuilder.Database.values())
			if (currentUrl != null && currentUrl.startsWith(value.prefix)) {
				type.setSelectedItem(value);
				break;
			}
		use.addActionListener(event -> {
			refresh();
			if (use.isEnabled())
				ready.accept(preview.getText());
		});
		cancel.addActionListener(event -> cancelled.run());
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "cancel");
		getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				cancelled.run();
			}
		});
		refresh();
	}

	static String showDialog(java.awt.Component owner, String currentUrl, String connectionName) {
		final var selected = new java.util.concurrent.atomic.AtomicReference<String>();
		final var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Build JDBC URL — " + connectionName,
				java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setContentPane(new JdbcUrlBuilderPanel(currentUrl, url -> {
			selected.set(url);
			dialog.dispose();
		}, dialog::dispose));
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setSize(750, 360);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return selected.get();
	}

	private static void row(JPanel form, JLabel label, JComponent field) {
		label.setLabelFor(field);
		final var row = new JPanel(new BorderLayout(8, 0));
		label.setPreferredSize(new java.awt.Dimension(130, 24));
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		form.add(row);
	}

	private void refresh() {
		try {
			preview.setText(JdbcUrlBuilder.build((JdbcUrlBuilder.Database) type.getSelectedItem(), host.getText(),
					port.getText(), database.getText()));
			preview.setCaretPosition(0);
			preview.setToolTipText(preview.getText());
			status.setText("Use URL replaces only the selected connection's JDBC URL.");
			use.setEnabled(true);
		} catch (IllegalArgumentException failure) {
			preview.setText("");
			preview.setToolTipText(null);
			use.setEnabled(false);
			status.setText(failure.getMessage());
		}
	}
}
