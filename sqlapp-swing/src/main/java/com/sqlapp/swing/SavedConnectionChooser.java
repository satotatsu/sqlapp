/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Lists the named connections of a {@link ConnectionCatalog}; choosing one
 * loads its settings file into a connection form through its profile panel.
 */
final class SavedConnectionChooser extends JPanel implements ComponentEnablement.SelfManaged {
	private static final long serialVersionUID = 1L;
	static final String TOOLTIP = "Saved connection: a subfolder of the connection folder holding dataSource.yaml / .properties";
	private static final String NONE = "(none)";
	final JComboBox<String> savedConnection = new JComboBox<>();
	final JButton connectionFolder = new JButton("Folder…");
	final JButton saveConnection = new JButton("Save as…");
	private final ConnectionCatalog catalog;
	private final ConnectionProfilePanel profile;
	private final EditorFileChooser folderDialogs = new EditorFileChooser();
	private final Runnable catalogListener = this::reloadConnections;
	private boolean reloading;

	SavedConnectionChooser(final ConnectionCatalog catalog, final ConnectionProfilePanel profile,
			final JTextField... formFields) {
		super(new BorderLayout(4, 0));
		this.catalog = Objects.requireNonNull(catalog);
		this.profile = Objects.requireNonNull(profile);
		add(savedConnection, BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(connectionFolder);
		actions.add(saveConnection);
		add(actions, BorderLayout.EAST);
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
		savedConnection.addPopupMenuListener(new PopupMenuListener() {
			public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
				reloadConnections();
			}

			public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
			}

			public void popupMenuCanceled(PopupMenuEvent event) {
			}
		});
		connectionFolder.addActionListener(event -> {
			final var chooser = folderDialogs.forDirectory(catalog.folder() == null ? "" : catalog.folder().toString());
			chooser.setDialogTitle("Choose the connection folder (for example src/main/config)");
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				catalog.setFolder(chooser.getSelectedFile().toPath());
		});
		saveConnection.addActionListener(event -> {
			final String name = JOptionPane.showInputDialog(this, "Connection name (folder name, e.g. local):",
					connectionName() == null ? "" : connectionName());
			if (name != null)
				saveAs(name);
		});
		final DocumentListener edited = new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				showCurrentConnection();
			}

			public void removeUpdate(DocumentEvent event) {
				showCurrentConnection();
			}

			public void changedUpdate(DocumentEvent event) {
				showCurrentConnection();
			}
		};
		for (final var field : formFields)
			field.getDocument().addDocumentListener(edited);
		profile.status.addPropertyChangeListener("text", event -> showCurrentConnection());
		reloadConnections();
	}

	/**
	 * Saves the form as a named connection in the connection folder, then selects
	 * it.
	 */
	void saveAs(final String name) {
		final Path file;
		try {
			file = catalog.newConnectionFile(name);
			Files.createDirectories(file.getParent());
		} catch (IllegalArgumentException | java.io.IOException failure) {
			profile.status.setText("Not saved: " + EditorStatusPanel.failureMessage(failure));
			return;
		}
		if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Replace " + file + "?", "Replace connection",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
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

	/** Name of the saved connection the form currently matches, or null. */
	String connectionName() {
		return savedConnection.getSelectedIndex() > 0 ? (String) savedConnection.getSelectedItem() : null;
	}

	private ConnectionCatalog.Entry selectedEntry() {
		final String name = connectionName();
		return catalog.entries().stream().filter(entry -> entry.name().equals(name)).findFirst().orElse(null);
	}

	@Override
	public void setEnabled(final boolean enabled) {
		super.setEnabled(enabled);
		if (catalog == null)
			return;
		savedConnection.setEnabled(enabled);
		connectionFolder.setEnabled(enabled);
		saveConnection.setEnabled(enabled && catalog.folder() != null);
	}
}
