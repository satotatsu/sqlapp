/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import javax.swing.*;

/** Shared connection form; actions are bound by the owning screen. */
final class ConnectionFields extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField jdbcUrl = new JTextField(20);
	final JTextField username = new JTextField();
	final JPasswordField password = new JPasswordField();
	final JTextField driverClass = new JTextField();
	final JButton test = new JButton("Test connection");
	final JButton buildUrl = new JButton("Build URL…");
	final ConnectionProfilePanel profile = new ConnectionProfilePanel(jdbcUrl, username, driverClass, password);
	private final JLabel driverLabel = new JLabel("Driver class");

	ConnectionFields(String title) {
		super(new GridBagLayout());
		setBorder(BorderFactory.createTitledBorder(title));
		row(0, new JLabel("JDBC URL"), jdbcUrl);
		row(1, new JLabel("Username"), username);
		row(2, new JLabel("Password"), password);
		row(3, driverLabel, driverClass);
		row(4, new JLabel("Profile"), profile);
		test.setToolTipText("Check this connection and supported database dialect without a migration job.");
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(buildUrl);
		actions.add(test);
		row(5, new JLabel("Connection"), actions);
		final var space = new GridBagConstraints();
		space.gridy = 99;
		space.weighty = 1;
		add(Box.createVerticalGlue(), space);
		showDriver(false);
	}

	void showDriver(boolean show) {
		driverLabel.setVisible(show);
		driverClass.setVisible(show);
	}

	Input snapshot() {
		return new Input(jdbcUrl.getText().trim(), username.getText().trim(), driverClass.getText().trim());
	}

	record Input(String jdbcUrl, String username, String driverClass) {
		PlanningJdbc.Spec spec() {
			return new PlanningJdbc.Spec(jdbcUrl, username, driverClass);
		}
	}

	void row(int index, JLabel label, Component field) {
		final var constraints = new GridBagConstraints();
		constraints.gridy = index;
		constraints.insets = new Insets(3, 3, 3, 3);
		constraints.anchor = GridBagConstraints.WEST;
		label.setLabelFor(field);
		add(label, constraints);
		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		add(field, constraints);
	}
}
