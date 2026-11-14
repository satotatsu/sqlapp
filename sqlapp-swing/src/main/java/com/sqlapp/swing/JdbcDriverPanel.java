/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.sqlapp.data.db.command.driver.JdbcDriverCatalog;
import com.sqlapp.data.db.command.driver.JdbcDriverStore;
import com.sqlapp.data.db.command.driver.MavenCoordinate;

/**
 * JDBC drivers: download from Maven repositories into the user's driver folder
 * (loaded again at every start), or load local JAR files for this session.
 */
final class JdbcDriverPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	static final String OTHER = "Other (Maven coordinate)";
	final JButton load = new JButton("Load driver JARs…");
	final JTextField driverClass = new JTextField();
	final JTextArea loaded = new JTextArea();
	final JComboBox<String> selectedDriver = new JComboBox<>();
	final JButton useSchema = new JButton("Use for Schema");
	final JButton useSource = new JButton("Use for source");
	final JButton useTarget = new JButton("Use for target");
	/** Catalog entries and {@link #OTHER}. */
	final JComboBox<Object> catalog = new JComboBox<>();
	final JTextField coordinate = new JTextField(28);
	final JComboBox<String> version = new JComboBox<>();
	final JButton findVersions = new JButton("Find versions");
	final JTextField downloadClass = new JTextField();
	final JButton download = new JButton("Download and load");
	final JButton cancel = new JButton("Cancel");
	final JButton repositories = new JButton("Repositories…");
	final JLabel repositoryList = new JLabel();
	final DefaultListModel<JdbcDriverStore.Installed> installedModel = new DefaultListModel<>();
	final JList<JdbcDriverStore.Installed> installed = new JList<>(installedModel);
	final JButton remove = new JButton("Remove");
	private final java.util.Map<JButton, java.util.function.Predicate<String>> receivers = new java.util.LinkedHashMap<>();
	final JLabel status = new JLabel("No additional JDBC libraries loaded. Bundled drivers remain available.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final JdbcDriverLibraries libraries;
	private final JdbcDriverStore store;
	private final EditorFileChooser dialogs = new EditorFileChooser();
	private BooleanSupplier blocked = () -> false;
	private boolean busy;
	private SwingWorker<?, ?> network;

	JdbcDriverPanel() {
		this(JdbcDriverLibraries.SESSION);
	}

	JdbcDriverPanel(final JdbcDriverLibraries libraries) {
		this(libraries, new JdbcDriverStore(JdbcDriverStore.defaultHome()));
	}

	JdbcDriverPanel(final JdbcDriverLibraries libraries, final JdbcDriverStore store) {
		super(new BorderLayout(8, 8));
		this.libraries = libraries;
		this.store = java.util.Objects.requireNonNull(store);
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		final var note = new JTextArea(
				"Bundled drivers: PostgreSQL, MySQL, SQL Server, SQLite, HSQLDB and UCanAccess. Download others, such as Oracle, from Maven:"
						+ " each JAR is checked against the repository's checksum, saved in " + store.home()
						+ " and loaded again at every start.\n"
						+ "Only use trusted drivers: loading runs Java code. Loading a driver never connects to a database.");
		note.setEditable(false);
		note.setLineWrap(true);
		note.setWrapStyleWord(true);
		note.setOpaque(false);
		note.setAlignmentX(LEFT_ALIGNMENT);
		header.add(note);
		final var downloadBox = downloadSection();
		downloadBox.setAlignmentX(LEFT_ALIGNMENT);
		header.add(downloadBox);
		final var local = new JPanel(new BorderLayout(8, 0));
		local.setBorder(BorderFactory.createTitledBorder("Local JAR files (this session only)"));
		final var label = new JLabel("Driver class (optional)");
		label.setLabelFor(driverClass);
		local.add(label, BorderLayout.WEST);
		local.add(driverClass, BorderLayout.CENTER);
		local.add(load, BorderLayout.EAST);
		load.setToolTipText("Select a driver JAR and its dependency JARs together");
		local.setAlignmentX(LEFT_ALIGNMENT);
		header.add(local);
		final var selection = new JPanel(new BorderLayout(8, 0));
		final var selectionLabel = new JLabel("Loaded driver");
		selectionLabel.setLabelFor(selectedDriver);
		selection.add(selectionLabel, BorderLayout.WEST);
		selection.add(selectedDriver, BorderLayout.CENTER);
		final var destinations = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		for (final var button : List.of(useSchema, useSource, useTarget)) {
			destinations.add(button);
			receivers.put(button, null);
			button.addActionListener(event -> useSelected(button));
		}
		selection.add(destinations, BorderLayout.EAST);
		selection.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		selection.setAlignmentX(LEFT_ALIGNMENT);
		header.add(selection);
		add(header, BorderLayout.NORTH);
		selectedDriver.addActionListener(event -> refreshActions());
		loaded.setEditable(false);
		loaded.setLineWrap(true);
		loaded.setWrapStyleWord(true);
		final var lists = new JPanel(new GridLayout(1, 2, 8, 0));
		final var installedPanel = new JPanel(new BorderLayout(4, 4));
		installedPanel.setBorder(BorderFactory.createTitledBorder("Downloaded drivers (loaded at start)"));
		installedPanel.add(new JScrollPane(installed), BorderLayout.CENTER);
		final var installedActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		installedActions.add(remove);
		installedPanel.add(installedActions, BorderLayout.SOUTH);
		installed.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index,
					final boolean selected, final boolean focused) {
				final var driver = (JdbcDriverStore.Installed) value;
				final String text = driver.coordinate()
						+ (driver.driverClass() == null ? "" : " — " + driver.driverClass())
						+ (isLoaded(driver) ? "" : " (not loaded)");
				return super.getListCellRendererComponent(list, text, index, selected, focused);
			}
		});
		installed.addListSelectionListener(event -> refreshActions());
		remove.setToolTipText("Stop loading this driver at start and delete its JARs");
		remove.addActionListener(event -> removeSelected());
		lists.add(installedPanel);
		final var scroll = new JScrollPane(loaded);
		scroll.setBorder(BorderFactory.createTitledBorder("Loaded libraries and driver classes"));
		lists.add(scroll);
		add(lists, BorderLayout.CENTER);
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
		refreshStore();
	}

	private JPanel downloadSection() {
		final var panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder("Download from Maven"));
		for (final var entry : JdbcDriverCatalog.entries())
			catalog.addItem(entry);
		catalog.addItem(OTHER);
		catalog.addActionListener(event -> chooseCatalogEntry());
		FieldHints.placeholder(coordinate, "groupId:artifactId, e.g. com.oracle.database.jdbc:ojdbc11");
		version.setEditable(true);
		version.setPrototypeDisplayValue("23.26.0.0.0.jre11-preview");
		FieldHints.placeholder(downloadClass, "Optional; blank uses JDBC service discovery");
		findVersions.setToolTipText("Read the published versions from the Maven repositories");
		download.setToolTipText("Download the driver and its runtime dependencies, verify them, save and load them");
		cancel.setEnabled(false);
		final var versionRow = new JPanel(new BorderLayout(4, 0));
		versionRow.add(version, BorderLayout.CENTER);
		versionRow.add(findVersions, BorderLayout.EAST);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(download);
		actions.add(cancel);
		actions.add(repositories);
		actions.add(repositoryList);
		row(panel, 0, "Driver", catalog);
		row(panel, 1, "Maven coordinate", coordinate);
		row(panel, 2, "Version", versionRow);
		row(panel, 3, "Driver class", downloadClass);
		row(panel, 4, "", actions);
		findVersions.addActionListener(event -> findVersions());
		download.addActionListener(event -> downloadSelected());
		cancel.addActionListener(event -> {
			if (network != null)
				network.cancel(true);
		});
		repositories.addActionListener(event -> editRepositories());
		chooseCatalogEntry();
		return panel;
	}

	private static void row(final JPanel panel, final int row, final String text, final Component field) {
		final var constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(2, 3, 2, 3);
		constraints.anchor = GridBagConstraints.WEST;
		final var label = new JLabel(text);
		label.setLabelFor(field);
		panel.add(label, constraints);
		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(field, constraints);
	}

	private void chooseCatalogEntry() {
		if (catalog.getSelectedItem() instanceof JdbcDriverCatalog.Entry entry) {
			coordinate.setText(entry.groupId() + ":" + entry.artifactId());
			downloadClass.setText(entry.driverClass());
		} else {
			coordinate.setText("");
			downloadClass.setText("");
		}
		version.removeAllItems();
		version.setSelectedItem("");
	}

	/**
	 * Selects a catalog driver and looks up its versions, without downloading; used
	 * when a connection finds no driver for its URL.
	 */
	void prepareDownload(final JdbcDriverCatalog.Entry entry) {
		if (busy)
			return;
		catalog.setSelectedItem(entry);
		status.setText("No driver for " + entry.urlPrefix() + " URLs. Choose a version, then Download and load.");
		findVersions();
	}

	private MavenCoordinate module() {
		final var parsed = MavenCoordinate.parse(coordinate.getText());
		return parsed.withVersion(null);
	}

	void findVersions() {
		if (busy || blocked.getAsBoolean()) {
			status.setText("Wait for current operations to finish.");
			return;
		}
		final MavenCoordinate module;
		try {
			module = module();
		} catch (final IllegalArgumentException invalid) {
			status.setText(invalid.getMessage());
			coordinate.requestFocusInWindow();
			return;
		}
		busy(true);
		status.setText("Reading versions of " + module.module() + "…");
		final var worker = new SwingWorker<com.sqlapp.data.db.command.driver.MavenRepositoryClient.Versions, Void>() {
			protected com.sqlapp.data.db.command.driver.MavenRepositoryClient.Versions doInBackground()
					throws Exception {
				return store.client().versions(module.groupId(), module.artifactId());
			}

			protected void done() {
				try {
					final var found = get();
					version.removeAllItems();
					found.versions().forEach(version::addItem);
					final String preferred = found.release() != null ? found.release()
							: found.versions().isEmpty() ? "" : found.versions().getFirst();
					version.setSelectedItem(preferred);
					status.setText(found.versions().size() + " version(s) of " + module.module() + ". Selected "
							+ (found.release() != null ? "the release " : "") + preferred + ".");
				} catch (final CancellationException cancelled) {
					status.setText("Version lookup cancelled.");
				} catch (final InterruptedException failure) {
					Thread.currentThread().interrupt();
					networkFailed("Version lookup failed", failure);
				} catch (final ExecutionException failure) {
					networkFailed("Version lookup failed", failure.getCause());
				} finally {
					network = null;
					busy(false);
				}
			}
		};
		network = worker;
		cancel.setEnabled(true);
		worker.execute();
	}

	void downloadSelected() {
		if (busy || blocked.getAsBoolean()) {
			status.setText("Wait for current operations to finish before adding JDBC libraries.");
			return;
		}
		final MavenCoordinate target;
		try {
			final var parsed = MavenCoordinate.parse(coordinate.getText());
			final Object chosen = version.getSelectedItem();
			final String selected = parsed.version() != null ? parsed.version()
					: chosen == null ? "" : chosen.toString().trim();
			if (selected.isEmpty())
				throw new IllegalArgumentException("Choose a version first: use Find versions or type one.");
			target = parsed.withVersion(selected);
		} catch (final IllegalArgumentException invalid) {
			status.setText(invalid.getMessage());
			return;
		}
		final String className = downloadClass.getText().trim();
		busy(true);
		status.setText("Resolving " + target + "…");
		final var log = new StringBuilder();
		final var worker = new SwingWorker<String, String>() {
			private JdbcDriverStore.Installed saved;

			protected String doInBackground() throws Exception {
				saved = store.install(target, className, store.client(), this::publish);
				try {
					final var library = libraries.add(saved.files(), saved.driverClass());
					return "Downloaded and loaded " + target + ": " + String.join(", ", library.classes())
							+ ". It is loaded again at every start. No database connection opened.";
				} catch (final IllegalArgumentException loadFailure) {
					publish("Load failed: " + EditorStatusPanel.failureDetails(loadFailure));
					return "Downloaded " + target + ", but it could not be loaded now: " + reason(loadFailure)
							+ " Restart if another version of this driver is loaded; see Details.";
				}
			}

			protected void process(final List<String> lines) {
				for (final String line : lines)
					log.append(line).append('\n');
				status.setText(lines.getLast());
			}

			protected void done() {
				try {
					status.setText(get());
					statusPanel.setDetails(status.getText() + "\n\n" + log);
				} catch (final CancellationException cancelled) {
					status.setText("Download cancelled. Nothing was added to the start list.");
					statusPanel.setDetails(status.getText() + "\n\n" + log);
				} catch (final InterruptedException failure) {
					Thread.currentThread().interrupt();
					networkFailed("Download failed", failure);
				} catch (final ExecutionException failure) {
					networkFailed("Download failed", failure.getCause());
					statusPanel.setDetails(statusPanel.detailsText() + "\n\n" + log);
				} finally {
					network = null;
					refreshLibraries();
					refreshStore();
					busy(false);
				}
			}
		};
		network = worker;
		cancel.setEnabled(true);
		worker.execute();
	}

	private static String reason(final Throwable failure) {
		final String message = failure.getMessage();
		return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
	}

	private void networkFailed(final String what, final Throwable failure) {
		status.setText(what + ": " + reason(failure));
		statusPanel.setDetails(status.getText() + "\nRepositories: " + repositoryText() + "\n"
				+ EditorStatusPanel.failureDetails(failure));
	}

	private void editRepositories() {
		if (busy)
			return;
		final List<URI> current;
		try {
			current = store.repositories();
		} catch (final IllegalArgumentException invalid) {
			status.setText(invalid.getMessage());
			return;
		}
		final var text = new JTextArea(String.join("\n", current.stream().map(URI::toString).toList()), 6, 50);
		final var content = new JPanel(new BorderLayout(4, 4));
		content.add(
				new JLabel("<html>One Maven repository URL per line, tried in order (https, http or file).<br>"
						+ "Maven Central: " + com.sqlapp.data.db.command.driver.MavenRepositoryClient.MAVEN_CENTRAL
						+ "<br>Do not include user names or passwords; Java proxy settings are used.</html>"),
				BorderLayout.NORTH);
		content.add(new JScrollPane(text), BorderLayout.CENTER);
		if (JOptionPane.showConfirmDialog(this, content, "Maven repositories", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		try {
			final var uris = new ArrayList<URI>();
			for (final String line : text.getText().split("\\R"))
				if (!line.isBlank())
					uris.add(URI.create(line.trim()));
			store.setRepositories(uris);
			status.setText("Repositories saved: " + repositoryText());
		} catch (final IllegalArgumentException | java.io.IOException invalid) {
			status.setText("Repositories not saved: " + reason(invalid));
		}
		refreshStore();
	}

	private String repositoryText() {
		try {
			return String.join(", ", store.repositories().stream().map(URI::toString).toList());
		} catch (final IllegalArgumentException invalid) {
			return invalid.getMessage();
		}
	}

	/**
	 * Loads the drivers recorded in the driver folder. Called once at start.
	 */
	void loadInstalled() {
		if (busy)
			return;
		busy(true);
		status.setText("Loading downloaded JDBC drivers…");
		new SwingWorker<List<String>, Void>() {
			private int count;

			protected List<String> doInBackground() {
				final var problems = new ArrayList<String>();
				final List<JdbcDriverStore.Installed> drivers;
				try {
					drivers = store.installed();
				} catch (final IllegalArgumentException invalid) {
					return List.of(invalid.getMessage());
				}
				for (final var driver : drivers) {
					if (isLoaded(driver))
						continue;
					final var missing = driver.files().stream().filter(file -> !Files.isRegularFile(file)).toList();
					if (!missing.isEmpty()) {
						problems.add(driver.coordinate() + ": missing " + missing + ". Download it again.");
						continue;
					}
					try {
						libraries.add(driver.files(), driver.driverClass());
						count++;
					} catch (final Exception failure) {
						problems.add(driver.coordinate() + ": " + EditorStatusPanel.failureDetails(failure));
					}
				}
				return problems;
			}

			protected void done() {
				try {
					final var problems = get();
					if (problems.isEmpty())
						status.setText(count == 0 ? status.getText()
								: "Loaded " + count + " downloaded JDBC driver(s) from " + store.home() + ".");
					else {
						status.setText("Some downloaded JDBC drivers were not loaded; see Details.");
						statusPanel.setDetails(status.getText() + "\n" + String.join("\n", problems));
					}
				} catch (final InterruptedException | ExecutionException failure) {
					status.setText("Downloaded JDBC drivers were not loaded; see Details.");
					statusPanel.setDetails(EditorStatusPanel.failureDetails(failure));
				} finally {
					refreshLibraries();
					refreshStore();
					busy(false);
				}
			}
		}.execute();
	}

	private boolean isLoaded(final JdbcDriverStore.Installed driver) {
		return libraries.libraries().stream()
				.anyMatch(library -> library.files().containsAll(driver.files().stream().map(file -> {
					try {
						return file.toRealPath();
					} catch (final java.io.IOException missing) {
						return file;
					}
				}).toList()));
	}

	private void removeSelected() {
		final var driver = installed.getSelectedValue();
		if (driver == null || busy)
			return;
		if (JOptionPane.showConfirmDialog(this,
				"Remove " + driver.coordinate() + " from the downloaded drivers and delete its JARs?\n"
						+ "It stays available until the application exits if it is loaded now.",
				"Remove driver", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
			return;
		removeInstalled(driver.coordinate());
	}

	void removeInstalled(final MavenCoordinate driver) {
		try {
			final var kept = store.remove(driver);
			status.setText(kept.isEmpty() ? "Removed " + driver + ". It is not loaded at the next start."
					: "Removed " + driver + " from the start list; " + kept.size()
							+ " JAR(s) in use were kept. Delete them after restarting; see Details.");
			if (!kept.isEmpty())
				statusPanel.setDetails(
						status.getText() + "\n" + String.join("\n", kept.stream().map(Path::toString).toList()));
		} catch (final IllegalArgumentException | java.io.IOException failure) {
			status.setText("Not removed: " + reason(failure));
		}
		refreshStore();
	}

	private void refreshStore() {
		final var previous = installed.getSelectedValue();
		installedModel.clear();
		try {
			store.installed().forEach(installedModel::addElement);
		} catch (final IllegalArgumentException invalid) {
			status.setText(invalid.getMessage());
		}
		if (previous != null)
			installed.setSelectedValue(previous, true);
		repositoryList.setText("From: " + repositoryText());
		refreshActions();
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
		for (final var component : new JComponent[] { load, driverClass, catalog, coordinate, version, findVersions,
				downloadClass, download, repositories })
			component.setEnabled(!value);
		cancel.setEnabled(value && network != null);
		refreshActions();
	}

	private void refreshActions() {
		selectedDriver.setEnabled(!busy && selectedDriver.getItemCount() > 0);
		for (final var entry : receivers.entrySet())
			entry.getKey().setEnabled(!busy && selectedDriver.getSelectedItem() != null && entry.getValue() != null);
		remove.setEnabled(!busy && installed.getSelectedValue() != null);
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
		installed.repaint();
		refreshActions();
	}
}
