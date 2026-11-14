/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO;

import java.awt.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaChangeChecks;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement;
import com.sqlapp.data.db.command.migration.transfer.TransferStagingComparisonReportIO;

/**
 * The common direct job workflow; adapters delegate all migration rules to
 * shared services.
 */
final class DirectTransferPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	enum Action {
		DISCOVER, REVIEW, EXECUTE, TEST_SOURCE, TEST_TARGET, COMPARE_STAGING
	}

	record Request(Path job, Path workspace, PlanningJdbc.Spec source, PlanningJdbc.Spec target, String schema,
			DirectTransferDesktopService.Reviewed reviewed, DirectTransferDesktopService.Recovery recovery,
			Path savedResult) {
		Request(Path job, Path workspace, PlanningJdbc.Spec source, PlanningJdbc.Spec target, String schema,
				DirectTransferDesktopService.Reviewed reviewed) {
			this(job, workspace, source, target, schema, reviewed, null, null);
		}
	}

	@FunctionalInterface
	interface Backend {
		Object perform(Action action, Request request, char[] sourcePassword, char[] targetPassword) throws Exception;
	}

	final ConnectionFields source = new ConnectionFields("Source database");
	final ConnectionFields target = new ConnectionFields("Target database");
	final JTextField job = new JTextField();
	final JTextField workspace = new JTextField();
	final JTextField sourceSchema = new JTextField();
	final JButton create = new JButton("New job…");
	final JButton review = new JButton("Review transfer");
	final JButton execute = new JButton("Execute reviewed transfer…");
	final JButton compareStaging = new JButton("Compare retained staging");
	final JButton compareSavedStaging = new JButton("Compare saved staging…");
	final JButton jobSettings = new JButton("Job settings…");
	final JToggleButton drivers = new JToggleButton("Show driver class");
	final JLabel status = new JLabel("Create a job from tables, or choose a saved job. Review never copies rows.");
	private final StagingComparisonSaveActions comparisonActions = new StagingComparisonSaveActions(this::isBusy,
			this::setBusy, status::setText, workspace::getText);
	final JButton saveComparison = comparisonActions.save;
	final JButton compareAgain = comparisonActions.repeat;
	final SavedReportButton savedComparison = comparisonActions.saved;
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	final ReadOnlyCodePanel result = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON, false));
	final JTextArea summary = new JTextArea(
			"1. Enter source and target connections.\n2. Create a direct job and choose tables, or open a saved job.\n3. Review the transfer.\n4. Confirm execution when ready.");
	final JTabbedPane results = new JTabbedPane();
	private final TransferWarningTabs warningTabs = new TransferWarningTabs();
	private final TransferTablePreparationTabs tableTabs = new TransferTablePreparationTabs();
	private final TransferSchemaStatementTabs statementTabs = new TransferSchemaStatementTabs();
	private final TransferVerificationTabs verificationTabs = new TransferVerificationTabs();
	private final TransferExecutionProgressTabs progressTabs = new TransferExecutionProgressTabs();
	final ReadOnlyCodePanel schemaSql = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL, false));
	final GeneratedSqlDraftActions schemaDraft = new GeneratedSqlDraftActions(schemaSql, this::isBusy, this::setBusy,
			status::setText,
			failure -> statusPanel.setDetails("SQL draft save failed.\n" + EditorStatusPanel.failureDetails(failure)));
	final ReadOnlyCodePanel gradle = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_GROOVY, false));
	private final JPanel form = new JPanel(new BorderLayout(4, 4));
	private final Backend backend;
	private final java.util.function.Function<DataMigrationDraftService.Prepared, Path> draftDialog;
	private final EditorFileChooser files = new EditorFileChooser();
	private DirectTransferDesktopService.Reviewed reviewed;
	private DirectTransferDesktopService.Recovery recovery;
	final ReportReviewView comparisonTab = new ReportReviewView();
	private final StagingComparisonHistory comparisonHistory = new StagingComparisonHistory();
	final JTextArea comparisonText = comparisonTab.summary;
	private long revision;
	private boolean busy;
	private List<Path> sourceFiles = List.of();
	private JTextField sourceFileInput;

	DirectTransferPanel() {
		this(new DirectTransferDesktopService(), null);
	}

	DirectTransferPanel(Backend backend,
			java.util.function.Function<DataMigrationDraftService.Prepared, Path> presenter) {
		super(new BorderLayout(8, 8));
		this.backend = backend;
		tableTabs.setStatementNavigator(task -> statementTabs.showTask(results, task));
		progressTabs.setNavigators(task -> statementTabs.showTask(results, task),
				task -> verificationTabs.showTask(results, task));
		draftDialog = presenter == null
				? prepared -> DirectTransferDraftDialog.show(this, prepared,
						ConnectionCatalog.jobNames(source.connectionName(), target.connectionName()))
				: presenter;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		// Numbered steps in the order the Summary describes them.
		final var connect = new StepSection("1. Connect source and target");
		final var endpoints = new JPanel(new GridLayout(1, 2, 8, 8));
		endpoints.add(source);
		endpoints.add(target);
		connect.add(endpoints, BorderLayout.CENTER);
		final var connectActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		connectActions.add(drivers);
		connect.add(connectActions, BorderLayout.SOUTH);
		final var jobStep = new StepSection("2. Create or choose a job");
		final var settings = new JPanel(new GridLayout(0, 1, 4, 4));
		settings.add(
				new JLabel("One job for GUI and Gradle. New job offers data transfer or schema only (no row copy)."));
		settings.add(row("Saved job",
				FieldHints.placeholder(job, "Optional when creating a New job; choose a saved job to reuse it"),
				false));
		settings.add(row("Working folder (required)",
				FieldHints.placeholder(workspace, "Existing writable folder for the job, reviews and reports"), true));
		settings.add(row("Source schema", FieldHints.placeholder(sourceSchema,
				"Used by New job; blank uses the source connection's current schema"), null));
		jobStep.add(settings, BorderLayout.CENTER);
		final var jobActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		jobActions.add(create);
		jobActions.add(jobSettings);
		jobStep.add(jobActions, BorderLayout.SOUTH);
		final var runStep = new StepSection("3. Review and execute");
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		actions.add(review);
		actions.add(execute);
		actions.add(compareStaging);
		actions.add(compareSavedStaging);
		runStep.add(actions, BorderLayout.CENTER);
		final var steps = new JPanel();
		steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
		steps.add(connect);
		steps.add(jobStep);
		steps.add(runStep);
		form.add(steps, BorderLayout.NORTH);
		summary.setEditable(false);
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		summary.setMargin(new Insets(8, 8, 8, 8));
		results.addTab("Summary", new JScrollPane(summary));
		results.addTab("Details", result);
		comparisonTab.add(comparisonActions, BorderLayout.SOUTH);
		compareStaging.setToolTipText(
				"Read the current target and retained staging. Re-enter the target password if needed.");
		compareStaging.addActionListener(event -> run(Action.COMPARE_STAGING));
		compareSavedStaging.setToolTipText(
				"Choose or drop transfer-result.json or import-result.json from an earlier unconfirmed commit. Uses the target connection above.");
		compareSavedStaging.addActionListener(event -> {
			if (isBusy() || !valid(target))
				return;
			var chooser = files.forOutputDirectory(
					new javax.swing.filechooser.FileNameExtensionFilter("Transfer result JSON", "json"),
					"transfer-result.json", workspace.getText());
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				compareSaved(chooser.getSelectedFile().toPath());
		});
		FileDropHandler.file(compareSavedStaging, List.of("json"), () -> !isBusy(), path -> {
			if (!valid(target))
				return false;
			compareSaved(path);
			return true;
		}, FileDropHandler.errors(status, statusPanel));
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(form), results);
		split.setResizeWeight(0.55);
		add(split, BorderLayout.CENTER);
		add(statusPanel, BorderLayout.SOUTH);
		create.addActionListener(event -> {
			if (isBusy() || !valid(source))
				return;
			if (workspace.getText().isBlank()) {
				final var chooser = files.forDirectory("");
				chooser.setDialogTitle("Choose a folder for transfer jobs and reports");
				if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
					return;
				workspace.setText(chooser.getSelectedFile().getAbsolutePath());
			}
			run(Action.DISCOVER);
		});
		review.addActionListener(event -> run(Action.REVIEW));
		jobSettings.addActionListener(event -> editSettings(path -> TransferJobSettingsDialog.show(this, path,
				com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.DataMode.JDBC_DIRECT)));
		execute.addActionListener(event -> executeReviewed(() -> JOptionPane.showConfirmDialog(this, (reviewed.review()
				.prepared().job().configuration().dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY
						? reviewed.review().prepared().job().configuration()
								.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
										? "Apply reviewed Schema SQL, including ALTER/DROP? Target data may change if explicitly allowed. No source rows are transferred.\nConfiguration: "
										: "Create the reviewed target tables without copying rows?\nConfiguration: "
						: reviewed.review().staging() != null
								? "Replace all rows in the reviewed target tables after staging and verification?\nConfiguration: "
								: "Copy into the reviewed target?\nConfiguration: ")
				+ reviewed.review().prepared().job().fingerprint() + "\nCompatibility warnings: "
				+ reviewed.review().prepared().warnings().size() + "\nDDL statements: "
				+ reviewed.review().prepared().schemaPreparation().statements().size()
				+ (reviewed.review().staging() != null
						? "\nFinal replacement uses one transaction. Staging tables and newly created target tables can remain after a failure. Inspect Summary and Schema SQL before confirming."
						: "\nCreated objects and committed chunks may remain after failure. Inspect the review and Schema SQL before confirming."),
				"Confirm direct transfer", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION));
		drivers.addActionListener(event -> {
			source.showDriver(drivers.isSelected());
			target.showDriver(drivers.isSelected());
			revalidate();
		});
		for (var fields : new ConnectionFields[] { source, target }) {
			fields.test.addActionListener(event -> run(fields == source ? Action.TEST_SOURCE : Action.TEST_TARGET));
			fields.buildUrl.addActionListener(event -> {
				if (isBusy())
					return;
				setBusy(true);
				try {
					final var url = JdbcUrlBuilderPanel.showDialog(this, fields.jdbcUrl.getText(),
							fields == source ? "Source database" : "Target database");
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
		listen(workspace);
		listen(sourceSchema);
		DisabledReason.install(execute, () -> busy ? "Wait for the current operation to finish."
				: reviewed == null ? "Choose Review transfer first. Only the current review can be executed." : null);
		refresh();
	}

	private JPanel row(String text, JTextField field, Boolean directory) {
		final var panel = new JPanel(new BorderLayout(6, 0));
		final var label = new JLabel(text);
		label.setLabelFor(field);
		panel.add(label, BorderLayout.WEST);
		panel.add(field, BorderLayout.CENTER);
		if (directory != null) {
			FileDropHandler.path(field, directory, directory ? List.of() : List.of("yaml", "yml"), () -> !isBusy(),
					FileDropHandler.errors(status, statusPanel));
			final var browse = new JButton("Browse…");
			browse.addActionListener(event -> {
				if (isBusy())
					return;
				final var chooser = directory ? files.forDirectory(field.getText())
						: files.forFile(new javax.swing.filechooser.FileNameExtensionFilter("Shared transfer YAML",
								"yaml", "yml"), field.getText());
				if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
					field.setText(chooser.getSelectedFile().getAbsolutePath());
			});
			panel.add(browse, BorderLayout.EAST);
		}
		return panel;
	}

	private void listen(JTextField field) {
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				changed();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				changed();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				changed();
			}
		});
	}

	private void changed() {
		revision++;
		clearRecovery();
		schemaDraft.clear();
		warningTabs.clear(results);
		tableTabs.clear(results);
		statementTabs.clear(results);
		verificationTabs.clear(results);
		progressTabs.clear(results);
		results.remove(schemaSql);
		schemaSql.showText("");
		results.remove(gradle);
		gradle.showText("");
		if (reviewed != null)
			summary.setText("Inputs changed. This review is no longer current.\n\n" + summary.getText());
		reviewed = null;
		if (!busy)
			status.setText("Inputs changed. Review the transfer again before execution.");
		refresh();
	}

	boolean isBusy() {
		return busy || source.profile.isBusy() || target.profile.isBusy();
	}

	boolean selectDriverClass(String name, boolean from) {
		if (isBusy())
			return false;
		(from ? source : target).driverClass.setText(name);
		drivers.setSelected(true);
		source.showDriver(true);
		target.showDriver(true);
		revalidate();
		return true;
	}

	private void setBusy(boolean value) {
		busy = value;
		ComponentEnablement.setTreeEnabled(form, !value);
		refresh();
	}

	private void refresh() {
		schemaDraft.refresh();
		create.setEnabled(!busy);
		jobSettings.setEnabled(!busy);
		review.setEnabled(!busy);
		compareStaging.setVisible(recovery != null);
		compareStaging.setEnabled(!busy && recovery != null);
		compareSavedStaging.setEnabled(!busy);
		comparisonActions.refresh();
		execute.setEnabled(!busy && reviewed != null
				&& !TransferSchemaChangeChecks.executionBlocked(reviewed.review().prepared().job().configuration(),
						reviewed.review().prepared().schemaPreparation()));
		execute.setToolTipText(reviewed != null && TransferSchemaChangeChecks.executionBlocked(
				reviewed.review().prepared().job().configuration(), reviewed.review().prepared().schemaPreparation())
						? "Inspect Schema SQL; enable data-impacting changes in Job settings and review again."
						: null);
		drivers.setEnabled(!busy);
		PrimaryAction.highlight(execute.isEnabled() ? execute : !job.getText().isBlank() ? review : create, create,
				review, execute);
	}

	void editSettings(
			java.util.function.Function<Path, com.sqlapp.data.db.command.migration.transfer.TransferJobConfigurationIO.Snapshot> presenter) {
		if (isBusy())
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
		final long requested = revision;
		setBusy(true);
		try {
			final var saved = presenter.apply(file);
			if (saved != null) {
				final boolean current = requested == revision;
				changed();
				status.setText(current ? "Job settings saved. Review again before execution."
						: "Settings saved for the previous selection. Review the selected job again.");
			}
		} finally {
			setBusy(false);
		}
	}

	private boolean valid(ConnectionFields fields) {
		try {
			fields.snapshot().spec();
			return true;
		} catch (IllegalArgumentException failure) {
			if (fields == source && sourceFileInput != null) {
				status.setText("Choose an existing readable source database file first.");
				sourceFileInput.requestFocusInWindow();
			} else {
				status.setText("Enter the " + (fields == source ? "source" : "target") + " JDBC URL first.");
				fields.jdbcUrl.requestFocusInWindow();
			}
			return false;
		}
	}

	void discover() {
		run(Action.DISCOVER);
	}

	void executeReviewed(BooleanSupplier confirmation) {
		if (isBusy() || reviewed == null)
			return;
		if (TransferSchemaChangeChecks.executionBlocked(reviewed.review().prepared().job().configuration(),
				reviewed.review().prepared().schemaPreparation())) {
			status.setText(
					"Execution blocked: inspect Schema SQL, then explicitly allow data-impacting changes in Job settings and review again.");
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
			status.setText("Transfer cancelled. No rows copied.");
			return;
		}
		if (snapshot != reviewed || requested != revision) {
			status.setText("Inputs changed. Review and confirm again.");
			return;
		}
		run(Action.EXECUTE);
	}

	private void run(Action action) {
		run(action, null);
	}

	private void compareSaved(Path resultReport) {
		run(Action.COMPARE_STAGING, java.util.Objects.requireNonNull(resultReport));
	}

	private void run(Action action, Path savedResult) {
		final boolean comparing = action == Action.COMPARE_STAGING;
		if (isBusy() || action == Action.EXECUTE && reviewed == null
				|| comparing && recovery == null && savedResult == null)
			return;
		final boolean needSource = action != Action.TEST_TARGET && !comparing;
		final boolean needTarget = action != Action.DISCOVER && action != Action.TEST_SOURCE;
		if (needSource && !valid(source) || needTarget && !valid(target))
			return;
		final boolean testing = action == Action.TEST_SOURCE || action == Action.TEST_TARGET || comparing;
		final Path folder;
		final Path input;
		try {
			folder = testing ? null : Path.of(workspace.getText());
			if (!testing && (workspace.getText().isBlank() || !Files.isDirectory(folder) || !Files.isWritable(folder)))
				throw new IllegalArgumentException();
			input = action == Action.DISCOVER || testing ? null : Path.of(job.getText());
			if (input != null && job.getText().isBlank())
				throw new IllegalArgumentException();
		} catch (RuntimeException failure) {
			status.setText("Choose a saved job and an existing writable working folder first.");
			return;
		}
		final var request = new Request(input, folder, needSource ? source.snapshot().spec() : null,
				needTarget ? target.snapshot().spec() : null, sourceSchema.getText(), reviewed, recovery, savedResult);
		final char[] from = needSource ? source.password.getPassword() : new char[0],
				to = target.password.getPassword();
		final long requested = revision;
		if (comparing) {
			comparisonActions.beginComparison();
			comparisonActions.setRepeatAction(() -> run(Action.COMPARE_STAGING, savedResult));
		}
		if (action == Action.EXECUTE) {
			source.password.setText("");
			target.password.setText("");
		}
		if (!testing) {
			clearRecovery();
			schemaDraft.clear();
			warningTabs.clear(results);
			tableTabs.clear(results);
			statementTabs.clear(results);
			verificationTabs.clear(results);
			progressTabs.clear(results);
			reviewed = null;
			results.remove(gradle);
			gradle.showText("");
		}
		setBusy(true);
		status.setText(comparing ? "Comparing staging and current target rows (read-only)…"
				: action == Action.EXECUTE ? request.reviewed().review().prepared().job().configuration()
						.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY
								? "Applying reviewed Schema SQL; completed statements may remain on failure. No source rows are transferred."
								: "Copying and verifying; committed chunks may remain on failure."
						: "Working: " + action + ". This action does not copy rows.");
		new SwingWorker<Object, Void>() {
			@Override
			protected Object doInBackground() throws Exception {
				try {
					return backend.perform(action, request, from, to);
				} finally {
					Arrays.fill(from, '\0');
					Arrays.fill(to, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					final var value = get();
					if (action != Action.EXECUTE && requested != revision) {
						status.setText("Inputs changed. Start the action again.");
						return;
					}
					if (action == Action.DISCOVER) {
						final var saved = draftDialog.apply((DataMigrationDraftService.Prepared) value);
						if (saved != null && requested == revision) {
							job.setText(saved.toString());
							status.setText("Job saved. Choose Review transfer; no rows have been copied.");
						} else
							status.setText("Job creation cancelled or inputs changed. No rows copied.");
					} else if (action == Action.REVIEW) {
						reviewed = (DirectTransferDesktopService.Reviewed) value;
						show(value, request.job());
						status.setText("Transfer reviewed. Compatibility warnings: "
								+ reviewed.review().prepared().warnings().size()
								+ ". Inspect the review before execution.");
					} else if (action == Action.EXECUTE) {
						show(value, request.job());
						status.setText(((DirectTransferDesktopService.Executed) value).outcome().prepared().job()
								.configuration().dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY
										? "Schema-only transfer finished. Inspect DDL results, warnings and the saved audit. No rows were copied."
										: "Direct transfer finished. Inspect verification, warnings and the saved audit before another run.");
					} else if (comparing) {
						if (value instanceof TransferStagingComparisonReportIO.Report report) {
							comparisonActions.show(report);
							comparisonTab.showReview(comparisonHistory.show(report,
									new ReportReviewService().stagingComparison(report)));
							selectComparison();
						} else {
							showComparison((TransferStagedReplacement.Comparison) value);
						}
						status.setText(
								"Read-only staging comparison finished. The recorded commit outcome is unchanged.");
					} else
						status.setText("Connection check: " + value);
				} catch (Exception failure) {
					final var cause = failure instanceof java.util.concurrent.ExecutionException ? failure.getCause()
							: failure;
					if (comparing) {
						if (requested == revision) {
							var detail = cause instanceof TransferPreparationFailure ? cause.getMessage()
									: "Check the reviewed target connection and retained staging tables.";
							showComparisonText("Staging comparison failed.\n\n" + detail);
							status.setText("Staging comparison failed. The recorded transfer result is retained.");
						} else {
							// changed() leaves the status alone while busy; do not keep showing
							// "Comparing…".
							status.setText("Inputs changed during the staging comparison. Start the comparison again.");
						}
						return;
					}
					schemaDraft.clear();
					warningTabs.clear(results);
					tableTabs.clear(results);
					statementTabs.clear(results);
					verificationTabs.clear(results);
					progressTabs.clear(results);
					reviewed = null;
					if (cause instanceof DirectTransferDesktopService.ExecutionFailure execution) {
						if (action == Action.EXECUTE && requested == revision
								&& execution.result.outcome().staging() != null && execution.result.outcome().staging()
										.state() == TransferStagedReplacement.State.COMMIT_UNCERTAIN)
							recovery = new DirectTransferDesktopService.Recovery(request.reviewed().review(),
									execution.result.outcome(), execution.result.resultFingerprint());
						show(execution.result, request.job());
						summary.setText("Action failed: " + execution.getMessage() + "\n\n" + summary.getText());
					} else {
						result.showText("");
						results.remove(schemaSql);
						schemaSql.showText("");
						results.remove(gradle);
						gradle.showText("");
						if (cause instanceof TransferActionFailure diagnostic)
							summary.setText(diagnostic.summary());
						else if (cause instanceof LinkageError linkage)
							summary.setText(TransferActionFailure.linkage(action.toString(), linkage).summary());
						else if (cause instanceof TransferPreparationFailure validation)
							summary.setText("Target preparation needs attention:\n\n" + validation.getMessage()
									+ "\n\nFix the target or job settings and review again.");
						else
							summary.setText(new TransferActionFailure(action.toString(),
									"The action could not complete.",
									"Test each connection, check the selected job and review again. No current review authorizes execution.",
									"Exception type: " + cause.getClass().getSimpleName()).summary());
						if (action != Action.EXECUTE)
							summary.append("\n\nThis action did not execute transfer DDL or copy rows.");
					}
					summary.setCaretPosition(0);
					results.setSelectedIndex(0);
					status.setText(
							"Direct transfer action failed. See Summary for the failure stage, reason and next checks.");
					statusPanel.setDetails(summary.getText());
				} finally {
					setBusy(false);
				}
			}
		}.execute();
	}

	private void clearRecovery() {
		recovery = null;
		comparisonActions.clear();
		comparisonHistory.clear();
		results.remove(comparisonTab);
		comparisonTab.clear();
	}

	void saveComparisonTo(Path folder) {
		comparisonActions.saveTo(folder);
	}

	private void showComparison(TransferStagedReplacement.Comparison comparison) {
		comparisonHistory.clear();
		comparisonTab.showReview(new ReportReviewService().stagingComparison(comparison));
		selectComparison();
	}

	private void showComparisonText(String text) {
		comparisonTab.clear();
		comparisonText.setText(text);
		comparisonText.setCaretPosition(0);
		selectComparison();
	}

	private void selectComparison() {
		if (results.indexOfComponent(comparisonTab) < 0)
			results.addTab("Staging comparison", comparisonTab);
		results.setSelectedComponent(comparisonTab);
	}

	private void show(Object value, Path definitionFile) {
		schemaDraft.clear();
		result.showJson(value);
		warningTabs.show(results, value);
		tableTabs.show(results, value);
		statementTabs.show(results, value);
		verificationTabs.show(results, value);
		progressTabs.show(results, value, null);
		results.remove(gradle);
		gradle.showText("");
		final String text = DirectTransferSummary.text(value, recovery != null, progressTabs.panel != null);
		if (value instanceof DirectTransferDesktopService.Reviewed reviewed) {
			final var saved = reviewed.review().prepared().job();
			gradle.showText(DirectTransferGradleGuide.text(
					new TransferJobConfigurationIO.Snapshot(reviewed.review().definitionFile(), saved.text(),
							saved.fingerprint(), saved.configuration()),
					source.profile.settingsFile(), target.profile.settingsFile()));
			results.addTab("Gradle", gradle);
		}
		results.remove(schemaSql);
		final var sql = DirectTransferSummary.schemaSql(value, definitionFile, sourceFiles);
		if (sql != null) {
			schemaSql.showText(sql.display());
			schemaDraft.show(sql.draft(), sql.protectedInputs());
			results.addTab("Schema SQL", schemaSql);
		}
		summary.setText(text);
		summary.setCaretPosition(0);
		results.setSelectedIndex(0);
	}

	void setSqlEditor(java.util.function.Predicate<Path> receiver) {
		schemaDraft.setEditor(receiver);
	}

	void setSourceFiles(List<Path> files) {
		sourceFiles = List.copyOf(files);
	}

	void useFileSource(JTextField input) {
		sourceFileInput = java.util.Objects.requireNonNull(input);
		source.useFileSource();
		final var row = sourceSchema.getParent();
		if (row != null && row.getParent() != null)
			row.getParent().remove(row);
	}
}
