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

	/**
	 * Named connections shared by every form; selecting one loads its settings
	 * file.
	 */
	final SavedConnectionChooser saved;
	final JComboBox<String> savedConnection;
	final JButton connectionFolder;
	final JButton saveConnection;

	ConnectionFields(String title) {
		this(title, ConnectionCatalog.SESSION);
	}

	ConnectionFields(String title, ConnectionCatalog catalog) {
		super(new GridBagLayout());
		saved = new SavedConnectionChooser(catalog, profile, jdbcUrl, username, driverClass);
		savedConnection = saved.savedConnection;
		connectionFolder = saved.connectionFolder;
		saveConnection = saved.saveConnection;
		FieldHints.placeholder(jdbcUrl, "Required, e.g. jdbc:postgresql://host:5432/db");
		FieldHints.placeholder(password, "Not saved; enter for this session");
		setBorder(BorderFactory.createTitledBorder(title));
		final var namedLabel = new JLabel("Saved");
		namedLabel.setToolTipText(SavedConnectionChooser.TOOLTIP);
		row(0, namedLabel, saved);
		row(1, new JLabel("JDBC URL"), jdbcUrl);
		row(2, new JLabel("Username"), username);
		row(3, new JLabel("Password"), password);
		row(4, driverLabel, driverClass);
		row(5, new JLabel("Profile"), profile);
		test.setToolTipText("Check this connection and supported database dialect without a migration job.");
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(buildUrl);
		actions.add(test);
		row(6, new JLabel("Connection"), actions);
		final var space = new GridBagConstraints();
		space.gridy = 99;
		space.weighty = 1;
		add(Box.createVerticalGlue(), space);
		showDriver(false);
	}

	/**
	 * Saves the form as a named connection in the connection folder, then selects
	 * it.
	 */
	void saveAs(final String name) {
		saved.saveAs(name);
	}

	/** Reloads connection names, keeping the selection that matches the form. */
	void reloadConnections() {
		saved.reloadConnections();
	}

	/** Name of the saved connection the form currently matches, or null. */
	String connectionName() {
		return saved.connectionName();
	}

	void showDriver(boolean show) {
		driverLabel.setVisible(show);
		driverClass.setVisible(show);
	}

	void useFileSource() {
		for (var component : getComponents())
			if (component instanceof JLabel label
					&& java.util.Set.of("Saved", "JDBC URL", "Username", "Profile").contains(label.getText()))
				label.setVisible(false);
		jdbcUrl.setVisible(false);
		username.setVisible(false);
		profile.setVisible(false);
		saved.setVisible(false);
		buildUrl.setVisible(false);
		setBorder(BorderFactory.createTitledBorder("Access source — optional password"));
	}

	Input snapshot() {
		return new Input(jdbcUrl.getText().trim(), username.getText().trim(), driverClass.getText().trim(),
				profile.settingsFile());
	}

	/**
	 * @param settingsFile settings file the form still matches, for its other
	 *                     settings; or null
	 */
	record Input(String jdbcUrl, String username, String driverClass, java.nio.file.Path settingsFile) {
		PlanningJdbc.Spec spec() {
			return new PlanningJdbc.Spec(jdbcUrl, username, driverClass, settingsFile);
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
