/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaChangeChecks;

/**
 * The common direct job workflow; adapters delegate all migration rules to
 * shared services.
 */
final class DirectTransferPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	enum Action {
		DISCOVER, REVIEW, EXECUTE, TEST_SOURCE, TEST_TARGET
	}

	record Request(Path job, Path workspace, PlanningJdbc.Spec source, PlanningJdbc.Spec target, String schema,
			DirectTransferDesktopService.Reviewed reviewed) {
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
	final JButton create = new JButton("New direct job…");
	final JButton review = new JButton("Review transfer");
	final JButton execute = new JButton("Execute reviewed transfer…");
	final JButton jobSettings = new JButton("Job settings…");
	final JToggleButton drivers = new JToggleButton("Driver class");
	final JLabel status = new JLabel(
			"Create a shared job from tables, or choose a saved job. Review never copies rows.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	final ReadOnlyCodePanel result = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON, false));
	final JTextArea summary = new JTextArea(
			"1. Enter source and target connections.\n2. Create a direct job and choose tables, or open a saved job.\n3. Review the transfer.\n4. Confirm execution when ready.");
	final JTabbedPane results = new JTabbedPane();
	private final TransferWarningTabs warningTabs = new TransferWarningTabs();
	final ReadOnlyCodePanel schemaSql = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL, false));
	final ReadOnlyCodePanel gradle = new ReadOnlyCodePanel(
			CodeEditors.create(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_GROOVY, false));
	private final JPanel form = new JPanel(new BorderLayout(4, 4));
	private final Backend backend;
	private final java.util.function.Function<DataMigrationDraftService.Prepared, Path> draftDialog;
	private final EditorFileChooser files = new EditorFileChooser();
	private DirectTransferDesktopService.Reviewed reviewed;
	private long revision;
	private boolean busy;

	DirectTransferPanel() {
		this(new DirectTransferDesktopService(), null);
	}

	DirectTransferPanel(Backend backend,
			java.util.function.Function<DataMigrationDraftService.Prepared, Path> presenter) {
		super(new BorderLayout(8, 8));
		this.backend = backend;
		draftDialog = presenter == null ? prepared -> DirectTransferDraftDialog.show(this, prepared) : presenter;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var settings = new JPanel(new GridLayout(0, 1, 4, 4));
		settings.add(new JLabel(
				"One job for GUI and Gradle. New direct job offers data transfer or schema only (no row copy)."));
		settings.add(row("Shared job (optional for New job)", job, false));
		settings.add(row("Working folder and reports", workspace, true));
		settings.add(row("Source schema for New job (blank: current)", sourceSchema, null));
		form.add(settings, BorderLayout.NORTH);
		final var endpoints = new JPanel(new GridLayout(1, 2, 8, 8));
		endpoints.add(source);
		endpoints.add(target);
		form.add(endpoints, BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(create);
		actions.add(jobSettings);
		actions.add(review);
		actions.add(execute);
		actions.add(drivers);
		form.add(actions, BorderLayout.SOUTH);
		summary.setEditable(false);
		summary.setLineWrap(true);
		summary.setWrapStyleWord(true);
		summary.setMargin(new Insets(8, 8, 8, 8));
		results.addTab("Summary", new JScrollPane(summary));
		results.addTab("Details", result);
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
						: "Copy into the reviewed target?\nConfiguration: ")
				+ reviewed.review().prepared().job().fingerprint() + "\nCompatibility warnings: "
				+ reviewed.review().prepared().warnings().size() + "\nDDL statements: "
				+ reviewed.review().prepared().schemaPreparation().statements().size()
				+ "\nCreated objects and committed chunks may remain after failure. Inspect the review and Schema SQL before confirming.",
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
		warningTabs.clear(results);
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
		enable(form, !value);
		refresh();
	}

	private static void enable(Container parent, boolean value) {
		for (var child : parent.getComponents()) {
			child.setEnabled(value);
			if (child instanceof Container container)
				enable(container, value);
		}
	}

	private void refresh() {
		create.setEnabled(!busy);
		jobSettings.setEnabled(!busy);
		review.setEnabled(!busy);
		execute.setEnabled(!busy && reviewed != null
				&& !TransferSchemaChangeChecks.executionBlocked(reviewed.review().prepared().job().configuration(),
						reviewed.review().prepared().schemaPreparation()));
		execute.setToolTipText(reviewed != null && TransferSchemaChangeChecks.executionBlocked(
				reviewed.review().prepared().job().configuration(), reviewed.review().prepared().schemaPreparation())
						? "Inspect Schema SQL; enable data-impacting changes in Job settings and review again."
						: null);
		drivers.setEnabled(!busy);
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
			status.setText("Choose a saved shared job before opening settings.");
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
			status.setText("Enter the " + (fields == source ? "source" : "target") + " JDBC URL first.");
			fields.jdbcUrl.requestFocusInWindow();
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
		if (isBusy() || action == Action.EXECUTE && reviewed == null)
			return;
		final boolean needSource = action != Action.TEST_TARGET;
		final boolean needTarget = action != Action.DISCOVER && action != Action.TEST_SOURCE;
		if (needSource && !valid(source) || needTarget && !valid(target))
			return;
		final boolean testing = action == Action.TEST_SOURCE || action == Action.TEST_TARGET;
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
			status.setText("Choose a shared job and an existing writable working folder first.");
			return;
		}
		final var request = new Request(input, folder, needSource ? source.snapshot().spec() : null,
				needTarget ? target.snapshot().spec() : null, sourceSchema.getText(), reviewed);
		final char[] from = source.password.getPassword(), to = target.password.getPassword();
		final long requested = revision;
		if (action == Action.EXECUTE) {
			source.password.setText("");
			target.password.setText("");
		}
		if (!testing) {
			warningTabs.clear(results);
			reviewed = null;
			results.remove(gradle);
			gradle.showText("");
		}
		setBusy(true);
		status.setText(action == Action.EXECUTE ? request.reviewed().review().prepared().job().configuration()
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
							status.setText(
									"Shared direct job saved. Choose Review transfer; no rows have been copied.");
						} else
							status.setText("Job creation cancelled or inputs changed. No rows copied.");
					} else if (action == Action.REVIEW) {
						reviewed = (DirectTransferDesktopService.Reviewed) value;
						show(value);
						status.setText("Transfer reviewed. Compatibility warnings: "
								+ reviewed.review().prepared().warnings().size()
								+ ". Inspect the review before execution.");
					} else if (action == Action.EXECUTE) {
						show(value);
						status.setText(((DirectTransferDesktopService.Executed) value).outcome().prepared().job()
								.configuration().dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY
										? "Schema-only transfer finished. Inspect DDL results, warnings and the saved audit. No rows were copied."
										: "Direct transfer finished. Inspect verification, warnings and the saved audit before another run.");
					} else
						status.setText("Connection check: " + value);
				} catch (Exception failure) {
					warningTabs.clear(results);
					final var cause = failure instanceof java.util.concurrent.ExecutionException ? failure.getCause()
							: failure;
					reviewed = null;
					if (cause instanceof DirectTransferDesktopService.ExecutionFailure execution) {
						show(execution.result);
						summary.setText("Action failed: " + execution.getMessage() + "\n\n" + summary.getText());
					} else {
						result.showText("");
						results.remove(schemaSql);
						schemaSql.showText("");
						results.remove(gradle);
						gradle.showText("");
						if (cause instanceof TransferActionFailure diagnostic)
							summary.setText(diagnostic.summary());
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

	private void show(Object value) {
		result.showJson(value);
		warningTabs.show(results, value);
		results.remove(gradle);
		gradle.showText("");
		final var text = new StringBuilder();
		if (value instanceof DirectTransferDesktopService.Reviewed reviewed) {
			final var review = reviewed.review();
			final var config = review.prepared().job().configuration();
			if (config.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY)
				text.append(config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
						? "SCHEMA ONLY: apply selected table differences using reviewed CREATE/ALTER/DROP SQL. No source rows are transferred or verified.\n"
						: "SCHEMA ONLY: create missing tables, constraints and indexes. No row copy or row verification.\n")
						.append(config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
								? "Existing selected tables may change. Unselected tables are outside the migration scope. The target schema must already exist.\n\n"
								: "Existing target tables are skipped without comparison or modification. The target schema must already exist.\n\n");
			if (TransferSchemaChangeChecks.executionBlocked(config, review.prepared().schemaPreparation()))
				text.append(
						"EXECUTION BLOCKED: data-impacting changes require explicit permission in Job settings and a new review. SQL remains available for inspection.\n\n");
			if (config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES)
				text.append("Data-impacting Schema changes allowed by saved job: ")
						.append(config.allowDestructiveSchemaChanges()).append("\n\n");
			if (TransferSchemaChangeChecks.unresolvedCount(review.prepared().schemaPreparation()) > 0)
				text.append("Unresolved table-change warnings: ")
						.append(TransferSchemaChangeChecks.unresolvedCount(review.prepared().schemaPreparation()))
						.append(". These changes still require manual work; see Warnings.\n\n");
			text.append("Transfer reviewed. No rows have been copied. Execution requires your confirmation.\n\n");
			TransferGeneratedKeyNotice.append(text, review.prepared().warnings());
			text.append("Source: ").append(review.sourceIdentity().productName()).append(" / ")
					.append(review.sourceIdentity().productVersion()).append('\n').append("Target: ")
					.append(review.targetIdentity().productName()).append(" / ")
					.append(review.targetIdentity().productVersion()).append('\n').append("Selected tables (")
					.append(config.tables().size()).append("):\n");
			for (String table : config.tables())
				text.append("  ").append(table).append('\n');
			if (config.dataMode() != TransferJobConfiguration.DataMode.SCHEMA_ONLY)
				text.append("\nChunk rows: ").append(config.chunkSize()).append("; verification: ")
						.append(config.verify() ? "enabled" : "disabled").append('\n');
			text.append("Target preparation: ").append(config.schemaMode())
					.append(config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
							? "; Schema SQL statements: "
							: "; DDL statements: ")
					.append(review.prepared().schemaPreparation().statements().size()).append('\n');
			warnings(text, review.prepared().warnings());
			text.append("\nReview report: ").append(reviewed.report());
			text.append("\nRun this saved job from Gradle: see the Gradle tab for setup and commands.");
			gradle.showText(DirectTransferGradleGuide.text(review.prepared().job()));
			results.addTab("Gradle", gradle);
		} else if (value instanceof DirectTransferDesktopService.Executed execution) {
			final var outcome = execution.outcome();
			final boolean schemaOnly = outcome.prepared().job().configuration()
					.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY;
			if (schemaOnly)
				text.append(outcome.prepared().job().configuration()
						.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
								? "SCHEMA ONLY: no source rows were transferred. Reviewed Schema SQL may change target data when explicitly allowed.\n"
								: "SCHEMA ONLY: no data-copy or row-verification operations were requested.\n");
			if (schemaOnly && outcome.phase() == TransferJobExecutionService.Phase.COMPLETE)
				text.append(TransferSchemaChangeChecks.unresolvedCount(outcome.prepared().schemaPreparation()) > 0
						? "Generated Schema SQL completed, but unresolved table changes remain. See Warnings before using the target.\n"
						: "Schema-only transfer completed. Inspect DDL results and compatibility warnings below.\n");
			else
				TransferExecutionSummary.append(text, outcome.phase());
			text.append("Execution phase: ").append(outcome.phase()).append('\n');
			TransferGeneratedKeyNotice.append(text, outcome.prepared().warnings());
			if (schemaOnly)
				text.append("Next: inspect DDL results and warnings; test target definitions before loading data.\n");
			else
				TransferNextSteps.append(text, outcome.phase(), outcome.prepared().job().configuration().verify(),
						outcome.prepared().warnings());
			if (schemaOnly)
				text.append("Row copy: not requested. Row verification: not applicable (schema only).\n");
			else
				text.append("Completed tables: ").append(outcome.completedTasks().size()).append('\n')
						.append("Processed rows: ")
						.append(schemaOnly ? "not applicable (schema only)"
								: outcome.copyResult() == null ? "see retained committed progress in Details"
										: outcome.copyResult().getProcessedRows())
						.append('\n').append("Verification: ")
						.append(schemaOnly ? "not applicable (schema only)"
								: !outcome.prepared().job().configuration().verify() ? "disabled"
										: outcome.verificationResult() == null ? "not completed"
												: outcome.verificationResult().isMatch() ? "matched" : "mismatch")
						.append('\n');
			warnings(text, outcome.prepared().warnings());
			text.append("\nResult report: ").append(execution.report() == null ? "unavailable" : execution.report());
			text.append(outcome.prepared().job().configuration()
					.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
							? "\nSchema SQL statements completed: "
							: "\nDDL statements completed: ")
					.append(outcome.schemaPreparation().completedStatements()).append(" / ")
					.append(outcome.schemaPreparation().planned().size());
			if (outcome.schemaPreparation().attemptedStatement() != null)
				text.append("; uncertain attempted statement: ")
						.append(outcome.schemaPreparation().attemptedStatement());
			if (outcome.phase() != TransferJobExecutionService.Phase.COMPLETE)
				text.append("\nCommitted chunks may remain. Inspect Details before retrying.");
		}
		results.remove(schemaSql);
		final var statements = value instanceof DirectTransferDesktopService.Reviewed review
				? review.review().prepared().schemaPreparation().statements()
				: ((DirectTransferDesktopService.Executed) value).outcome().schemaPreparation().planned();
		if (!statements.isEmpty()) {
			final var sql = new StringBuilder(
					"-- Reviewed target preparation. Created objects may remain after failure.\n\n");
			for (int index = 0; index < statements.size(); index++)
				sql.append("-- Statement ").append(index + 1).append(" / ").append(statements.get(index).taskId())
						.append('\n').append(statements.get(index).sql()).append("\n\n");
			schemaSql.showText(sql.toString());
			results.addTab("Schema SQL", schemaSql);
		}
		summary.setText(text.toString());
		summary.setCaretPosition(0);
		results.setSelectedIndex(0);
	}

	private static void warnings(StringBuilder text, List<String> warnings) {
		text.append("\nCompatibility warnings: ").append(warnings.size()).append('\n');
		for (String warning : warnings)
			text.append("  ").append(warning).append('\n');
	}
}
