/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Builds plans and applies only the explicitly reviewed connected plan.
 */
final class SchemaMigrationPlanPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField jdbcUrl = new JTextField(40);
	final JTextField username = new JTextField();
	final JPasswordField password = new JPasswordField();
	final JTextField upDirectory = new JTextField();
	final JTextField downDirectory = new JTextField();
	final JTextField setupDirectory = new JTextField();
	final JTextField finalizeDirectory = new JTextField();
	final JTextField targetVersion = new JTextField();
	final JTextField historyTable = new JTextField("changelog");
	final JTextField driverClass = new JTextField();
	final ConnectionProfilePanel profile = new ConnectionProfilePanel(jdbcUrl, username, driverClass, password);
	final JCheckBox checksumValidation = new JCheckBox("Record / validate checksums", true);
	final JCheckBox rejectOutOfOrder = new JCheckBox("Reject out-of-order migrations", true);
	final JCheckBox rejectNonTransactional = new JCheckBox("Reject non-transactional SQL", true);
	final JCheckBox requireDown = new JCheckBox("Require rollback SQL");
	final JCheckBox repeatables = new JCheckBox("Enable R__ repeatable migrations");
	final JCheckBox recursive = new JCheckBox("Search SQL directories recursively");
	final JButton build = new JButton("Connect and build plan");
	final JButton save = new JButton("Save plan JSON…");
	final JButton apply = new JButton("Apply reviewed plan…");
	final JButton saveExecution = new JButton("Save last result JSON…");
	final ReportReviewView view = new ReportReviewView();
	final JLabel status = new JLabel("Connect to read migration history. Migration SQL is not executed.");
	final EditorStatusPanel statusPanel = new EditorStatusPanel(status);
	final SavedReportButton savedReport = new SavedReportButton(this::isBusy);
	private final EditorFileChooser directoryDialogs = new EditorFileChooser();
	private final EditorFileChooser outputDialogs = new EditorFileChooser();
	private final JPanel form = new JPanel(new BorderLayout(4, 4));
	private final JToggleButton advancedToggle = new JToggleButton("Advanced options");
	private final Planner planner;
	private final Saver saver;
	private final Executor executor;
	private final ExecutionSaver executionSaver;

	private SchemaMigrationPlanningService.Result result;
	private SchemaMigrationPlanningService.Request reviewedRequest;
	private PlanningJdbc.Spec reviewedConnection;
	private SchemaMigrationExecutionService.Outcome execution;
	private boolean executing;
	private boolean busy;
	private long revision;

	@FunctionalInterface
	interface Planner {
		SchemaMigrationPlanningService.Result plan(SchemaMigrationPlanningService.Request request, String jdbcUrl,
				String username, char[] password, String driverClass);
	}

	@FunctionalInterface
	interface Saver {
		void save(SchemaMigrationPlanningService.Result result, Path destination) throws Exception;
	}

	@FunctionalInterface
	interface Executor {
		SchemaMigrationExecutionService.Outcome execute(SchemaMigrationPlanningService.Request request,
				PlanningJdbc.Spec connection, SchemaMigrationPlanningService.Result reviewed, char[] password);
	}

	@FunctionalInterface
	interface ExecutionSaver {
		void save(SchemaMigrationExecutionService.Outcome outcome, Path destination) throws Exception;
	}

	SchemaMigrationPlanPanel() {
		this(new SchemaMigrationPlanningService()::plan, new SchemaMigrationPlanningService()::save);
	}

	SchemaMigrationPlanPanel(final Planner planner, final Saver saver) {
		this(planner, saver, new SchemaMigrationExecutionService()::execute,
				new SchemaMigrationExecutionService()::save);
	}

	SchemaMigrationPlanPanel(final Planner planner, final Saver saver, final Executor executor,
			final ExecutionSaver executionSaver) {
		super(new BorderLayout(8, 8));
		this.planner = java.util.Objects.requireNonNull(planner, "planner");
		this.saver = java.util.Objects.requireNonNull(saver, "saver");
		this.executor = java.util.Objects.requireNonNull(executor, "executor");
		this.executionSaver = java.util.Objects.requireNonNull(executionSaver, "executionSaver");
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		final var common = new JPanel(new GridBagLayout());
		row(common, 0, "JDBC URL", jdbcUrl);
		row(common, 1, "Username", username);
		row(common, 2, "Password", password);
		row(common, 3, "Connection profile", profile);
		row(common, 4, "Up SQL directory", directoryField(upDirectory));
		form.add(common, BorderLayout.NORTH);
		final var advanced = new JPanel(new GridBagLayout());
		row(advanced, 0, "Down SQL directory", directoryField(downDirectory));
		row(advanced, 1, "Setup SQL directory", directoryField(setupDirectory));
		row(advanced, 2, "Finalize SQL directory", directoryField(finalizeDirectory));
		row(advanced, 3, "Target version (optional)", targetVersion);
		row(advanced, 4, "History table", historyTable);
		row(advanced, 5, "Driver class (optional)", driverClass);
		final var policies = new JPanel(new java.awt.GridLayout(3, 2));
		for (final var policy : new JCheckBox[] { checksumValidation, rejectOutOfOrder, rejectNonTransactional,
				requireDown, repeatables, recursive }) {
			policies.add(policy);
			policy.addActionListener(event -> clear());
		}
		row(advanced, 6, "Policies", policies);
		advanced.setVisible(false);
		form.add(advanced, BorderLayout.CENTER);
		final var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(advancedToggle);
		actions.add(build);
		actions.add(save);
		actions.add(apply);
		actions.add(saveExecution);
		final var input = new JPanel(new BorderLayout());
		input.add(new JScrollPane(form), BorderLayout.CENTER);
		input.add(actions, BorderLayout.SOUTH);
		input.setPreferredSize(new Dimension(900, 240));
		final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, input, view);
		split.setResizeWeight(0.25);
		split.setDividerLocation(240);
		add(split, BorderLayout.CENTER);
		final var footer = new JPanel(new BorderLayout(8, 0));
		footer.add(statusPanel, BorderLayout.CENTER);
		footer.add(savedReport, BorderLayout.EAST);
		add(footer, BorderLayout.SOUTH);
		advancedToggle.addActionListener(event -> {
			advanced.setVisible(advancedToggle.isSelected());
			input.revalidate();
			split.setDividerLocation(advancedToggle.isSelected() ? 400 : 240);
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
		for (final var field : new JTextField[] { jdbcUrl, username, password, upDirectory, downDirectory,
				setupDirectory, finalizeDirectory, targetVersion, historyTable, driverClass }) {
			field.getDocument().addDocumentListener(changed);
		}
		build.addActionListener(event -> build());
		save.addActionListener(event -> chooseSave());
		apply.addActionListener(event -> confirmApply());
		saveExecution.addActionListener(event -> chooseExecutionSave());
		save.setEnabled(false);
		apply.setEnabled(false);
		saveExecution.setEnabled(false);
	}

	private void row(final JPanel panel, final int row, final String label, final Component field) {
		final var constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(3, 3, 3, 3);
		constraints.anchor = GridBagConstraints.WEST;
		final var caption = new JLabel(label);
		caption.setLabelFor(field);
		panel.add(caption, constraints);
		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(field, constraints);
	}

	private JPanel directoryField(final JTextField field) {
		FileDropHandler.path(field, true, java.util.List.of(), () -> !isBusy(),
				FileDropHandler.errors(status, statusPanel));
		final var panel = new JPanel(new BorderLayout(6, 0));
		final var browse = new JButton("Browse…");
		panel.add(field, BorderLayout.CENTER);
		panel.add(browse, BorderLayout.EAST);
		browse.addActionListener(event -> {
			final var chooser = directoryChooser(field.getText());
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				field.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		return panel;
	}

	boolean selectUpDirectory(final Path directory) {
		if (executing || !upDirectory.isEnabled())
			return false;
		upDirectory.setText(
				java.util.Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize().toString());
		clear();
		status.setText(
				"Selected saved SQL directory. Review all SQL files and existing options, then connect and build a new plan.");
		return true;
	}

	JFileChooser directoryChooser(final String currentPath) {
		return directoryDialogs
				.forDirectory(currentPath == null || currentPath.isBlank() ? upDirectory.getText() : currentPath);
	}

	JFileChooser outputChooser(final String filename) {
		return outputDialogs.forOutputDirectory(
				new javax.swing.filechooser.FileNameExtensionFilter("Migration report JSON", "json"), filename,
				upDirectory.getText());
	}

	private void clear() {
		revision++;
		result = null;
		reviewedRequest = null;
		reviewedConnection = null;
		if (execution == null) {
			view.clear();
		}
		save.setEnabled(false);
		apply.setEnabled(false);
		status.setText("Inputs changed. Connect and build a new plan to review current migration history.");
	}

	private void busy(final boolean busy) {
		this.busy = busy;
		savedReport.refresh();
		enable(form, !busy);
		advancedToggle.setEnabled(!busy);
		build.setEnabled(!busy);
		save.setEnabled(!busy && result != null);
		final String restriction = SchemaMigrationExecutionService.restriction(reviewedRequest, result);
		apply.setEnabled(!busy && restriction == null);
		apply.setToolTipText(restriction);
		saveExecution.setEnabled(!busy && execution != null && execution.report() != null);
	}

	private void enable(final Container container, final boolean enabled) {
		for (final var child : container.getComponents()) {
			child.setEnabled(enabled);
			if (child instanceof Container nested) {
				enable(nested, enabled);
			}
		}
	}

	private void build() {
		if (busy || blockedByProfile())
			return;
		final String url = jdbcUrl.getText().trim();
		final String user = username.getText().trim();
		final String driver = driverClass.getText().trim();
		final String up = upDirectory.getText().trim();
		final String down = downDirectory.getText().trim();
		final String setup = setupDirectory.getText().trim();
		final String finish = finalizeDirectory.getText().trim();
		final String target = targetVersion.getText().trim();
		final String history = historyTable.getText().trim();
		final boolean checksums = checksumValidation.isSelected();
		final boolean ordered = rejectOutOfOrder.isSelected();
		final boolean transactional = rejectNonTransactional.isSelected();
		final boolean rollback = requireDown.isSelected();
		final boolean repeated = repeatables.isSelected();
		final boolean recurse = recursive.isSelected();
		final char[] secret = password.getPassword();
		password.setText("");
		clear();
		final long requestedRevision = revision;
		busy(true);
		status.setText("Connecting and reading migration history…");
		new SwingWorker<Prepared, Void>() {
			@Override
			protected Prepared doInBackground() {
				try {
					final var request = new SchemaMigrationPlanningService.Request(path(up), path(down), path(setup),
							path(finish), version(target), history, checksums, ordered, transactional, rollback,
							repeated, recurse);
					return new Prepared(planner.plan(request, url, user, secret, driver), request,
							new PlanningJdbc.Spec(url, user, driver));
				} finally {
					Arrays.fill(secret, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					if (revision != requestedRevision) {
						return;
					}
					final var prepared = get();
					result = prepared.result();
					reviewedRequest = prepared.request();
					directoryDialogs.rememberDirectory(prepared.request().upDirectory());
					reviewedConnection = prepared.connection();
					view.showReview(result.review());
					status.setText(result.artifact().plan().hasBlockers()
							? "Plan generated with blockers. Review details; migration SQL was not executed."
							: "Plan generated. Review details; migration SQL was not executed.");
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

	private static Path path(final String value) {
		return value.isEmpty() ? null : Path.of(value);
	}

	private record Prepared(SchemaMigrationPlanningService.Result result,
			SchemaMigrationPlanningService.Request request, PlanningJdbc.Spec connection) {
	}

	private void confirmApply() {
		if (busy || blockedByProfile())
			return;
		if (!apply.isEnabled()) {
			return;
		}
		final var confirmed = result;
		final var executionPassword = new JPasswordField(24);
		final var text = new JTextArea("This applies SQL to the selected database.\n" + "JDBC URL: "
				+ reviewedConnection.jdbcUrl() + "\nUser: " + reviewedConnection.username() + "\nPlan fingerprint: "
				+ result.artifact().planFingerprint() + "\n\n" + result.review().summary(), 14, 65);
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		final var confirmation = new JPanel(new BorderLayout(8, 8));
		confirmation.add(new JScrollPane(text), BorderLayout.CENTER);
		final var credentials = new JPanel(new BorderLayout(8, 0));
		credentials.add(new JLabel("Re-enter password"), BorderLayout.WEST);
		credentials.add(executionPassword, BorderLayout.CENTER);
		confirmation.add(credentials, BorderLayout.SOUTH);
		final String[] options = { "Apply", "Cancel" };
		final int choice = JOptionPane.showOptionDialog(this, confirmation, "Apply reviewed Schema migration",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		final char[] secret = executionPassword.getPassword();
		executionPassword.setText("");
		if (choice == 0 && result == confirmed) {
			applyReviewed(secret);
		} else {
			Arrays.fill(secret, '\0');
			if (choice == 0) {
				status.setText("Plan changed during confirmation. Review the current plan before applying.");
			}
		}
	}

	void applyReviewed(final char[] secret) {
		if (!apply.isEnabled() || busy || blockedByProfile()) {
			Arrays.fill(secret, '\0');
			return;
		}
		final var snapshot = result;
		final var request = reviewedRequest;
		final var connection = reviewedConnection;
		result = null;
		reviewedRequest = null;
		reviewedConnection = null;
		execution = null;
		executing = true;
		view.clear();
		busy(true);
		status.setText("Rechecking and applying reviewed plan… Do not close the application.");
		new SwingWorker<SchemaMigrationExecutionService.Outcome, Void>() {
			@Override
			protected SchemaMigrationExecutionService.Outcome doInBackground() {
				try {
					return executor.execute(request, connection, snapshot, secret);
				} finally {
					Arrays.fill(secret, '\0');
				}
			}

			@Override
			protected void done() {
				try {
					// Execution evidence must remain visible even if inputs were changed
					// programmatically.
					execution = get();
					view.showReview(execution.review());
					status.setText(execution.successful()
							? "Execution completed. Save the result; build a new plan before applying again."
							: "Execution did not complete normally. Review the result and actual database before retrying.");
					statusPanel.setDetails(status.getText() + "\n\n" + view.summary.getText());
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					failure(exception);
				} catch (final ExecutionException exception) {
					failure(exception.getCause());
				} finally {
					executing = false;
					busy(false);
				}
			}
		}.execute();
	}

	boolean isExecuting() {
		return executing;
	}

	boolean isBusy() {
		return busy || executing || profile.isBusy();
	}

	boolean selectDriverClass(final String name) {
		if (isBusy())
			return false;
		driverClass.setText(java.util.Objects.requireNonNull(name));
		if (!advancedToggle.isSelected())
			advancedToggle.doClick(0);
		return true;
	}

	private boolean blockedByProfile() {
		if (!profile.isBusy())
			return false;
		status.setText("Wait for the connection profile operation to finish, then try again.");
		return true;
	}

	private void chooseExecutionSave() {
		final var chooser = outputChooser("migration-execution.json");
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		final Path destination = chooser.getSelectedFile().toPath();
		if (Files.exists(destination) && JOptionPane.showConfirmDialog(this, "Replace " + destination + "?",
				"Replace execution result", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		saveExecutionTo(destination);
	}

	void saveExecutionTo(final Path destination) {
		final var snapshot = execution;
		if (snapshot == null || snapshot.report() == null || busy || blockedByProfile()) {
			return;
		}
		busy(true);
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				executionSaver.save(snapshot, destination);
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					status.setText("Saved execution result: " + destination);
					savedReport.saved(destination, ReportReviewService.Type.SCHEMA_EXECUTION);
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

	private static Long version(final String value) {
		if (value.isEmpty()) {
			return null;
		}
		try {
			return Long.valueOf(value);
		} catch (final NumberFormatException exception) {
			throw new IllegalArgumentException("Target version must be a non-negative integer.");
		}
	}

	private void chooseSave() {
		final var chooser = outputChooser("migration-plan.json");
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		final var destination = chooser.getSelectedFile().toPath();
		if (Files.exists(destination) && JOptionPane.showConfirmDialog(this, "Replace " + destination + "?",
				"Replace migration plan", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
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
		status.setText("Saving reviewed plan…");
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
					status.setText("Saved plan: " + destination);
					savedReport.saved(destination, ReportReviewService.Type.SCHEMA_PLAN);
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
		status.setText("Operation failed. Check the connection, driver and migration settings.");
		statusPanel.setDetails(status.getText() + "\n\n" + view.summary.getText());
	}
}
