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
	final JComboBox<String> savedConnection = new JComboBox<>();
	final JButton connectionFolder = new JButton("Folder…");
	final JButton saveConnection = new JButton("Save as…");
	private final ConnectionCatalog catalog;
	private final EditorFileChooser folderDialogs = new EditorFileChooser();
	private final Runnable catalogListener = this::reloadConnections;
	private boolean reloading;
	private static final String NONE = "(none)";

	ConnectionFields(String title) {
		this(title, ConnectionCatalog.SESSION);
	}

	ConnectionFields(String title, ConnectionCatalog catalog) {
		super(new GridBagLayout());
		this.catalog = java.util.Objects.requireNonNull(catalog);
		FieldHints.placeholder(jdbcUrl, "Required, e.g. jdbc:postgresql://host:5432/db");
		FieldHints.placeholder(password, "Not saved; enter for this session");
		setBorder(BorderFactory.createTitledBorder(title));
		final var named = new JPanel(new BorderLayout(4, 0));
		named.add(savedConnection, BorderLayout.CENTER);
		final var namedActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		namedActions.add(connectionFolder);
		namedActions.add(saveConnection);
		named.add(namedActions, BorderLayout.EAST);
		final var namedLabel = new JLabel("Saved");
		namedLabel.setToolTipText(
				"Saved connection: a subfolder of the connection folder holding dataSource.yaml / .properties");
		row(0, namedLabel, named);
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
		installCatalog();
		final var space = new GridBagConstraints();
		space.gridy = 99;
		space.weighty = 1;
		add(Box.createVerticalGlue(), space);
		showDriver(false);
	}

	private void installCatalog() {
		connectionFolder.setToolTipText(
				"Choose the folder whose subfolders hold dataSource.yaml / .properties files, e.g. src/main/config");
		saveConnection.setToolTipText("Save URL, username and driver as <folder>/<name>/dataSource.yaml (no password)");
		catalog.addListener(catalogListener);
		// Keep the row narrow so source and target forms fit side by side.
		savedConnection.setPrototypeDisplayValue("sqlserver");
		savedConnection.addActionListener(event -> {
			if (reloading || savedConnection.getSelectedIndex() <= 0)
				return;
			final var entry = selectedEntry();
			if (entry != null)
				profile.loadFrom(entry.file());
		});
		savedConnection.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
			public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
				reloadConnections();
			}

			public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {
			}

			public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {
			}
		});
		connectionFolder.addActionListener(event -> {
			final var chooser = folderDialogs.forDirectory(catalog.folder() == null ? "" : catalog.folder().toString());
			chooser.setDialogTitle("Choose the connection folder (for example src/main/config)");
			if (chooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION)
				catalog.setFolder(chooser.getSelectedFile().toPath());
		});
		saveConnection.addActionListener(event -> {
			final String name = JOptionPane.showInputDialog(this, "Connection name (folder name, e.g. local):",
					selectedName() == null ? "" : selectedName());
			if (name != null)
				saveAs(name);
		});
		for (var field : new JTextField[] { jdbcUrl, username, driverClass })
			field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
				public void insertUpdate(javax.swing.event.DocumentEvent event) {
					showCurrentConnection();
				}

				public void removeUpdate(javax.swing.event.DocumentEvent event) {
					showCurrentConnection();
				}

				public void changedUpdate(javax.swing.event.DocumentEvent event) {
					showCurrentConnection();
				}
			});
		profile.status.addPropertyChangeListener("text", event -> showCurrentConnection());
		reloadConnections();
	}

	/**
	 * Saves the form as a named connection in the connection folder, then selects
	 * it.
	 */
	void saveAs(final String name) {
		final java.nio.file.Path file;
		try {
			file = catalog.newConnectionFile(name);
			java.nio.file.Files.createDirectories(file.getParent());
		} catch (IllegalArgumentException | java.io.IOException failure) {
			profile.status.setText("Not saved: " + EditorStatusPanel.failureMessage(failure));
			return;
		}
		if (java.nio.file.Files.exists(file) && JOptionPane.showConfirmDialog(this, "Replace " + file + "?",
				"Replace connection", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;
		profile.saveTo(file);
	}

	/** Reloads connection names, keeping the selection that matches the form. */
	void reloadConnections() {
		reloading = true;
		try {
			final var entries = catalog.entries();
			savedConnection.removeAllItems();
			savedConnection.addItem(catalog.folder() == null ? "(no folder)" : NONE);
			for (final var entry : entries)
				savedConnection.addItem(entry.name());
			savedConnection.setToolTipText(
					catalog.folder() == null ? "No connection folder chosen" : "Connections in " + catalog.folder());
			saveConnection.setEnabled(catalog.folder() != null && isEnabled());
		} finally {
			reloading = false;
		}
		showCurrentConnection();
	}

	/**
	 * Selects the connection whose settings file still matches the form, or the
	 * placeholder.
	 */
	private void showCurrentConnection() {
		final var current = profile.settingsFile();
		String match = null;
		if (current != null)
			for (final var entry : catalog.entries())
				if (entry.file().toAbsolutePath().normalize().equals(current))
					match = entry.name();
		reloading = true;
		try {
			if (match == null)
				savedConnection.setSelectedIndex(savedConnection.getItemCount() > 0 ? 0 : -1);
			else
				savedConnection.setSelectedItem(match);
		} finally {
			reloading = false;
		}
	}

	private String selectedName() {
		return savedConnection.getSelectedIndex() > 0 ? (String) savedConnection.getSelectedItem() : null;
	}

	private ConnectionCatalog.Entry selectedEntry() {
		final String name = selectedName();
		return catalog.entries().stream().filter(entry -> entry.name().equals(name)).findFirst().orElse(null);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		if (saveConnection != null && catalog != null)
			saveConnection.setEnabled(enabled && catalog.folder() != null);
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
		savedConnection.getParent().setVisible(false);
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
