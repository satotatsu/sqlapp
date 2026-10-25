/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
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
import javax.swing.JPasswordField;
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
	final JButton build = new JButton("Connect and inspect plan");
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
	final JLabel status = new JLabel("Select job YAML and connections. Inspection does not migrate rows.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	private final EditorFileChooser inputDialogs = new EditorFileChooser();
	private final EditorFileChooser outputDialogs = new EditorFileChooser();
	private final JPanel settings = new JPanel(new BorderLayout(4, 4));
	private final JToggleButton advancedToggle = new JToggleButton("Advanced options");
	private final Planner planner;
	private final Saver saver;
	private DataMigrationPlanningService.Result result;
	private long revision;
	private java.util.function.Predicate<Path> jobEditor;
	private PlanningJdbc.Spec reviewedSource;
	private PlanningJdbc.Spec reviewedTarget;
	private DataMigrationExecutionService.Outcome execution;
	private boolean executing;
	private DataMigrationExecutionControl control;
	private final javax.swing.Timer progressTimer = new javax.swing.Timer(250, event -> refreshProgress());
	private final Executor executor;
	private final ExecutionSaver executionSaver;

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

	DataMigrationPlanPanel(final Planner planner, final Saver saver, final Executor executor,
			final ExecutionSaver executionSaver) {
		super(new BorderLayout(8, 8));
		this.executor = java.util.Objects.requireNonNull(executor, "executor");
		this.executionSaver = java.util.Objects.requireNonNull(executionSaver, "executionSaver");
		this.planner = java.util.Objects.requireNonNull(planner, "planner");
		this.saver = java.util.Objects.requireNonNull(saver, "saver");
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var fileRow = new JPanel(new BorderLayout(8, 0));
		final var browse = new JButton("Browse…");
		fileRow.add(new JLabel("Job YAML"), BorderLayout.WEST);
		fileRow.add(configurationFile, BorderLayout.CENTER);
		final var fileActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
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
		final var advanced = new JPanel(new BorderLayout(8, 0));
		advanced.add(new JLabel("Expected configuration SHA-256 (optional)"), BorderLayout.WEST);
		advanced.add(expectedFingerprint, BorderLayout.CENTER);
		advanced.setVisible(false);
		settings.add(advanced, BorderLayout.SOUTH);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(advancedToggle);
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
		showExecution.addActionListener(event -> view.showReview(execution.review()));
		showVerification.addActionListener(
				event -> view.showReview(new ReportReviewService().verification(execution.verificationReport())));
		saveVerification.addActionListener(event -> {
			final var chooser = outputChooser("data-verification.json");
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				final var destination = chooser.getSelectedFile().toPath();
				if (!Files.exists(destination) || JOptionPane.showConfirmDialog(this, "Replace " + destination + "?",
						"Replace verification report", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
					saveVerificationTo(destination);
			}
		});
		final var input = new JPanel(new BorderLayout());
		input.add(new JScrollPane(settings), BorderLayout.CENTER);
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
		pause.addActionListener(event -> {
			if (executing && control != null && control.requestPause())
				refreshProgress();
		});
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, input, view);
		split.setResizeWeight(0.25);
		split.setDividerLocation(325);
		add(split, BorderLayout.CENTER);
		add(statusPanel, BorderLayout.SOUTH);
		advancedToggle.addActionListener(event -> {
			advanced.setVisible(advancedToggle.isSelected());
			source.showDriver(advancedToggle.isSelected());
			target.showDriver(advancedToggle.isSelected());
			settings.revalidate();
			split.setDividerLocation(advancedToggle.isSelected() ? 395 : 325);
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
		expectedFingerprint.getDocument().addDocumentListener(changed);
		for (final var fields : new ConnectionFields[] { source, target }) {
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
		save.addActionListener(event -> chooseSave());
		save.setEnabled(false);
		apply.setEnabled(false);
		saveExecution.setEnabled(false);
		apply.addActionListener(event -> confirmExecution());
		saveExecution.addActionListener(event -> {
			final var chooser = outputChooser("data-execution-audit.json");
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				final var destination = chooser.getSelectedFile().toPath();
				if (!Files.exists(destination) || JOptionPane.showConfirmDialog(this, "Replace " + destination + "?",
						"Replace audit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					saveExecutionTo(destination);
				}
			}
		});
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

	private void busy(final boolean busy) {
		enable(settings, !busy);
		advancedToggle.setEnabled(!busy);
		build.setEnabled(!busy);
		refreshEditAction();
		save.setEnabled(!busy && result != null);
		apply.setEnabled(!busy && result != null && DataMigrationExecutionService.restriction(result) == null);
		saveExecution.setEnabled(!busy && execution != null && execution.hasAudit());
		showExecution.setEnabled(!busy && execution != null);
		showVerification.setEnabled(!busy && execution != null && execution.verificationReport() != null);
		saveVerification.setEnabled(showVerification.isEnabled());
	}

	private static void enable(final Container parent, final boolean enabled) {
		for (final var child : parent.getComponents()) {
			child.setEnabled(enabled);
			if (child instanceof Container nested) {
				enable(nested, enabled);
			}
		}
	}

	private void build() {
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
		return executing;
	}

	private void confirmExecution() {
		final var confirmed = result;
		if (confirmed == null || !apply.isEnabled())
			return;
		final var sourceSecret = new JPasswordField();
		final var targetSecret = new JPasswordField();
		final var message = new javax.swing.JTextArea(
				"Execute job: " + confirmed.report().jobId() + "\nSource: " + reviewedSource.jdbcUrl() + "\nTarget: "
						+ reviewedTarget.jdbcUrl() + "\nPlan: " + confirmed.report().planFingerprint()
						+ "\nConfiguration: " + confirmed.report().provenance().configurationFingerprint()
						+ "\nYAML checkpoint, lease, report and verification settings apply."
						+ "\nEach chunk commits separately. Failure can leave committed rows."
						+ "\nReview configured output files in the plan before continuing.");
		message.setEditable(false);
		final var content = new JPanel(new GridLayout(0, 1, 4, 4));
		content.add(new JScrollPane(message));
		content.add(new JLabel("Source password (not saved)"));
		content.add(sourceSecret);
		content.add(new JLabel("Target password (not saved)"));
		content.add(targetSecret);
		final Object[] options = { "Execute", "Cancel" };
		final int choice = JOptionPane.showOptionDialog(this, content, "Execute data migration",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		final char[] sourcePassword = sourceSecret.getPassword();
		final char[] targetPassword = targetSecret.getPassword();
		sourceSecret.setText("");
		targetSecret.setText("");
		try {
			if (choice == 0 && result == confirmed)
				executeReviewed(sourcePassword, targetPassword);
		} finally {
			Arrays.fill(sourcePassword, '\0');
			Arrays.fill(targetPassword, '\0');
		}
	}

	void executeReviewed(final char[] sourcePassword, final char[] targetPassword) {
		try {
			if (!apply.isEnabled() || result == null || executing)
				return;
			final var confirmed = result;
			final var sourceSpec = reviewedSource;
			final var targetSpec = reviewedTarget;
			final char[] sourceCopy = sourcePassword.clone();
			final char[] targetCopy = targetPassword.clone();
			result = null;
			execution = null;
			executing = true;
			control = new DataMigrationExecutionControl();
			final var executionControl = control;
			activity.setIndeterminate(true);
			refreshProgress();
			progressTimer.start();
			busy(true);
			status.setText("Executing reviewed data job. Wait for the outcome before closing.");
			new SwingWorker<DataMigrationExecutionService.Outcome, Void>() {
				protected DataMigrationExecutionService.Outcome doInBackground() {
					try {
						return executor.execute(confirmed, sourceSpec, sourceCopy, targetSpec, targetCopy,
								executionControl);
					} finally {
						Arrays.fill(sourceCopy, '\0');
						Arrays.fill(targetCopy, '\0');
					}
				}

				protected void done() {
					try {
						execution = get();
						view.showReview(execution.review());
						status.setText(execution.successful()
								? "Data execution completed. Save the audit; inspect again before another run."
								: execution.paused()
										? "Data execution paused. Save the audit, inspect checkpoints and confirm again to resume."
										: "Data execution failed. Review committed chunks and checkpoints before retrying.");
						statusPanel.setDetails(status.getText() + "\n\n" + view.summary.getText());
					} catch (final InterruptedException exception) {
						Thread.currentThread().interrupt();
						failure(exception);
					} catch (final ExecutionException exception) {
						failure(exception.getCause());
					} finally {
						executing = false;
						progressTimer.stop();
						activity.setIndeterminate(false);
						pause.setEnabled(false);
						final var last = executionControl.progress();
						progress.setText("Execution ended." + (last.migrationId().isEmpty() ? ""
								: " Last checkpoint: " + last.migrationId() + ": " + last.checkpointRows() + " rows; "
										+ last.completedChunks() + " chunks"));
						progress.setToolTipText(progress.getText());
						busy(false);
					}
				}
			}.execute();
		} finally {
			Arrays.fill(sourcePassword, '\0');
			Arrays.fill(targetPassword, '\0');
		}
	}

	void refreshProgress() {
		if (!executing || control == null)
			return;
		final var snapshot = control.progress();
		final boolean requested = control.pauseRequested();
		pause.setEnabled(snapshot.pauseAvailable() && !requested);
		pause.setToolTipText("Stops after a committed chunk and saved checkpoint; does not interrupt JDBC calls.");
		progress.setText((requested ? "Pause requested; waiting for a committed chunk. " : "") + snapshot.phase()
				+ (snapshot.migrationId().isEmpty() ? ""
						: " | " + snapshot.migrationId() + ": " + snapshot.checkpointRows() + " checkpoint rows; "
								+ snapshot.completedChunks() + " chunks"));
		progress.setToolTipText(progress.getText());
	}

	void saveExecutionTo(final Path destination) {
		if (execution == null || !saveExecution.isEnabled())
			return;
		final var snapshot = execution;
		busy(true);
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() throws Exception {
				executionSaver.save(snapshot, destination);
				return null;
			}

			protected void done() {
				try {
					get();
					status.setText("Saved execution audit: " + destination);
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

	void saveVerificationTo(final Path destination) {
		if (execution == null || !saveVerification.isEnabled())
			return;
		final var snapshot = execution;
		busy(true);
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() {
				new DataMigrationExecutionService().saveVerification(snapshot, destination);
				return null;
			}

			protected void done() {
				try {
					get();
					status.setText("Saved verification report: " + destination);
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
		if (snapshot == null) {
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

	private void failure(final Throwable failure) {
		view.summary.setText("Operation failed.\n" + EditorStatusPanel.failureDetails(failure));
		status.setText("Operation failed. Check job YAML, Schema XML, connections and drivers.");
		statusPanel.setDetails(status.getText() + "\n\n" + view.summary.getText());
	}

	static final class ConnectionFields extends JPanel {
		private static final long serialVersionUID = 1L;
		final JTextField jdbcUrl = new JTextField(20);
		final JTextField username = new JTextField();
		final JPasswordField password = new JPasswordField();
		final JTextField driverClass = new JTextField();
		final ConnectionProfilePanel profile = new ConnectionProfilePanel(jdbcUrl, username, driverClass, password);
		private final JLabel driverLabel = new JLabel("Driver class");

		ConnectionFields(final String title) {
			super(new GridBagLayout());
			setBorder(BorderFactory.createTitledBorder(title));
			row(0, new JLabel("JDBC URL"), jdbcUrl);
			row(1, new JLabel("Username"), username);
			row(2, new JLabel("Password"), password);
			row(3, driverLabel, driverClass);
			row(4, new JLabel("Profile"), profile);
			showDriver(false);
		}

		void showDriver(final boolean show) {
			driverLabel.setVisible(show);
			driverClass.setVisible(show);
		}

		Input snapshot() {
			return new Input(jdbcUrl.getText().trim(), username.getText().trim(), driverClass.getText().trim());
		}

		private void row(final int index, final JLabel label, final Component field) {
			final var constraints = new GridBagConstraints();
			constraints.gridy = index;
			constraints.insets = new Insets(3, 3, 3, 3);
			constraints.anchor = GridBagConstraints.WEST;
			label.setLabelFor(field);
			add(label, constraints);
			constraints.gridx = 1;
			constraints.weightx = 1;
			constraints.fill = GridBagConstraints.HORIZONTAL;
			add(field, constraints);
		}
	}

	private record Input(String jdbcUrl, String username, String driverClass) {
		PlanningJdbc.Spec spec() {
			return new PlanningJdbc.Spec(jdbcUrl, username, driverClass);
		}
	}
}
