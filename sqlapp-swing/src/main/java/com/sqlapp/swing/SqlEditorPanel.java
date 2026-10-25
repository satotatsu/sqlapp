/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.sqlapp.data.db.command.migration.schema.MigrationSqlEditorIO;
import com.sqlapp.data.db.command.migration.schema.MigrationSqlEditorIO.Snapshot;

/**
 * SQL text editing only; saved files are inspected by the separate plan
 * workflow.
 */
final class SqlEditorPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final RSyntaxTextArea text = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, true);
	final EditorNavigationBar navigation = new EditorNavigationBar(text);
	final JButton fresh = new JButton("New");
	final JButton open = new JButton("Open…");
	final JButton reload = new JButton("Reload from disk");
	final JButton save = new JButton("Save");
	final JButton saveAs = new JButton("Save as…");
	final JButton use = new JButton("Use saved SQL directory");
	final javax.swing.JComboBox<String> product = new javax.swing.JComboBox<>(new String[] { "Select product…",
			"HSQL Database Engine", "PostgreSQL", "MySQL", "Oracle", "Microsoft SQL Server", "SQLite" });
	final javax.swing.JTextField version = new javax.swing.JTextField(7);
	final JButton inspect = new JButton("Inspect boundaries");
	final SqlSplitPreviewPanel splitPreview = new SqlSplitPreviewPanel();
	final javax.swing.JTabbedPane workspace = new javax.swing.JTabbedPane();
	private long inspectionRevision;
	private final Inspector inspector;
	final JLabel file = new JLabel();
	final JLabel status = new JLabel("Edit SQL offline. Syntax highlighting does not validate or execute SQL.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final MigrationSqlEditorIO io;
	private final Predicate<Path> directoryReceiver;
	private Snapshot snapshot;
	private long revision;
	private boolean busy;

	SqlEditorPanel(final Predicate<Path> receiver) {
		this(new MigrationSqlEditorIO(), receiver);
	}

	@FunctionalInterface
	interface Inspector {
		com.sqlapp.data.db.command.migration.schema.MigrationSqlInspection.Result inspect(String text, String product,
				String version);
	}

	SqlEditorPanel(final MigrationSqlEditorIO io, final Predicate<Path> receiver) {
		this(io, receiver, new com.sqlapp.data.db.command.migration.schema.MigrationSqlInspection()::inspect);
	}

	SqlEditorPanel(final MigrationSqlEditorIO io, final Predicate<Path> receiver, final Inspector inspector) {
		super(new BorderLayout(8, 8));
		this.io = java.util.Objects.requireNonNull(io, "io");
		this.inspector = java.util.Objects.requireNonNull(inspector, "inspector");
		directoryReceiver = java.util.Objects.requireNonNull(receiver, "receiver");
		setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		for (final var button : new JButton[] { fresh, open, reload, save, saveAs, use })
			actions.add(button);
		final var header = new JPanel(new BorderLayout(4, 4));
		header.add(actions, BorderLayout.NORTH);
		final var boundaryOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		boundaryOptions.add(new JLabel("SQL splitting"));
		boundaryOptions.add(product);
		final var versionLabel = new JLabel("DB version");
		versionLabel.setLabelFor(version);
		boundaryOptions.add(versionLabel);
		boundaryOptions.add(version);
		boundaryOptions.add(inspect);
		version.setToolTipText("Explicit version, e.g. 15.0. This does not select a database connection.");
		final var tools = new JPanel(new BorderLayout(4, 4));
		tools.add(navigation, BorderLayout.NORTH);
		tools.add(boundaryOptions, BorderLayout.SOUTH);
		header.add(tools, BorderLayout.CENTER);
		header.add(file, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		workspace.addTab("SQL editor", CodeEditors.scroll(text));
		workspace.addTab("Statement boundaries", splitPreview);
		add(workspace, BorderLayout.CENTER);
		navigation.setEditorActivation(() -> workspace.setSelectedIndex(0));
		add(statusPanel, BorderLayout.SOUTH);
		text.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				changed();
			}

			public void removeUpdate(final DocumentEvent event) {
				changed();
			}

			public void changedUpdate(final DocumentEvent event) {
				changed();
			}
		});
		fresh.addActionListener(event -> {
			if (!canDiscardChanges())
				return;
			snapshot = null;
			workspace.setSelectedIndex(0);
			navigation.history.resetWith(() -> text.setText(""));
			refresh();
		});
		open.addActionListener(event -> {
			final var chooser = chooser();
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				requestOpen(chooser.getSelectedFile().toPath());
		});
		save.addActionListener(event -> {
			if (snapshot != null)
				saveTo(snapshot.file());
		});
		reload.setToolTipText("Read the current file again; unsaved edits require confirmation. No file is written.");
		reload.addActionListener(event -> reloadFromDisk());
		saveAs.addActionListener(event -> {
			final var chooser = chooser();
			chooser.setDialogTitle("Save SQL — choose a new filename");
			chooser.setSelectedFile(new java.io.File(SchemaComparisonService.defaultSqlDraftName()));
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
				saveTo(chooser.getSelectedFile().toPath());
		});
		use.addActionListener(event -> useSavedDirectory());
		inspect.addActionListener(event -> inspectBoundaries());
		product.addActionListener(event -> inspectionChanged());
		version.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				inspectionChanged();
			}

			public void removeUpdate(final DocumentEvent event) {
				inspectionChanged();
			}

			public void changedUpdate(final DocumentEvent event) {
				inspectionChanged();
			}
		});
		EditorFileShortcuts.install(this, text, () -> !busy, () -> snapshot != null, fresh, open, save, saveAs);
		refresh();
	}

	boolean isBusy() {
		return busy;
	}

	boolean isDirty() {
		return snapshot == null ? !text.getText().isEmpty() : !snapshot.text().equals(text.getText());
	}

	boolean canDiscardChanges() {
		if (busy)
			return false;
		if (!isDirty())
			return true;
		final Object[] options = { "Discard edits", "Cancel" };
		return JOptionPane.showOptionDialog(this, "This SQL file has unsaved edits.", "Unsaved SQL",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0;
	}

	boolean requestOpen(final Path path) {
		if (!canDiscardChanges())
			return false;
		loadFrom(path);
		return true;
	}

	boolean reloadFromDisk() {
		if (busy || snapshot == null)
			return false;
		return requestOpen(snapshot.file());
	}

	private JFileChooser chooser() {
		final var chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Migration SQL", "sql"));
		return chooser;
	}

	private void changed() {
		revision++;
		inspectionChanged();
		refresh();
		if (!busy)
			status.setText("Edited. Save and build a new connected plan before applying SQL.");
	}

	private void refresh() {
		fresh.setEnabled(!busy);
		open.setEnabled(!busy);
		reload.setEnabled(!busy && snapshot != null);
		product.setEnabled(!busy);
		version.setEnabled(!busy);
		inspect.setEnabled(!busy && !text.getText().isBlank());
		save.setEnabled(!busy && snapshot != null && isDirty());
		saveAs.setEnabled(!busy && !text.getText().isBlank());
		use.setEnabled(!busy && snapshot != null && !isDirty());
		navigation.setBusy(busy);
		text.setEditable(!busy);
		file.setText((snapshot == null ? "New SQL (unsaved)" : snapshot.file().toString()) + (isDirty() ? " *" : ""));
		file.setToolTipText(file.getText());
	}

	void loadFrom(final Path path) {
		if (busy)
			return;
		final long requested = revision;
		busy = true;
		refresh();
		status.setText("Opening SQL…");
		new SwingWorker<Snapshot, Void>() {
			protected Snapshot doInBackground() throws Exception {
				return io.read(path);
			}

			protected void done() {
				try {
					final var loaded = get();
					if (revision != requested) {
						status.setText("Buffer changed; open again to replace it.");
						return;
					}
					snapshot = loaded;
					navigation.history.resetWith(() -> text.setText(loaded.text()));
					text.setCaretPosition(0);
					workspace.setSelectedIndex(0);
					status.setText(
							"Opened SQL. Review text; syntax and database compatibility have not been validated.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause());
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	void saveTo(final Path destination) {
		if (busy)
			return;
		final String buffer = text.getText();
		final String expected = snapshot != null && snapshot.file().equals(destination.toAbsolutePath().normalize())
				? snapshot.fingerprint()
				: null;
		busy = true;
		refresh();
		status.setText("Saving SQL and checking for external changes…");
		new SwingWorker<Snapshot, Void>() {
			protected Snapshot doInBackground() throws Exception {
				return io.write(destination, buffer, expected);
			}

			protected void done() {
				try {
					snapshot = get();
					status.setText(isDirty() ? "Saved earlier revision. Newer buffer edits remain unsaved."
							: "Saved SQL. Review all directory files and build a new connected plan before applying.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause());
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	void useSavedDirectory() {
		if (!use.isEnabled())
			return;
		final var saved = snapshot;
		final long requested = revision;
		busy = true;
		refresh();
		new SwingWorker<Path, Void>() {
			protected Path doInBackground() throws Exception {
				return io.migrationDirectory(saved);
			}

			protected void done() {
				try {
					final Path directory = get();
					if (revision != requested) {
						status.setText("Buffer changed; save before selecting its directory.");
						return;
					}
					status.setText(directoryReceiver.test(directory)
							? "Selected Up SQL directory. Connect and build a new plan to review all files."
							: "Plan screen is busy. Saved SQL retained; try again later.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause());
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private void inspectionChanged() {
		inspectionRevision++;
		splitPreview.clear();
		if (!busy)
			status.setText("SQL or dialect changed. Inspect boundaries again; this does not validate or execute SQL.");
	}

	void inspectBoundaries() {
		if (!inspect.isEnabled())
			return;
		final String buffer = text.getText();
		final String selectedProduct = product.getSelectedIndex() <= 0 ? null : (String) product.getSelectedItem();
		final String selectedVersion = version.getText();
		final long requested = inspectionRevision;
		splitPreview.clear();
		busy = true;
		refresh();
		status.setText("Splitting SQL offline using the selected database dialect…");
		new SwingWorker<com.sqlapp.data.db.command.migration.schema.MigrationSqlInspection.Result, Void>() {
			protected com.sqlapp.data.db.command.migration.schema.MigrationSqlInspection.Result doInBackground() {
				return inspector.inspect(buffer, selectedProduct, selectedVersion);
			}

			protected void done() {
				try {
					if (requested != inspectionRevision) {
						status.setText("SQL or dialect changed; inspect boundaries again.");
						return;
					}
					final var result = get();
					splitPreview.showResult(result);
					workspace.setSelectedComponent(splitPreview);
					status.setText("Previewed " + result.sqlParts()
							+ " SQL statements / batches. No SQL validation or execution performed.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failed(exception);
				} catch (final ExecutionException exception) {
					failed(exception.getCause());
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private void failed(final Throwable failure) {
		status.setText(EditorStatusPanel.failureMessage(failure));
	}
}
