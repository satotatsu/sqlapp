/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** The same explicit file actions for Schema, data source and data target. */
final class ConnectionProfilePanel extends JPanel implements ComponentEnablement.SelfManaged {
	private static final long serialVersionUID = 1L;
	final JButton load = new JButton("Load…");
	final JButton save = new JButton("Save…");
	final JLabel status = new JLabel("Passwords are never saved");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final JTextField jdbcUrl;
	private final JTextField username;
	private final JTextField driverClass;
	private final JPasswordField password;
	private final SettingsReader reader;
	/**
	 * Settings file the form was loaded from or saved to, with the values it held
	 * then.
	 */
	private Path settingsFile;
	private String settingsValues;
	private final Writer writer;
	private long revision;
	private boolean busy;
	private final EditorFileChooser fileDialogs = new EditorFileChooser();

	/**
	 * Reads a data source settings file or a legacy profile, with an optional
	 * session password.
	 */
	@FunctionalInterface
	interface SettingsReader {
		ConnectionProfileService.Loaded read(Path file) throws Exception;
	}

	@FunctionalInterface
	interface Reader {
		ConnectionProfileService.Profile read(Path file) throws Exception;
	}

	@FunctionalInterface
	interface Writer {
		void write(ConnectionProfileService.Profile profile, Path file) throws Exception;
	}

	ConnectionProfilePanel(final JTextField jdbcUrl, final JTextField username, final JTextField driverClass,
			final JPasswordField password) {
		this(new ConnectionProfileService()::readAny, jdbcUrl, username, driverClass, password,
				new ConnectionProfileService()::write);
	}

	ConnectionProfilePanel(final JTextField jdbcUrl, final JTextField username, final JTextField driverClass,
			final JPasswordField password, final Reader reader, final Writer writer) {
		this(file -> ConnectionProfileService.Loaded.profileOnly(reader.read(file)), jdbcUrl, username, driverClass,
				password, writer);
	}

	ConnectionProfilePanel(final SettingsReader reader, final JTextField jdbcUrl, final JTextField username,
			final JTextField driverClass, final JPasswordField password, final Writer writer) {
		super(new java.awt.BorderLayout(4, 0));
		this.jdbcUrl = jdbcUrl;
		this.username = username;
		this.driverClass = driverClass;
		this.password = password;
		this.reader = reader;
		this.writer = writer;
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(load);
		actions.add(save);
		add(actions, java.awt.BorderLayout.WEST);
		add(statusPanel, java.awt.BorderLayout.CENTER);
		final DocumentListener changed = new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				inputsChanged();
			}

			public void removeUpdate(final DocumentEvent event) {
				inputsChanged();
			}

			public void changedUpdate(final DocumentEvent event) {
				inputsChanged();
			}
		};
		for (final var field : new JTextField[] { jdbcUrl, username, driverClass, password }) {
			field.getDocument().addDocumentListener(changed);
		}
		load.addActionListener(event -> {
			final var chooser = chooser(false);
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				loadFrom(chooser.getSelectedFile().toPath());
		});
		save.addActionListener(event -> {
			final var chooser = chooser(true);
			if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
				return;
			final var file = chooser.getSelectedFile().toPath();
			if (!Files.exists(file) || JOptionPane.showConfirmDialog(this, "Replace " + file + "?",
					"Replace connection profile", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
				saveTo(file);
		});
		for (final var target : new javax.swing.JComponent[] { this, load })
			FileDropHandler.file(target, SETTINGS_EXTENSIONS, () -> !busy && isEnabled() && jdbcUrl.isEnabled(),
					this::droppedProfile, FileDropHandler.errors(status, statusPanel));
	}

	private boolean droppedProfile(final Path path) {
		loadFrom(path);
		return busy;
	}

	/** Data source settings shared with Gradle, plus legacy profile JSON. */
	static final java.util.List<String> SETTINGS_EXTENSIONS = java.util.List.of("yaml", "yml", "properties", "json",
			"xml");

	JFileChooser chooser(final boolean saving) {
		return saving
				? fileDialogs.create(new FileNameExtensionFilter("Connection settings YAML", "yaml", "yml"),
						"dataSource.yaml")
				: fileDialogs.create(new FileNameExtensionFilter("Connection settings (YAML, properties, JSON, XML)",
						SETTINGS_EXTENSIONS.toArray(String[]::new)), null);
	}

	/**
	 * The data source settings file that still matches the form, for reuse in
	 * Gradle settings; null after the URL, user or driver was edited.
	 */
	Path settingsFile() {
		return settingsFile != null && currentValues().equals(settingsValues) ? settingsFile : null;
	}

	private String currentValues() {
		return jdbcUrl.getText().trim() + "\n" + username.getText().trim() + "\n" + driverClass.getText().trim();
	}

	private void rememberSettings(final Path file) {
		settingsFile = file;
		settingsValues = file == null ? null : currentValues();
	}

	private void inputsChanged() {
		revision++;
		if (!busy)
			status.setText("Connection inputs changed");
	}

	void loadFrom(final Path file) {
		if (busy || !isEnabled() || !jdbcUrl.isEnabled())
			return;
		final long requestedRevision = revision;
		busy(true);
		new SwingWorker<ConnectionProfileService.Loaded, Void>() {
			protected ConnectionProfileService.Loaded doInBackground() throws Exception {
				return reader.read(file);
			}

			protected void done() {
				try {
					final var loaded = get();
					final var profile = loaded.profile();
					if (requestedRevision != revision || !isEnabled() || !jdbcUrl.isEnabled()) {
						if (loaded.password() != null)
							java.util.Arrays.fill(loaded.password(), '\0');
						status.setText("Inputs changed; load again");
						return;
					}
					password.setText("");
					jdbcUrl.setText(profile.jdbcUrl());
					username.setText(profile.username());
					driverClass.setText(profile.driverClass());
					if (loaded.password() != null) {
						// Session only: the form never saves passwords.
						password.setText(new String(loaded.password()));
						java.util.Arrays.fill(loaded.password(), '\0');
					}
					rememberSettings(loaded.settingsFile());
					status.setText(loaded.note());
					fileDialogs.remember(file);
					statusPanel.setDetails(status.getText() + "\nFile: " + file);
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause());
				} finally {
					busy(false);
				}
			}
		}.execute();
	}

	void saveTo(final Path file) {
		if (busy || !isEnabled() || !jdbcUrl.isEnabled())
			return;
		final String url = jdbcUrl.getText(), user = username.getText(), driver = driverClass.getText();
		busy(true);
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() throws Exception {
				writer.write(new ConnectionProfileService.Profile(1, url, user, driver), file);
				return null;
			}

			protected void done() {
				try {
					get();
					final boolean unchanged = url.equals(jdbcUrl.getText()) && user.equals(username.getText())
							&& driver.equals(driverClass.getText());
					status.setText(
							unchanged ? "Profile saved" : "Profile saved; newer connection inputs are not saved");
					final String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
					if (unchanged && (name.endsWith(".yaml") || name.endsWith(".yml")))
						rememberSettings(file.toAbsolutePath().normalize());
					fileDialogs.remember(file);
					statusPanel.setDetails(status.getText() + "\nFile: " + file);
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause());
				} finally {
					busy(false);
				}
			}
		}.execute();
	}

	private void busy(final boolean busy) {
		this.busy = busy;
		refreshActions();
		if (busy)
			status.setText("Reading/writing profile…");
	}

	@Override
	public void setEnabled(final boolean enabled) {
		super.setEnabled(enabled);
		// Swing constructors may call this before the action fields are initialized.
		if (statusPanel != null) {
			statusPanel.setEnabled(enabled);
			ComponentEnablement.setTreeEnabled(statusPanel, enabled);
		}
		if (load != null && save != null)
			refreshActions();
	}

	private void refreshActions() {
		load.setEnabled(!busy && isEnabled());
		save.setEnabled(!busy && isEnabled());
	}

	boolean isBusy() {
		return busy;
	}

	private void failed(final Throwable failure) {
		status.setText("Profile operation failed");
		statusPanel.setDetails(EditorStatusPanel.failureDetails(failure));
	}
}
