/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Review and confirmed execution of a declarative data migration job. */
final class DataMigrationPlanPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField configurationFile = new JTextField();
	final JTextField expectedFingerprint = new JTextField();
	final ConnectionFields source = new ConnectionFields("Source database");
	final ConnectionFields target = new ConnectionFields("Target database");
	final JButton editJob = new JButton("Edit job…");
	final JButton newMigration = new JButton("New job…");
	final JTextField sourceSchema = new JTextField();
	final JButton chooseSourceSchema = new JButton("Choose schema…");
	final JTextField sourceSchemaSummary = new JTextField("Current schema (default)");
	final JButton build = new JButton("Review plan");
	final JToggleButton focusReview = new JToggleButton("Show results only");
	private final ReviewFocusControl reviewFocus;
	final JButton apply = new JButton("Execute reviewed job…");
	final JButton saveExecution = new JButton("Save last audit…");
	final JButton showExecution = new JButton("Last execution");
	final JButton showVerification = new JButton("Last verification");
	final JButton saveVerification = new JButton("Save verification…");
	final JButton pause = new JButton("Pause after chunk");
	final javax.swing.JProgressBar activity = new javax.swing.JProgressBar();
	final JLabel progress = new JLabel("No data execution running.");
	final JButton save = new JButton("Save status JSON…");
	final ReportReviewView view = new ReportReviewView();
	final JLabel status = new JLabel(
			"Enter source and target connections, then New job; or open saved settings. Inspection does not copy rows.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	final SavedReportButton savedReport = new SavedReportButton(this::isBusy);
	private final EditorFileChooser inputDialogs = new EditorFileChooser();
	final EditorFileChooser outputDialogs = new EditorFileChooser();
	private final JPanel settings = new JPanel(new BorderLayout(4, 4));
	private final JToggleButton advancedToggle = new JToggleButton("Advanced options");
	private final Planner planner;
	private final DataMigrationInputActions inputActions;
	private final DataMigrationExecutionActions executionActions;
	private final Saver saver;
	DataMigrationPlanningService.Result result;
	long revision;
	private java.util.function.Predicate<Path> jobEditor;
	PlanningJdbc.Spec reviewedSource;
	PlanningJdbc.Spec reviewedTarget;
	boolean busy;

	@FunctionalInterface
	interface ConnectionTester {
		String test(PlanningJdbc.Spec connection, char[] password);
	}

	@FunctionalInterface
	interface SchemaLister {
		java.util.List<String> list(PlanningJdbc.Spec source, char[] password);
	}

	@FunctionalInterface
	interface DraftDiscovery {
		DataMigrationDraftService.Prepared discover(PlanningJdbc.Spec source, char[] password, String schema,
				Path parent);
	}

	@FunctionalInterface
	interface Executor {
		DataMigrationExecutionService.Outcome execute(DataMigrationPlanningService.Result result,
				PlanningJdbc.Spec source, char[] sourcePassword, PlanningJdbc.Spec target, char[] targetPassword,
				DataMigrationExecutionControl control);
	}

	@FunctionalInterface
	interface ExecutionSaver {
		void save(DataMigrationExecutionService.Outcome result, Path destination) throws Exception;
	}

	@FunctionalInterface
	interface Planner {
		DataMigrationPlanningService.Result plan(DataMigrationPlanningService.Request request, PlanningJdbc.Spec source,
				char[] sourcePassword, PlanningJdbc.Spec target, char[] targetPassword);
	}

	@FunctionalInterface
	interface Saver {
		void save(DataMigrationPlanningService.Result result, Path destination) throws Exception;
	}

	DataMigrationPlanPanel() {
		this(new DataMigrationPlanningService()::plan, new DataMigrationPlanningService()::save);
	}

	DataMigrationPlanPanel(final Planner planner, final Saver saver) {
		this(planner, saver, new DataMigrationExecutionService()::execute, new DataMigrationExecutionService()::save);
	}

	DataMigrationPlanPanel(final Planner planner, final Saver saver, final ConnectionTester tester) {
		this(planner, saver, new DataMigrationExecutionService()::execute, new DataMigrationExecutionService()::save,
				new DataMigrationDraftService()::discover, null, tester);
	}

	DataMigrationPlanPanel(final Planner planner, final Saver saver, final Executor executor,
			final ExecutionSaver executionSaver) {
		this(planner, saver, executor, executionSaver, new DataMigrationDraftService()::discover, null);
	}

	DataMigrationPlanPanel(final Planner planner, final Saver saver, final Executor executor,
			final ExecutionSaver executionSaver, final DraftDiscovery discovery,
			final java.util.function.Function<DataMigrationDraftService.Prepared, com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot> dialog) {
		this(planner, saver, executor, executionSaver, discovery, dialog, new JdbcConnectionTestService()::test);
	}

	private DataMigrationPlanPanel(final Planner planner, final Saver saver, final Executor executor,
			final ExecutionSaver executionSaver, final DraftDiscovery discovery,
			final java.util.function.Function<DataMigrationDraftService.Prepared, com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO.Snapshot> dialog,
			final ConnectionTester tester) {
		super(new BorderLayout(8, 8));
		inputActions = new DataMigrationInputActions(this, java.util.Objects.requireNonNull(discovery), dialog,
				java.util.Objects.requireNonNull(tester));
		this.planner = java.util.Objects.requireNonNull(planner, "planner");
		this.saver = java.util.Objects.requireNonNull(saver, "saver");
		executionActions = new DataMigrationExecutionActions(this,
				java.util.Objects.requireNonNull(executor, "executor"),
				java.util.Objects.requireNonNull(executionSaver, "executionSaver"));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var fileRow = new JPanel(new BorderLayout(8, 0));
		final var browse = new JButton("Browse…");
		fileRow.add(new JLabel("Saved job"), BorderLayout.WEST);
		fileRow.add(configurationFile, BorderLayout.CENTER);
		FileDropHandler.path(configurationFile, false, java.util.List.of("yaml", "yml"), () -> !isBusy(),
				FileDropHandler.errors(status, statusPanel));
		final var fileActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		fileActions.add(newMigration);
		fileActions.add(browse);
		fileActions.add(editJob);
		fileRow.add(fileActions, BorderLayout.EAST);
		editJob.setEnabled(false);
		editJob.addActionListener(event -> editSelectedJob());
		settings.add(fileRow, BorderLayout.NORTH);
		final var connections = new JPanel(new GridLayout(1, 2, 8, 0));
		connections.add(source);
		connections.add(target);
		settings.add(connections, BorderLayout.CENTER);
		final var schemaSelection = new JPanel(new BorderLayout(4, 0));
		sourceSchemaSummary.setEditable(false);
		schemaSelection.add(sourceSchemaSummary, BorderLayout.CENTER);
		schemaSelection.add(chooseSourceSchema, BorderLayout.EAST);
		source.row(6, new JLabel("Schema"), schemaSelection);
		chooseSourceSchema.addActionListener(event -> chooseSourceSchema(new SourceSchemaDiscoveryService()::list,
				names -> SourceSchemaSelectionPanel.showDialog(this, names, sourceSchema.getText())));
		final var advanced = new JPanel(new BorderLayout(8, 0));
		advanced.add(new JLabel("Expected configuration SHA-256"), BorderLayout.WEST);
		FieldHints.placeholder(expectedFingerprint, "Optional: stop if the job YAML no longer has this fingerprint");
		advanced.add(expectedFingerprint, BorderLayout.CENTER);
		final var schemaRow = new JPanel(new BorderLayout(8, 0));
		schemaRow.add(new JLabel("Source schema"), BorderLayout.WEST);
		FieldHints.placeholder(sourceSchema, "Blank uses the source connection's current schema");
		schemaRow.add(sourceSchema, BorderLayout.CENTER);
		advanced.add(schemaRow, BorderLayout.SOUTH);
		advanced.setVisible(false);
		settings.add(advanced, BorderLayout.SOUTH);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(advancedToggle);
		actions.add(focusReview);
		actions.add(build);
		actions.add(save);
		actions.add(apply);
		final var results = new JPanel(new FlowLayout(FlowLayout.LEFT));
		results.add(showExecution);
		results.add(saveExecution);
		results.add(showVerification);
		results.add(saveVerification);
		showExecution.setEnabled(false);
		showVerification.setEnabled(false);
		saveVerification.setEnabled(false);
		final var input = new JPanel(new BorderLayout());
		final var inputViewport = new JScrollPane(settings);
		input.add(inputViewport, BorderLayout.CENTER);
		final var executionRow = new JPanel(new BorderLayout(8, 0));
		activity.setPreferredSize(new java.awt.Dimension(70, 16));
		executionRow.add(activity, BorderLayout.WEST);
		executionRow.add(progress, BorderLayout.CENTER);
		executionRow.add(pause, BorderLayout.EAST);
		final var controls = new JPanel(new BorderLayout(4, 4));
		controls.add(actions, BorderLayout.NORTH);
		controls.add(results, BorderLayout.CENTER);
		controls.add(executionRow, BorderLayout.SOUTH);
		input.add(controls, BorderLayout.SOUTH);
		pause.setEnabled(false);
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, input, view);
		split.setResizeWeight(0.25);
		split.setDividerLocation(355);
		reviewFocus = new ReviewFocusControl(split, inputViewport, controls, focusReview, advancedToggle);
		add(split, BorderLayout.CENTER);
		final var footer = new JPanel(new BorderLayout(8, 0));
		footer.add(statusPanel, BorderLayout.CENTER);
		footer.add(savedReport, BorderLayout.EAST);
		add(footer, BorderLayout.SOUTH);
		advancedToggle.addActionListener(event -> {
			advanced.setVisible(advancedToggle.isSelected());
			source.showDriver(advancedToggle.isSelected());
			target.showDriver(advancedToggle.isSelected());
			settings.revalidate();
			split.setDividerLocation(advancedToggle.isSelected() ? 425 : 355);
		});
		final DocumentListener changed = new DocumentListener() {
			public void insertUpdate(final DocumentEvent event) {
				clear();
			}

			public void removeUpdate(final DocumentEvent event) {
				clear();
			}

			public void changedUpdate(final DocumentEvent event) {
				clear();
			}
		};
		configurationFile.getDocument().addDocumentListener(changed);
		sourceSchema.getDocument().addDocumentListener(changed);
		sourceSchema.getDocument().addDocumentListener(new DocumentListener() {
			private void refresh() {
				final String name = sourceSchema.getText();
				sourceSchemaSummary.setText(name.isBlank() ? "Current schema (default)" : name);
				sourceSchemaSummary.setToolTipText("Schema: " + sourceSchemaSummary.getText());
				sourceSchemaSummary.setCaretPosition(0);
			}

			public void insertUpdate(DocumentEvent event) {
				refresh();
			}

			public void removeUpdate(DocumentEvent event) {
				refresh();
			}

			public void changedUpdate(DocumentEvent event) {
				refresh();
			}
		});
		final DocumentListener sourceIdentityChanged = new DocumentListener() {
			private void reset() {
				if (!sourceSchema.getText().isEmpty())
					sourceSchema.setText("");
			}

			public void insertUpdate(DocumentEvent event) {
				reset();
			}

			public void removeUpdate(DocumentEvent event) {
				reset();
			}

			public void changedUpdate(DocumentEvent event) {
				reset();
			}
		};
		for (var field : new JTextField[] { source.jdbcUrl, source.username, source.driverClass })
			field.getDocument().addDocumentListener(sourceIdentityChanged);
		expectedFingerprint.getDocument().addDocumentListener(changed);
		for (final var fields : new ConnectionFields[] { source, target }) {
			fields.test.addActionListener(event -> testConnection(fields));
			fields.buildUrl.addActionListener(event -> buildConnectionUrl(fields,
					url -> inputActions.showUrlBuilder(url, fields == source ? "Source" : "Target")));
			for (final var field : new JTextField[] { fields.jdbcUrl, fields.username, fields.password,
					fields.driverClass }) {
				field.getDocument().addDocumentListener(changed);
			}
		}
		browse.addActionListener(event -> {
			final var chooser = inputChooser();
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				configurationFile.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		build.addActionListener(event -> build());
		newMigration.addActionListener(event -> inputActions.chooseNewMigration());
		save.addActionListener(event -> chooseSave());
		save.setEnabled(false);
		apply.setEnabled(false);
		saveExecution.setEnabled(false);
		apply.addActionListener(event -> executionActions.confirmExecution());
		DisabledReason.install(editJob,
				() -> jobEditor == null ? null
						: !configurationFile.isEnabled() ? "Wait for the current operation to finish."
								: "Choose a saved job YAML first.");
		DisabledReason.install(save,
				() -> busy ? "Wait for the current operation to finish." : "Choose Review plan first.");
		DisabledReason.install(apply, () -> busy || isExecuting() ? "Wait for the current operation to finish."
				: DataMigrationExecutionService.restriction(result));
		executionActions.refreshActions(false);
	}

	void setJobEditor(final java.util.function.Predicate<Path> jobEditor) {
		this.jobEditor = jobEditor;
		refreshEditAction();
	}

	private void refreshEditAction() {
		editJob.setEnabled(
				jobEditor != null && configurationFile.isEnabled() && !configurationFile.getText().isBlank());
	}

	void editSelectedJob() {
		if (jobEditor == null || !editJob.isEnabled() || !configurationFile.isEnabled())
			return;
		final Path path;
		try {
			path = Path.of(configurationFile.getText().trim()).toAbsolutePath().normalize();
		} catch (final java.nio.file.InvalidPathException exception) {
			status.setText("Enter a valid job YAML path before opening the editor.");
			return;
		}
		if (jobEditor.test(path)) {
			clear();
			status.setText("Editing job YAML. Save and select the saved job, then inspect a new plan.");
		} else {
			status.setText("Job was not opened. The current review is retained.");
		}
	}

	private void clear() {
		refreshEditAction();
		revision++;
		result = null;
		apply.setEnabled(false);
		view.clear();
		save.setEnabled(false);
		status.setText("Inputs changed. Inspect again to read current target and checkpoint state.");
	}

	JFileChooser inputChooser() {
		return inputDialogs.forFile(new FileNameExtensionFilter("Migration job YAML / JSON", "yaml", "yml", "json"),
				configurationFile.getText());
	}

	JFileChooser outputChooser(final String filename) {
		return outputDialogs.forOutput(new FileNameExtensionFilter("Migration report JSON", "json"), filename,
				configurationFile.getText());
	}

	void busy(final boolean busy) {
		this.busy = busy;
		savedReport.refresh();
		ComponentEnablement.setTreeEnabled(settings, !busy);
		reviewFocus.setBusy(busy);
		build.setEnabled(!busy);
		newMigration.setEnabled(!busy);
		refreshEditAction();
		save.setEnabled(!busy && result != null);
		apply.setEnabled(!busy && result != null && DataMigrationExecutionService.restriction(result) == null);
		executionActions.refreshActions(busy);
	}

	void chooseSourceSchema(final SchemaLister lister,
			final java.util.function.Function<java.util.List<String>, String> picker) {
		inputActions.chooseSourceSchema(lister, picker);
	}

	void buildConnectionUrl(final ConnectionFields fields, final java.util.function.Function<String, String> dialog) {
		inputActions.buildConnectionUrl(fields, dialog);
	}

	void testConnection(final ConnectionFields fields) {
		inputActions.testConnection(fields);
	}

	void createMigration(final Path parent) {
		inputActions.createMigration(parent);
	}

	void build() {
		if (busy || blockedByProfile())
			return;
		final String path = configurationFile.getText().trim();
		final String fingerprint = expectedFingerprint.getText().trim();
		final var sourceInput = source.snapshot();
		final var targetInput = target.snapshot();
		final char[] sourceSecret = source.password.getPassword();
		final char[] targetSecret = target.password.getPassword();
		source.password.setText("");
		target.password.setText("");
		clear();
		final long requestedRevision = revision;
		busy(true);
		status.setText("Connecting, resolving job and checking target/checkpoints…");
		new SwingWorker<DataMigrationPlanningService.Result, Void>() {
			@Override
			protected DataMigrationPlanningService.Result doInBackground() {
				try {
					if (path.isEmpty()) {
						throw new IllegalArgumentException("Select a migration job YAML file.");
					}
					return planner.plan(new DataMigrationPlanningService.Request(Path.of(path), fingerprint),
							sourceInput.spec(), sourceSecret, targetInput.spec(), targetSecret);
				} finally {
					Arrays.fill(sourceSecret, '\0');
					Arrays.fill(targetSecret, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					if (revision != requestedRevision) {
						return;
					}
					result = get();
					inputDialogs.remember(result.configurationFile());
					reviewedSource = sourceInput.spec();
					reviewedTarget = targetInput.spec();
					view.showReview(result.review());
					final int[] widths = { 50, 110, 150, 150, 90, 75, 100, 125, 200 };
					for (int index = 0; index < Math.min(widths.length, view.tasks.getColumnCount()); index++) {
						view.tasks.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
					}
					status.setText(result.report().compatible()
							? "Snapshot generated. No rows migrated; execution approval has not been checked."
							: "Checkpoint mismatch found. Review details before any migration or resume.");
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failure(exception);
				} catch (final ExecutionException exception) {
					failure(exception.getCause());
				} finally {
					busy(false);
				}
			}
		}.execute();
	}

	boolean isExecuting() {
		return executionActions.isExecuting();
	}

	void executeReviewed(final char[] sourcePassword, final char[] targetPassword) {
		executionActions.executeReviewed(sourcePassword, targetPassword);
	}

	void refreshProgress() {
		executionActions.refreshProgress();
	}

	void saveExecutionTo(final Path destination) {
		executionActions.saveExecutionTo(destination);
	}

	void saveVerificationTo(final Path destination) {
		executionActions.saveVerificationTo(destination);
	}

	boolean isBusy() {
		return busy || isExecuting() || source.profile.isBusy() || target.profile.isBusy();
	}

	boolean selectDriverClass(final String name, final boolean sourceConnection) {
		if (isBusy())
			return false;
		showInputs();
		(sourceConnection ? source : target).driverClass.setText(java.util.Objects.requireNonNull(name));
		if (!advancedToggle.isSelected())
			advancedToggle.doClick(0);
		return true;
	}

	void showInputs() {
		reviewFocus.showInputs();
	}

	boolean blockedByProfile() {
		if (!source.profile.isBusy() && !target.profile.isBusy())
			return false;
		status.setText("Wait for the connection profile operations to finish, then try again.");
		return true;
	}

	private void chooseSave() {
		final var chooser = outputChooser("data-migration-status.json");
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		final Path destination = chooser.getSelectedFile().toPath();
		if (Files.exists(destination) && JOptionPane.showConfirmDialog(this, "Replace " + destination + "?",
				"Replace status report", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		saveTo(destination);
	}

	void saveTo(final Path destination) {
		final var snapshot = result;
		if (snapshot == null || busy || blockedByProfile()) {
			return;
		}
		busy(true);
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				saver.save(snapshot, destination);
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					status.setText("Saved status report: " + destination);
					savedReport.saved(destination, ReportReviewService.Type.BULK_OPERATIONAL);
					outputDialogs.remember(destination);
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failure(exception);
				} catch (final ExecutionException exception) {
					failure(exception.getCause());
				} finally {
					busy(false);
				}
			}
		}.execute();
	}

	void failure(final Throwable failure) {
		view.summary.setText("Operation failed.\n" + EditorStatusPanel.failureDetails(failure));
		status.setText("Operation failed. Check job YAML, Schema XML, connections and drivers.");
		statusPanel.setDetails(status.getText() + "\n\n" + view.summary.getText());
	}

}
