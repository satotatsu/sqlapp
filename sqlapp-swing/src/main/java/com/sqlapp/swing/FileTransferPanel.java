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
import com.sqlapp.data.db.command.migration.transfer.TransferStagingComparisonReportIO;

/**
 * GUI authoring/export/review/import adapter; shared services own all migration
 * rules.
 */
final class FileTransferPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	enum Action {
		DISCOVER, EXPORT, INSPECT, INSPECT_DATA, REVIEW, IMPORT, TEST, COMPARE_STAGING;

		boolean isInspection() {
			return this == INSPECT || this == INSPECT_DATA;
		}
	}

	@FunctionalInterface
	interface Backend {
		Object perform(Action action, Path input, Path workspace, PlanningJdbc.Spec spec, char[] password,
				String schema, FileTransferDesktopService.Reviewed reviewed) throws Exception;
	}

	final JComboBox<String> step = new JComboBox<>(new String[] { "Export source to files", "Import file package" });
	final ConnectionFields source = new ConnectionFields("Source database — export only");
	final ConnectionFields target = new ConnectionFields("Target database — import only");
	final JTextField job = new JTextField();
	final JTextField manifest = new JTextField();
	final JTextField workspace = new JTextField();
	final JTextField sourceSchema = new JTextField();
	final JButton create = new JButton("New job…");
	final JButton jobSettings = new JButton("Job settings…");
	final JButton export = new JButton("Export source to files");
	final JButton review = new JButton("Review import");
	final JButton inspectPackage = new JButton("Inspect package (no connection)");
	final JButton previewPackage = new JButton("Preview package data…");
	final JCheckBox inspectValues = new JCheckBox(
			"Also validate packaged values during inspection (offline; may take longer)");
	final JButton apply = new JButton("Execute reviewed import…");
	final JButton compareStaging = new JButton("Compare staging…");
	private final ReportReviewView stagingComparison = new ReportReviewView();
	private final StagingComparisonHistory comparisonHistory = new StagingComparisonHistory();
	private Path retainedImportResult;
	final JButton nextImport = new JButton("Continue to import this package…");
	final JToggleButton drivers = new JToggleButton("Show driver class");
	final JLabel status = new JLabel(
			"Choose Export to create a job without YAML, or Import to use a completed package.");
	final StagingComparisonSaveActions comparisonActions = new StagingComparisonSaveActions(this::isBusy, this::setBusy,
			status::setText, workspace::getText);
	final SavedReportButton savedComparison = comparisonActions.saved;
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	final ReadOnlyCodePanel result = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON, false));
	final ReadOnlyCodePanel schemaSql = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL, false));
	final GeneratedSqlDraftActions schemaDraft = new GeneratedSqlDraftActions(schemaSql, this::isBusy, this::setBusy,
			status::setText,
			failure -> statusPanel.setDetails("SQL draft save failed.\n" + EditorStatusPanel.failureDetails(failure)));
	final JTabbedPane resultTabs = new JTabbedPane();
	private final TransferWarningTabs warningTabs = new TransferWarningTabs();
	private final TransferTablePreparationTabs tableTabs = new TransferTablePreparationTabs();
	private final TransferSchemaStatementTabs statementTabs = new TransferSchemaStatementTabs();
	private final TransferVerificationTabs verificationTabs = new TransferVerificationTabs();
	private final TransferExecutionProgressTabs progressTabs = new TransferExecutionProgressTabs();
	private final TransferPackageTablesTabs packageTabs = new TransferPackageTablesTabs();
	final JTextArea summary = new JTextArea(
			"1. Export: create or choose a job and export a file package.\n2. Import: choose the completed manifest and enter the target connection.\n3. Review warnings and optional table creation, then confirm import.");
	private boolean hasSummary;
	final ReadOnlyCodePanel gradle = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_GROOVY, false));
	private final JPanel connections = new JPanel(new CardLayout());
	// Numbered steps; their order and titles follow the Export / Import choice.
	private final JPanel steps = new JPanel();
	private final StepSection connectStep = new StepSection("");
	private final StepSection packageStep = new StepSection("");
	private final StepSection runStep = new StepSection("");
	private final JPanel settings = new JPanel(new CardLayout());
	private final Backend backend;
	private final java.util.function.Function<DataMigrationDraftService.Prepared, Path> draftDialog;
	private final EditorFileChooser files = new EditorFileChooser();
	private boolean busy;
	private JTextField sourceFileInput;
	private List<Path> sourceFiles = List.of();
	private long revision;
	private FileTransferDesktopService.Reviewed reviewed;
	private TransferFilePackageIO.Snapshot exportedForImport;

	FileTransferPanel() {
		this(new FileTransferDesktopService(), null);
	}

	FileTransferPanel(Backend backend,
			java.util.function.Function<DataMigrationDraftService.Prepared, Path> presenter) {
		super(new BorderLayout(8, 8));
		this.backend = backend;
		tableTabs.setStatementNavigator(task -> statementTabs.showTask(resultTabs, task));
		packageTabs.setPreviewNavigator(selection -> openPackagePreview(selection.manifest(), selection.fingerprint(),
				selection.task(), selection.description()));
		progressTabs.setNavigators(task -> statementTabs.showTask(resultTabs, task),
				task -> verificationTabs.showTask(resultTabs, task));
		draftDialog = presenter == null
				? prepared -> FileTransferDraftDialog.show(this, prepared,
						ConnectionCatalog.jobNames(source.connectionName(), target.connectionName()))
				: presenter;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var input = new JPanel(new BorderLayout(4, 4));
		final var heading = new JPanel(new BorderLayout(4, 4));
		heading.add(new JLabel("Move data with a portable file package — the source is not needed during import."),
				BorderLayout.NORTH);
		heading.add(step, BorderLayout.CENTER);
		heading.add(new JLabel(
				"Numeric keys and supported scalar columns; review compatibility warnings and optional table creation."),
				BorderLayout.SOUTH);
		input.add(heading, BorderLayout.NORTH);
		connections.add(source, "0");
		connections.add(target, "1");
		final var exportSettings = new JPanel(new GridLayout(0, 1, 4, 4));
		exportSettings.add(row("Saved job",
				FieldHints.placeholder(job, "Optional when creating a New job; choose a saved job to reuse it"), false,
				List.of("yaml", "yml")));
		exportSettings.add(row("Source schema",
				FieldHints.placeholder(sourceSchema, "Blank uses the source connection's current schema"), null,
				List.of()));
		final var importSettings = new JPanel(new GridLayout(0, 1, 4, 4));
		importSettings.add(row("Completed package (manifest or folder)", manifest, false, List.of("json")));
		importSettings.add(row("Save import results to", workspace, true, List.of()));
		importSettings.add(inspectValues);
		// Cards share the tallest card's height; keep rows at their natural height.
		settings.add(top(exportSettings), "0");
		settings.add(top(importSettings), "1");
		connectStep.add(connections, BorderLayout.CENTER);
		final var connectActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		connectActions.add(drivers);
		connectStep.add(connectActions, BorderLayout.SOUTH);
		packageStep.add(settings, BorderLayout.CENTER);
		steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
		input.add(steps, BorderLayout.CENTER);
		// All actions stay in the fixed bottom step so scrolling the form never hides
		// them.
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(create);
		actions.add(jobSettings);
		actions.add(export);
		actions.add(inspectPackage);
		actions.add(previewPackage);
		actions.add(review);
		actions.add(apply);
		actions.add(compareStaging);
		runStep.add(actions, BorderLayout.CENTER);
		stagingComparison.add(comparisonActions, BorderLayout.SOUTH);
		compareStaging.addActionListener(event -> {
			if (isBusy() || !validConnection(target))
				return;
			if (retainedImportResult != null) {
				compareStaging(retainedImportResult);
				return;
			}
			var chooser = files.forOutputDirectory(
					new javax.swing.filechooser.FileNameExtensionFilter("Import result JSON", "json"),
					"import-result.json", workspace.getText());
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				compareStaging(chooser.getSelectedFile().toPath());
		});
		FileDropHandler.file(compareStaging, List.of("json"), () -> !isBusy() && step.getSelectedIndex() == 1,
				path -> compareStaging(path), FileDropHandler.errors(status, statusPanel));
		summary.setEditable(false);
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		summary.setMargin(new Insets(8, 8, 8, 8));
		final var summaryPanel = new JPanel(new BorderLayout(4, 4));
		summaryPanel.add(new JScrollPane(summary), BorderLayout.CENTER);
		final var nextActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		nextActions.add(nextImport);
		summaryPanel.add(nextActions, BorderLayout.SOUTH);
		resultTabs.addTab("Summary", summaryPanel);
		resultTabs.addTab("Details", result);
		final var controls = new JPanel(new BorderLayout(4, 4));
		controls.add(new JScrollPane(input), BorderLayout.CENTER);
		controls.add(runStep, BorderLayout.SOUTH);
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, controls, resultTabs);
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
			chooser.setDialogTitle("Choose a folder for file transfer jobs");
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				discover(chooser.getSelectedFile().toPath());
		});
		export.addActionListener(event -> run(Action.EXPORT, null));
		jobSettings.addActionListener(event -> editSettings(path -> TransferJobSettingsDialog.show(this, path,
				com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.DataMode.FILE)));
		nextImport.addActionListener(event -> {
			if (isBusy() || exportedForImport == null || step.getSelectedIndex() != 0)
				return;
			final var exported = exportedForImport;
			step.setSelectedIndex(1);
			manifest.setText(exported.file().toString());
			show(exported);
			summary.setText(FileTransferSummary.readyForImport(exported));
			summary.setCaretPosition(0);
			showGradle(null, exported.file(), exported.fingerprint());
			status.setText(
					"Package selected for import. Enter the target connection and review; no rows have been imported.");
			target.jdbcUrl.requestFocusInWindow();
		});
		review.addActionListener(event -> run(Action.REVIEW, null));
		inspectPackage.addActionListener(
				event -> run(inspectValues.isSelected() ? Action.INSPECT_DATA : Action.INSPECT, null));
		inspectValues.addItemListener(event -> changed());
		previewPackage.addActionListener(event -> {
			if (isBusy() || manifest.getText().isBlank())
				return;
			try {
				openPackagePreview(Path.of(manifest.getText()), null, null, null);
			} catch (Exception failure) {
				status.setText(EditorStatusPanel.failureMessage(failure));
			}
		});
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
					"Import the reviewed file package into the checked target?\nPackage fingerprint: "
							+ reviewed.review().manifestFingerprint()
							+ (reviewed.review().staging() == null
									? "\nRows may remain committed if copying or verification later fails."
									: "\nAfter staging and verification, replace all rows in the selected target tables in one transaction.")
							+ "\nMissing-table DDL statements: "
							+ reviewed.review().schemaPreparation().statements().size() + ". Compatibility warnings: "
							+ reviewed.review().schemaPreparation().warnings().size()
							+ ". DDL may remain applied after failure. Review generated SQL before confirming.",
					"Confirm file import", JOptionPane.OK_CANCEL_OPTION,
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
		DisabledReason.install(apply, () -> busy ? "Wait for the current operation to finish."
				: reviewed == null ? "Choose Review import first. Only the current review can be imported." : null);
		DisabledReason.install(previewPackage, () -> isBusy() ? "Wait for the current operation to finish."
				: manifest.getText().isBlank() ? "Choose a completed package manifest first." : null);
		cards();
		refresh();
	}

	private static JPanel top(final java.awt.Component content) {
		final var panel = new JPanel(new BorderLayout());
		panel.add(content, BorderLayout.NORTH);
		return panel;
	}

	private JPanel row(String text, JTextField field, Boolean directory, List<String> extensions) {
		final var panel = new JPanel(new BorderLayout(6, 0));
		final var label = new JLabel(text);
		label.setLabelFor(field);
		panel.add(label, BorderLayout.WEST);
		panel.add(field, BorderLayout.CENTER);
		if (directory != null) {
			if (field == manifest)
				FileDropHandler.fileOrDirectory(field, extensions, () -> !isBusy(),
						FileDropHandler.errors(status, statusPanel));
			else
				FileDropHandler.path(field, directory, extensions, () -> !isBusy(),
						FileDropHandler.errors(status, statusPanel));
			final var browse = new JButton("Browse…");
			browse.addActionListener(event -> {
				if (isBusy())
					return;
				final var chooser = directory ? files.forDirectory(field.getText())
						: files.forFile(new javax.swing.filechooser.FileNameExtensionFilter("Transfer files",
								extensions.toArray(String[]::new)), field.getText());
				if (field == manifest) {
					chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
					chooser.setDialogTitle("Choose manifest.json or its completed package folder");
				}
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
		retainedImportResult = null;
		comparisonActions.clear();
		comparisonHistory.clear();
		resultTabs.remove(stagingComparison);
		exportedForImport = null;
		if (hasSummary && !summary.getText().startsWith("Inputs changed."))
			summary.setText(
					"Inputs changed. The previous result below cannot authorize import.\n\n" + summary.getText());
		clearGradle();
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
		steps.removeAll();
		steps.add(importing ? packageStep : connectStep);
		steps.add(importing ? connectStep : packageStep);
		connectStep.setTitle(importing ? "2. Connect the target" : "1. Connect the source");
		packageStep.setTitle(importing ? "1. Choose the package" : "2. Job");
		runStep.setTitle(importing ? "3. Inspect, review and import" : "3. Create or open the job, then export");
		steps.revalidate();
		steps.repaint();
		create.setVisible(!importing);
		jobSettings.setVisible(!importing);
		export.setVisible(!importing);
		review.setVisible(importing);
		inspectPackage.setVisible(importing);
		previewPackage.setVisible(importing);
		apply.setVisible(importing);
		compareStaging.setVisible(importing);
	}

	boolean isBusy() {
		return busy || source.profile.isBusy() || target.profile.isBusy();
	}

	private void openPackagePreview(Path input, String fingerprint, String task,
			TransferFileImportService.PackageDescription description) {
		if (isBusy())
			return;
		try {
			setBusy(true);
			FileTransferPreviewPanel.show(this, input, fingerprint, task, description);
		} catch (Exception failure) {
			status.setText(EditorStatusPanel.failureMessage(failure));
		} finally {
			setBusy(false);
		}
	}

	private void setBusy(boolean value) {
		busy = value;
		ComponentEnablement.setTreeEnabled(connections, !value);
		ComponentEnablement.setTreeEnabled(settings, !value);
		step.setEnabled(!value);
		refresh();
	}

	private void refresh() {
		schemaDraft.refresh();
		create.setEnabled(!busy);
		jobSettings.setEnabled(!busy);
		export.setEnabled(!busy);
		review.setEnabled(!busy);
		inspectPackage.setEnabled(!busy);
		previewPackage.setEnabled(!isBusy() && !manifest.getText().isBlank());
		apply.setEnabled(!busy && reviewed != null);
		compareStaging.setEnabled(!busy && step.getSelectedIndex() == 1);
		comparisonActions.refresh();
		drivers.setEnabled(!busy);
		nextImport.setVisible(exportedForImport != null && step.getSelectedIndex() == 0);
		nextImport.setEnabled(!busy && exportedForImport != null && step.getSelectedIndex() == 0);
		final JButton next = step.getSelectedIndex() == 1 ? apply.isEnabled() ? apply : review
				: nextImport.isVisible() ? nextImport : job.getText().isBlank() ? create : export;
		PrimaryAction.highlight(next, create, export, review, apply, nextImport);
	}

	private boolean validConnection(ConnectionFields fields) {
		try {
			fields.snapshot().spec();
			return true;
		} catch (IllegalArgumentException failure) {
			if (fields == source && sourceFileInput != null) {
				status.setText("Choose an existing readable source database file first.");
				sourceFileInput.requestFocusInWindow();
			} else {
				status.setText("Enter a JDBC URL or load a connection profile first.");
				fields.jdbcUrl.requestFocusInWindow();
			}
			return false;
		}
	}

	void discover(Path directory) {
		run(Action.DISCOVER, directory);
	}

	void editSettings(
			java.util.function.Function<Path, com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO.Snapshot> presenter) {
		if (isBusy() || step.getSelectedIndex() != 0)
			return;
		final Path file;
		try {
			if (job.getText().isBlank())
				throw new IllegalArgumentException();
			file = Path.of(job.getText());
		} catch (RuntimeException failure) {
			status.setText("Choose a saved job before opening settings.");
			return;
		}
		setBusy(true);
		try {
			if (presenter.apply(file) != null) {
				changed();
				status.setText(
						"Job settings saved. Export a new package; completed packages keep their original settings.");
			}
		} finally {
			setBusy(false);
		}
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

	private record Compared(TransferStagingComparisonReportIO.Report report, ReportReviewService.Review view) {
	}

	private boolean compareStaging(Path report) {
		if (isBusy() || step.getSelectedIndex() != 1 || !validConnection(target))
			return false;
		var spec = target.snapshot().spec();
		var password = target.password.getPassword();
		var requested = revision;
		resultTabs.remove(stagingComparison);
		comparisonActions.beginComparison();
		setBusy(true);
		comparisonActions.setRepeatAction(() -> compareStaging(report));
		status.setText("Comparing staging and target rows (read-only)…");
		new SwingWorker<Compared, Void>() {
			@Override
			protected Compared doInBackground() throws Exception {
				try {
					var comparison = (com.sqlapp.data.db.command.migration.transfer.TransferStagingComparisonReportIO.Report) backend
							.perform(Action.COMPARE_STAGING, report, null, spec, password, null, null);
					return new Compared(comparison, new ReportReviewService().stagingComparison(comparison));
				} finally {
					Arrays.fill(password, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					var comparison = get();
					if (requested != revision)
						return;
					stagingComparison.showReview(comparisonHistory.show(comparison.report(), comparison.view()));
					comparisonActions.show(comparison.report());
					resultTabs.addTab("Staging comparison", stagingComparison);
					resultTabs.setSelectedComponent(stagingComparison);
					status.setText("Read-only staging comparison finished.");
				} catch (Exception failure) {
					if (requested == revision) {
						var cause = failure instanceof java.util.concurrent.ExecutionException ? failure.getCause()
								: failure;
						status.setText("Staging comparison failed. See Details.");
						statusPanel.setDetails(EditorStatusPanel.failureMessage(cause));
						stagingComparison.clear();
						stagingComparison.summary
								.setText("Staging comparison failed. See Details, check the target connection "
										+ "and retained staging, then use Compare again. No rows were changed by this check.");
						resultTabs.addTab("Staging comparison", stagingComparison);
						resultTabs.setSelectedComponent(stagingComparison);
					}
				} finally {
					setBusy(false);
				}
			}
		}.execute();
		return true;
	}

	private void run(Action action, Path directory) {
		if (isBusy())
			return;
		final boolean importing = step.getSelectedIndex() == 1;
		if (action.isInspection() && !importing)
			return;
		if (action == Action.IMPORT && (reviewed == null || !importing))
			return;
		final var fields = action == Action.DISCOVER || action == Action.EXPORT ? source : importing ? target : source;
		if (!action.isInspection() && !validConnection(fields))
			return;
		final Path input;
		try {
			final String path = action == Action.EXPORT ? job.getText() : manifest.getText();
			if (action != Action.TEST && action != Action.DISCOVER && path.isBlank())
				throw new IllegalArgumentException();
			input = action == Action.TEST || action == Action.DISCOVER ? null : Path.of(path);
		} catch (RuntimeException failure) {
			status.setText("Choose a saved job or completed package manifest first.");
			return;
		}
		final var spec = action.isInspection() ? null : fields.snapshot().spec();
		final char[] password = action.isInspection() ? new char[0] : fields.password.getPassword();
		final var prior = reviewed;
		final long requested = revision;
		final String schema = sourceSchema.getText();
		if (action != Action.TEST) {
			retainedImportResult = null;
			comparisonActions.clear();
			comparisonHistory.clear();
			resultTabs.remove(stagingComparison);
			clearGradle();
			exportedForImport = null;
		}
		if (action.isInspection())
			reviewed = null;
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
							status.setText("Inputs changed. Start New job again.");
							return;
						}
						final Path saved = draftDialog.apply((DataMigrationDraftService.Prepared) value);
						if (saved != null && requested == revision) {
							job.setText(saved.toString());
							status.setText(
									"Shared file job saved. Choose Export source to files to create the package.");
						} else
							status.setText("Job creation cancelled or inputs changed. Previous job retained.");
					} else if (action.isInspection()) {
						if (requested != revision) {
							status.setText("Inputs changed. Inspect the selected package again.");
							return;
						}
						final var inspected = (FileTransferDesktopService.Inspected) value;
						show(inspected);
						showGradle(null, inspected.snapshot().file(), inspected.snapshot().fingerprint());
						status.setText(inspected.valuesValidated()
								? "Package and values checked without a connection. Review the target before import."
								: "Package file integrity checked without a connection. Review packaged values and the target before import.");
					} else if (action == Action.REVIEW) {
						if (requested != revision) {
							status.setText("Inputs changed. Review package again.");
							return;
						}
						reviewed = (FileTransferDesktopService.Reviewed) value;
						show(value);
						showGradle(null, reviewed.review().manifestFile(), reviewed.review().manifestFingerprint());
						status.setText("Package and target reviewed. Compatibility warnings: "
								+ reviewed.review().schemaPreparation().warnings().size()
								+ ". Inspect the review and generated DDL before import.");
					} else if (action == Action.EXPORT) {
						final var saved = (TransferFilePackageIO.Snapshot) value;
						if (requested == revision) {
							manifest.setText(saved.file().toString());
							show(value);
							showGradle(input, saved.file(), saved.fingerprint());
							exportedForImport = saved;
						} else {
							show(value);
							summary.setText(
									"Inputs changed. This export belongs to previous inputs.\n\n" + summary.getText());
						}
						status.setText("file package exported. Warnings: " + saved.manifest().warnings().size()
								+ ". Inspect the result before import. Manifest: " + saved.file());
					} else if (action == Action.IMPORT) {
						reviewed = null;
						show(value, prior);
						status.setText("File import finished. Compatibility warnings: "
								+ ((TransferFileImportService.Outcome) value).schemaPreparation().warnings().size()
								+ ". Inspect verification and the saved audit before another run.");
					} else
						status.setText(requested == revision ? "Connection check: " + value
								: "Inputs changed. Test the connection again.");
				} catch (Exception failure) {
					schemaDraft.clear();
					warningTabs.clear(resultTabs);
					tableTabs.clear(resultTabs);
					statementTabs.clear(resultTabs);
					verificationTabs.clear(resultTabs);
					progressTabs.clear(resultTabs);
					packageTabs.clear(resultTabs);
					final Throwable cause = failure instanceof java.util.concurrent.ExecutionException
							? failure.getCause()
							: failure;
					if (action == Action.REVIEW || action == Action.IMPORT)
						reviewed = null;
					if (cause instanceof TransferFileImportService.ImportException imported) {
						var outcome = imported.outcome();
						if (requested == revision && outcome.staging() != null && outcome.staging()
								.state() == com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement.State.COMMIT_UNCERTAIN)
							retainedImportResult = outcome.auditFile();
						show(imported.outcome(), prior);
					} else if (cause instanceof TransferPreparationFailure validation) {
						summary.setText((action == Action.EXPORT ? "Export preparation needs attention:\n\n"
								: "Target preparation needs attention:\n\n")
								+ validation.getMessage()
								+ (action == Action.EXPORT
										? "\n\nFix the source selection or name mapping, then export a new package."
										: "\n\nFix the target or job settings and review again."));
						summary.setCaretPosition(0);
						resultTabs.setSelectedIndex(0);
					} else {
						summary.setText(
								"File transfer action failed. Previous results cannot authorize import.\nInspect status Details and review again after fixing the problem.\n\n"
										+ summary.getText());
						resultTabs.setSelectedIndex(0);
					}
					status.setText(
							"File transfer operation failed; inspect Details and any retained result before retrying.");
					statusPanel.setDetails(status.getText() + "\n" + EditorStatusPanel.failureDetails(cause));
				} finally {
					setBusy(false);
				}
			}
		}.execute();
	}

	private void show(Object value) {
		show(value, null);
	}

	private void show(Object value, FileTransferDesktopService.Reviewed prior) {
		schemaDraft.clear();
		result.showJson(value);
		warningTabs.show(resultTabs, value, prior);
		tableTabs.show(resultTabs, value);
		statementTabs.show(resultTabs, value);
		verificationTabs.show(resultTabs, value, prior);
		progressTabs.show(resultTabs, value, prior);
		packageTabs.show(resultTabs, value);
		var summaryText = new StringBuilder(FileTransferSummary.text(value));
		if (value instanceof TransferFileImportService.Outcome outcome
				&& TransferRecordedTargets.matches(outcome, prior) && prior.manifest() != null
				&& !prior.manifest().warnings().isEmpty())
			summaryText.append("\nRetained package export warnings: ").append(prior.manifest().warnings().size())
					.append(". See Warnings alongside the recorded import warnings.\n");
		if (progressTabs.panel != null)
			TransferExecutionSummary.appendProgressGuide(summaryText);
		summary.setText(summaryText.toString());
		summary.setCaretPosition(0);
		hasSummary = true;
		resultTabs.remove(schemaSql);
		final var sql = FileTransferSummary.schemaSql(value, prior, sourceFiles);
		if (sql != null) {
			schemaSql.showText(sql.display());
			if (sql.protectedInputs() != null)
				schemaDraft.show(sql.draft(), sql.protectedInputs());
			resultTabs.addTab("Schema SQL", schemaSql);
		}
		resultTabs.setSelectedIndex(0);
	}

	private void clearGradle() {
		schemaDraft.clear();
		warningTabs.clear(resultTabs);
		tableTabs.clear(resultTabs);
		statementTabs.clear(resultTabs);
		verificationTabs.clear(resultTabs);
		progressTabs.clear(resultTabs);
		packageTabs.clear(resultTabs);
		resultTabs.remove(gradle);
		gradle.showText("");
	}

	void useFileSource(JTextField input) {
		sourceFileInput = java.util.Objects.requireNonNull(input);
		source.useFileSource();
		final var row = sourceSchema.getParent();
		if (row != null && row.getParent() != null)
			row.getParent().remove(row);
	}

	void setSourceFiles(List<Path> files) {
		sourceFiles = List.copyOf(files);
	}

	private void showGradle(Path exportJob, Path manifest, String fingerprint) {
		gradle.showText(FileTransferGradleGuide.text(exportJob, manifest, fingerprint, source.profile.settingsFile(),
				target.profile.settingsFile()));
		resultTabs.addTab("Gradle", gradle);
	}

	void setSqlEditor(java.util.function.Predicate<Path> receiver) {
		schemaDraft.setEditor(receiver);
	}
}
