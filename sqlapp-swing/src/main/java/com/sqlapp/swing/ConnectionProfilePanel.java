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
final class ConnectionProfilePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton load = new JButton("Load…");
	final JButton save = new JButton("Save…");
	final JLabel status = new JLabel("Password excluded");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final JTextField jdbcUrl;
	private final JTextField username;
	private final JTextField driverClass;
	private final JPasswordField password;
	private final Reader reader;
	private final Writer writer;
	private long revision;
	private boolean busy;
	private final EditorFileChooser fileDialogs = new EditorFileChooser();

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
		this(jdbcUrl, username, driverClass, password, new ConnectionProfileService()::read,
				new ConnectionProfileService()::write);
	}

	ConnectionProfilePanel(final JTextField jdbcUrl, final JTextField username, final JTextField driverClass,
			final JPasswordField password, final Reader reader, final Writer writer) {
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
	}

	JFileChooser chooser(final boolean saving) {
		return fileDialogs.create(new FileNameExtensionFilter("Connection profile JSON", "json"),
				saving ? "connection.json" : null);
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
		new SwingWorker<ConnectionProfileService.Profile, Void>() {
			protected ConnectionProfileService.Profile doInBackground() throws Exception {
				return reader.read(file);
			}

			protected void done() {
				try {
					final var profile = get();
					if (requestedRevision != revision || !isEnabled() || !jdbcUrl.isEnabled()) {
						status.setText("Inputs changed; load again");
						return;
					}
					password.setText("");
					jdbcUrl.setText(profile.jdbcUrl());
					username.setText(profile.username());
					driverClass.setText(profile.driverClass());
					status.setText("Loaded; enter password");
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
