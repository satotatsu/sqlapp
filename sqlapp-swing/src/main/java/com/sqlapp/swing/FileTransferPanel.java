/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.*;

import com.sqlapp.data.db.command.migration.transfer.TransferFilePackageIO;
import com.sqlapp.data.db.command.migration.transfer.TransferFileImportService;

/**
 * GUI authoring/export/review/import adapter; shared services own all migration
 * rules.
 */
final class FileTransferPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	enum Action {
		DISCOVER, EXPORT, REVIEW, IMPORT, TEST
	}

	@FunctionalInterface
	interface Backend {
		Object perform(Action action, Path input, Path workspace, PlanningJdbc.Spec spec, char[] password,
				String schema, FileTransferDesktopService.Reviewed reviewed) throws Exception;
	}

	final JComboBox<String> step = new JComboBox<>(new String[] { "Export source to CSV", "Import CSV package" });
	final ConnectionFields source = new ConnectionFields("Source database — export only");
	final ConnectionFields target = new ConnectionFields("Target database — import only");
	final JTextField job = new JTextField();
	final JTextField manifest = new JTextField();
	final JTextField workspace = new JTextField();
	final JTextField sourceSchema = new JTextField();
	final JButton create = new JButton("New CSV job…");
	final JButton export = new JButton("Export source to CSV");
	final JButton review = new JButton("Review package and target");
	final JButton apply = new JButton("Import reviewed package…");
	final JToggleButton drivers = new JToggleButton("Driver class");
	final JLabel status = new JLabel(
			"Choose Export to create a job without YAML, or Import to use a completed package.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	final ReadOnlyCodePanel result = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON, false));
	final ReadOnlyCodePanel schemaSql = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL, false));
	final JTabbedPane resultTabs = new JTabbedPane();
	private final JPanel connections = new JPanel(new CardLayout());
	private final JPanel settings = new JPanel(new CardLayout());
	private final Backend backend;
	private final java.util.function.Function<DataMigrationDraftService.Prepared, Path> draftDialog;
	private final EditorFileChooser files = new EditorFileChooser();
	private boolean busy;
	private long revision;
	private FileTransferDesktopService.Reviewed reviewed;

	FileTransferPanel() {
		this(new FileTransferDesktopService(), null);
	}

	FileTransferPanel(Backend backend,
			java.util.function.Function<DataMigrationDraftService.Prepared, Path> presenter) {
		super(new BorderLayout(8, 8));
		this.backend = backend;
		draftDialog = presenter == null ? prepared -> FileTransferDraftDialog.show(this, prepared) : presenter;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var input = new JPanel(new BorderLayout(4, 4));
		final var heading = new JPanel(new BorderLayout(4, 4));
		heading.add(new JLabel("Move data with a portable CSV package — the source is not needed during import."),
				BorderLayout.NORTH);
		heading.add(step, BorderLayout.CENTER);
		heading.add(
				new JLabel("Same product, numeric keys and scalar columns; optionally create missing target tables."),
				BorderLayout.SOUTH);
		input.add(heading, BorderLayout.NORTH);
		connections.add(source, "0");
		connections.add(target, "1");
		final var exportSettings = new JPanel(new GridLayout(0, 1, 4, 4));
		exportSettings.add(row("Saved CSV job (optional for New job)", job, false, List.of("yaml", "yml")));
		exportSettings.add(row("Source schema (blank: current)", sourceSchema, null, List.of()));
		final var importSettings = new JPanel(new GridLayout(0, 1, 4, 4));
		importSettings.add(row("Completed package manifest", manifest, false, List.of("json")));
		importSettings.add(row("Save import results to", workspace, true, List.of()));
		settings.add(exportSettings, "0");
		settings.add(importSettings, "1");
		final var form = new JPanel(new BorderLayout(4, 4));
		form.add(settings, BorderLayout.NORTH);
		form.add(connections, BorderLayout.CENTER);
		input.add(form, BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(create);
		actions.add(export);
		actions.add(review);
		actions.add(apply);
		actions.add(drivers);
		input.add(actions, BorderLayout.SOUTH);
		resultTabs.addTab("Review / result", result);
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(input), resultTabs);
		split.setResizeWeight(0.55);
		add(split, BorderLayout.CENTER);
		add(statusPanel, BorderLayout.SOUTH);
		step.addActionListener(event -> {
			cards();
			changed();
		});
		drivers.addActionListener(event -> {
			source.showDriver(drivers.isSelected());
			target.showDriver(drivers.isSelected());
			revalidate();
		});
		create.addActionListener(event -> {
			if (isBusy() || !validConnection(source))
				return;
			final var chooser = files.forDirectory("");
			chooser.setDialogTitle("Choose a folder for CSV transfer jobs");
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				discover(chooser.getSelectedFile().toPath());
		});
		export.addActionListener(event -> run(Action.EXPORT, null));
		review.addActionListener(event -> run(Action.REVIEW, null));
		apply.addActionListener(event -> {
			if (isBusy() || reviewed == null)
				return;
			if (workspace.getText().isBlank()) {
				final var chooser = files.forDirectory("");
				chooser.setDialogTitle("Choose a folder for import results");
				if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
					return;
				workspace.setText(chooser.getSelectedFile().getAbsolutePath());
			}
			importReviewed(() -> JOptionPane.showConfirmDialog(this,
					"Import the reviewed CSV package into the checked target?\nPackage fingerprint: "
							+ reviewed.review().manifestFingerprint()
							+ "\nRows may remain committed if copying or verification later fails. Existing rows are never cleared."
							+ "\nMissing-table DDL statements: "
							+ reviewed.review().schemaPreparation().statements().size() + ". Compatibility warnings: "
							+ reviewed.review().schemaPreparation().warnings().size()
							+ ". DDL may remain applied after failure. Review generated SQL before confirming.",
					"Confirm CSV import", JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION);
		});
		for (var fields : new ConnectionFields[] { source, target }) {
			fields.test.addActionListener(event -> run(Action.TEST, null));
			fields.buildUrl.addActionListener(event -> {
				if (isBusy())
					return;
				setBusy(true);
				try {
					final String url = JdbcUrlBuilderPanel.showDialog(this, fields.jdbcUrl.getText(),
							fields == source ? "Export source" : "Import target");
					if (url != null)
						fields.jdbcUrl.setText(url);
				} finally {
					setBusy(false);
				}
			});
			for (var field : new JTextField[] { fields.jdbcUrl, fields.username, fields.driverClass })
				listen(field);
		}
		listen(job);
		listen(manifest);
		listen(sourceSchema);
		cards();
		refresh();
	}

	private JPanel row(String text, JTextField field, Boolean directory, List<String> extensions) {
		final var panel = new JPanel(new BorderLayout(6, 0));
		final var label = new JLabel(text);
		label.setLabelFor(field);
		panel.add(label, BorderLayout.WEST);
		panel.add(field, BorderLayout.CENTER);
		if (directory != null) {
			FileDropHandler.path(field, directory, extensions, () -> !isBusy(),
					FileDropHandler.errors(status, statusPanel));
			final var browse = new JButton("Browse…");
			browse.addActionListener(event -> {
				if (isBusy())
					return;
				final var chooser = directory ? files.forDirectory(field.getText())
						: files.forFile(new javax.swing.filechooser.FileNameExtensionFilter("Transfer files",
								extensions.toArray(String[]::new)), field.getText());
				if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
					field.setText(chooser.getSelectedFile().getAbsolutePath());
			});
			panel.add(browse, BorderLayout.EAST);
		}
		return panel;
	}

	private void listen(JTextField field) {
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent event) {
				changed();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent event) {
				changed();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent event) {
				changed();
			}
		});
	}

	private void changed() {
		revision++;
		reviewed = null;
		resultTabs.remove(schemaSql);
		schemaSql.showText("");
		if (!busy)
			status.setText("Inputs changed. Review the package again before import.");
		refresh();
	}

	private void cards() {
		((CardLayout) connections.getLayout()).show(connections, Integer.toString(step.getSelectedIndex()));
		((CardLayout) settings.getLayout()).show(settings, Integer.toString(step.getSelectedIndex()));
		final boolean importing = step.getSelectedIndex() == 1;
		create.setVisible(!importing);
		export.setVisible(!importing);
		review.setVisible(importing);
		apply.setVisible(importing);
	}

	boolean isBusy() {
		return busy || source.profile.isBusy() || target.profile.isBusy();
	}

	private void setBusy(boolean value) {
		busy = value;
		enable(connections, !value);
		enable(settings, !value);
		step.setEnabled(!value);
		refresh();
	}

	private static void enable(Container parent, boolean enabled) {
		for (var child : parent.getComponents()) {
			child.setEnabled(enabled);
			if (child instanceof Container container)
				enable(container, enabled);
		}
	}

	private void refresh() {
		create.setEnabled(!busy);
		export.setEnabled(!busy);
		review.setEnabled(!busy);
		apply.setEnabled(!busy && reviewed != null);
		drivers.setEnabled(!busy);
	}

	private boolean validConnection(ConnectionFields fields) {
		try {
			fields.snapshot().spec();
			return true;
		} catch (IllegalArgumentException failure) {
			status.setText("Enter a JDBC URL or load a connection profile first.");
			fields.jdbcUrl.requestFocusInWindow();
			return false;
		}
	}

	void discover(Path directory) {
		run(Action.DISCOVER, directory);
	}

	void importReviewed(BooleanSupplier confirmation) {
		if (isBusy() || reviewed == null || step.getSelectedIndex() != 1)
			return;
		final Path directory;
		try {
			directory = Path.of(workspace.getText());
			if (workspace.getText().isBlank() || !java.nio.file.Files.isDirectory(directory)
					|| !java.nio.file.Files.isWritable(directory))
				throw new IllegalArgumentException();
		} catch (RuntimeException failure) {
			status.setText("Choose an existing writable folder for import results before confirming.");
			return;
		}
		final var snapshot = reviewed;
		final long requested = revision;
		boolean accepted;
		setBusy(true);
		try {
			accepted = confirmation.getAsBoolean();
		} finally {
			setBusy(false);
		}
		if (!accepted) {
			status.setText("Import cancelled. No rows copied.");
			return;
		}
		if (snapshot != reviewed || requested != revision) {
			status.setText("Inputs changed. Review and confirm again.");
			return;
		}
		run(Action.IMPORT, directory);
	}

	private void run(Action action, Path directory) {
		if (isBusy())
			return;
		final boolean importing = step.getSelectedIndex() == 1;
		if (action == Action.IMPORT && (reviewed == null || !importing))
			return;
		final var fields = action == Action.DISCOVER || action == Action.EXPORT ? source : importing ? target : source;
		if (!validConnection(fields))
			return;
		final Path input;
		try {
			final String path = action == Action.EXPORT ? job.getText() : manifest.getText();
			if (action != Action.TEST && action != Action.DISCOVER && path.isBlank())
				throw new IllegalArgumentException();
			input = action == Action.TEST || action == Action.DISCOVER ? null : Path.of(path);
		} catch (RuntimeException failure) {
			status.setText("Choose a saved CSV job or completed package manifest first.");
			return;
		}
		final var spec = fields.snapshot().spec();
		final char[] password = fields.password.getPassword();
		final var prior = reviewed;
		final long requested = revision;
		final String schema = sourceSchema.getText();
		if (action == Action.IMPORT || action == Action.EXPORT)
			fields.password.setText("");
		setBusy(true);
		status.setText(action == Action.IMPORT ? "Importing and verifying; committed chunks may be retained on failure."
				: "Working: " + action + ". No rows are imported by this action.");
		new SwingWorker<Object, Void>() {
			@Override
			protected Object doInBackground() throws Exception {
				try {
					return backend.perform(action, input, directory, spec, password, schema, prior);
				} finally {
					Arrays.fill(password, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					final Object value = get();
					if (action == Action.DISCOVER) {
						if (requested != revision) {
							status.setText("Inputs changed. Start New CSV job again.");
							return;
						}
						final Path saved = draftDialog.apply((DataMigrationDraftService.Prepared) value);
						if (saved != null && requested == revision) {
							job.setText(saved.toString());
							status.setText("Shared CSV job saved. Choose Export source to CSV to create the package.");
						} else
							status.setText("Job creation cancelled or inputs changed. Previous job retained.");
					} else if (action == Action.REVIEW) {
						if (requested != revision) {
							status.setText("Inputs changed. Review package again.");
							return;
						}
						reviewed = (FileTransferDesktopService.Reviewed) value;
						show(value);
						status.setText("Package and target reviewed. Compatibility warnings: "
								+ reviewed.review().schemaPreparation().warnings().size()
								+ ". Inspect the review and generated DDL before import.");
					} else if (action == Action.EXPORT) {
						final var saved = (TransferFilePackageIO.Snapshot) value;
						show(value);
						if (requested == revision)
							manifest.setText(saved.file().toString());
						status.setText("CSV package exported. Warnings: " + saved.manifest().warnings().size()
								+ ". Inspect the result before import. Manifest: " + saved.file());
					} else if (action == Action.IMPORT) {
						reviewed = null;
						show(value);
						status.setText("CSV import finished. Compatibility warnings: "
								+ ((TransferFileImportService.Outcome) value).schemaPreparation().warnings().size()
								+ ". Inspect verification and the saved audit before another run.");
					} else
						status.setText(requested == revision ? "Connection check: " + value
								: "Inputs changed. Test the connection again.");
				} catch (Exception failure) {
					final Throwable cause = failure instanceof java.util.concurrent.ExecutionException
							? failure.getCause()
							: failure;
					if (action == Action.REVIEW || action == Action.IMPORT)
						reviewed = null;
					if (cause instanceof TransferFileImportService.ImportException imported)
						show(imported.outcome());
					status.setText("CSV operation failed; inspect Details and any retained result before retrying.");
					statusPanel.setDetails(status.getText() + "\n" + EditorStatusPanel.failureDetails(cause));
				} finally {
					setBusy(false);
				}
			}
		}.execute();
	}

	private void show(Object value) {
		result.showText(new com.sqlapp.util.JsonConverter().toJsonString(value));
		resultTabs.remove(schemaSql);
		final var statements = value instanceof FileTransferDesktopService.Reviewed review
				? review.review().schemaPreparation().statements()
				: value instanceof TransferFileImportService.Outcome outcome ? outcome.schemaPreparation().planned()
						: List.<com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation.Statement>of();
		if (!statements.isEmpty()) {
			final var text = new StringBuilder(
					"-- Reviewed missing-table preparation. DDL may remain applied after failure.\n\n");
			final var warnings = value instanceof FileTransferDesktopService.Reviewed review
					? review.review().schemaPreparation().warnings()
					: ((TransferFileImportService.Outcome) value).schemaPreparation().warnings();
			for (String warning : warnings)
				text.append("-- WARNING: ").append(warning.replace('\n', ' ').replace('\r', ' ')).append('\n');
			for (int index = 0; index < statements.size(); index++)
				text.append("-- Statement ").append(index + 1).append(" / ").append(statements.get(index).taskId())
						.append('\n').append(statements.get(index).sql()).append("\n\n");
			schemaSql.showText(text.toString());
			resultTabs.addTab("Schema SQL", schemaSql);
			resultTabs.setSelectedComponent(value instanceof FileTransferDesktopService.Reviewed ? schemaSql : result);
		}
	}
}
