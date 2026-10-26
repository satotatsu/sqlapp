/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Explicit loading of local JDBC libraries for the current application session.
 */
final class JdbcDriverPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton load = new JButton("Load driver JARs…");
	final JTextField driverClass = new JTextField();
	final JTextArea loaded = new JTextArea();
	final JComboBox<String> selectedDriver = new JComboBox<>();
	final JButton useSchema = new JButton("Use for Schema");
	final JButton useSource = new JButton("Use for source");
	final JButton useTarget = new JButton("Use for target");
	private final java.util.Map<JButton, java.util.function.Predicate<String>> receivers = new java.util.LinkedHashMap<>();
	final JLabel status = new JLabel("No additional JDBC libraries loaded. Bundled drivers remain available.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final JdbcDriverLibraries libraries;
	private final EditorFileChooser dialogs = new EditorFileChooser();
	private BooleanSupplier blocked = () -> false;
	private boolean busy;

	JdbcDriverPanel() {
		this(JdbcDriverLibraries.SESSION);
	}

	JdbcDriverPanel(final JdbcDriverLibraries libraries) {
		super(new BorderLayout(8, 8));
		this.libraries = libraries;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var header = new JPanel(new BorderLayout(8, 8));
		final var note = new JTextArea(
				"Select a local JDBC driver JAR and any dependency JARs together. Only load trusted libraries: loading runs Java code.\n"
						+ "Drivers are available to Schema and data migration until the application exits. No files or preferences are saved.\n"
						+ "Leave the class blank for JDBC service discovery. Loading libraries does not connect to a database.");
		note.setEditable(false);
		note.setLineWrap(true);
		note.setWrapStyleWord(true);
		note.setOpaque(false);
		header.add(note, BorderLayout.NORTH);
		final var entry = new JPanel(new BorderLayout(8, 0));
		final var label = new JLabel("Driver class (optional)");
		label.setLabelFor(driverClass);
		entry.add(label, BorderLayout.WEST);
		entry.add(driverClass, BorderLayout.CENTER);
		entry.add(load, BorderLayout.EAST);
		final var controls = new JPanel(new BorderLayout(8, 8));
		controls.add(entry, BorderLayout.NORTH);
		final var selection = new JPanel(new BorderLayout(8, 0));
		final var selectionLabel = new JLabel("Loaded driver");
		selectionLabel.setLabelFor(selectedDriver);
		selection.add(selectionLabel, BorderLayout.WEST);
		selection.add(selectedDriver, BorderLayout.CENTER);
		final var destinations = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		for (final var button : List.of(useSchema, useSource, useTarget)) {
			destinations.add(button);
			receivers.put(button, null);
			button.addActionListener(event -> useSelected(button));
		}
		selection.add(destinations, BorderLayout.EAST);
		controls.add(selection, BorderLayout.SOUTH);
		header.add(controls, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		selectedDriver.addActionListener(event -> refreshActions());
		loaded.setEditable(false);
		loaded.setLineWrap(true);
		loaded.setWrapStyleWord(true);
		final var scroll = new JScrollPane(loaded);
		scroll.setBorder(BorderFactory.createTitledBorder("Added libraries and driver classes"));
		add(scroll, BorderLayout.CENTER);
		add(statusPanel, BorderLayout.SOUTH);
		load.addActionListener(event -> {
			if (busy || blocked.getAsBoolean()) {
				status.setText("Wait for current operations to finish before adding JDBC libraries.");
				return;
			}
			final var chooser = dialogs.create(new FileNameExtensionFilter("JDBC driver JARs", "jar"), null);
			chooser.setMultiSelectionEnabled(true);
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				loadFrom(java.util.Arrays.stream(chooser.getSelectedFiles()).map(java.io.File::toPath).toList(),
						driverClass.getText());
		});
		for (final var target : new JComponent[] { this, loaded, load })
			FileDropHandler.files(target, List.of("jar"), () -> !busy && !blocked.getAsBoolean(),
					this::droppedLibraries, FileDropHandler.errors(status, statusPanel));
		refreshLibraries();
	}

	private boolean droppedLibraries(final List<Path> paths) {
		if (busy || blocked.getAsBoolean())
			return false;
		loadFrom(paths, driverClass.getText());
		return busy;
	}

	void setBlockedSupplier(final BooleanSupplier blocked) {
		this.blocked = java.util.Objects.requireNonNull(blocked);
	}

	void setConnectionReceivers(final java.util.function.Predicate<String> schema,
			final java.util.function.Predicate<String> source, final java.util.function.Predicate<String> target) {
		receivers.put(useSchema, schema);
		receivers.put(useSource, source);
		receivers.put(useTarget, target);
		refreshActions();
	}

	private void useSelected(final JButton button) {
		final var receiver = receivers.get(button);
		final var name = (String) selectedDriver.getSelectedItem();
		if (busy || receiver == null || name == null)
			return;
		if (blocked.getAsBoolean()) {
			status.setText("Wait for current operations before selecting a connection driver.");
			return;
		}
		status.setText(
				receiver.test(name) ? "Driver class selected: " + name + ". Review the connection and build a new plan."
						: "Connection form is busy. Wait for it to finish, then try again.");
	}

	boolean isBusy() {
		return busy;
	}

	void loadFrom(final List<Path> files, final String className) {
		if (busy || blocked.getAsBoolean()) {
			status.setText("Wait for current operations to finish before adding JDBC libraries.");
			return;
		}
		final var selected = List.copyOf(files);
		busy(true);
		status.setText("Loading JDBC libraries…");
		new SwingWorker<JdbcDriverLibraries.Library, Void>() {
			protected JdbcDriverLibraries.Library doInBackground() throws Exception {
				return libraries.add(selected, className);
			}

			protected void done() {
				try {
					final var library = get();
					refreshLibraries();
					dialogs.remember(library.files().getFirst());
					status.setText(
							"Loaded " + library.classes().size() + " JDBC driver(s). No database connection opened.");
					statusPanel.setDetails(
							status.getText() + "\nJARs: " + library.files() + "\nDriver classes: " + library.classes());
				} catch (final InterruptedException failure) {
					Thread.currentThread().interrupt();
					failed(failure);
				} catch (final ExecutionException failure) {
					failed(failure.getCause());
				} finally {
					busy(false);
				}
			}
		}.execute();
	}

	private void busy(final boolean value) {
		busy = value;
		load.setEnabled(!value);
		driverClass.setEnabled(!value);
		refreshActions();
	}

	private void refreshActions() {
		selectedDriver.setEnabled(!busy && selectedDriver.getItemCount() > 0);
		for (final var entry : receivers.entrySet())
			entry.getKey().setEnabled(!busy && selectedDriver.getSelectedItem() != null && entry.getValue() != null);
	}

	private void failed(final Throwable failure) {
		status.setText("Driver load failed. Check the JARs, dependencies and driver class.");
		statusPanel.setDetails(status.getText() + "\n" + EditorStatusPanel.failureDetails(failure));
	}

	private void refreshLibraries() {
		final var previous = selectedDriver.getSelectedItem();
		selectedDriver.removeAllItems();
		final var text = new StringBuilder();
		for (final var library : libraries.libraries()) {
			for (final var file : library.files())
				text.append(file).append('\n');
			text.append("Driver classes: ").append(String.join(", ", library.classes())).append("\n\n");
			library.classes().forEach(selectedDriver::addItem);
		}
		if (previous != null)
			selectedDriver.setSelectedItem(previous);
		loaded.setText(text.toString());
		loaded.setCaretPosition(0);
		refreshActions();
	}
}
