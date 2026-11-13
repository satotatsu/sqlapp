/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

/**
 * Confirmed execution of a reviewed data job for {@link DataMigrationPlanPanel}:
 * password prompt, background run, progress and pause, and saving the last
 * execution audit and verification report. Inputs and buttons stay on the panel.
 */
final class DataMigrationExecutionActions {
	private final DataMigrationPlanPanel panel;
	private final DataMigrationPlanPanel.Executor executor;
	private final DataMigrationPlanPanel.ExecutionSaver executionSaver;
	private final javax.swing.Timer progressTimer = new javax.swing.Timer(250, event -> refreshProgress());
	private DataMigrationExecutionService.Outcome execution;
	private boolean executing;
	private DataMigrationExecutionControl control;

	DataMigrationExecutionActions(final DataMigrationPlanPanel panel, final DataMigrationPlanPanel.Executor executor,
			final DataMigrationPlanPanel.ExecutionSaver executionSaver) {
		this.panel = panel;
		this.executor = executor;
		this.executionSaver = executionSaver;
		panel.showExecution.addActionListener(event -> panel.view.showReview(execution.review()));
		panel.showVerification.addActionListener(
				event -> panel.view.showReview(new ReportReviewService().verification(execution.verificationReport())));
		panel.saveVerification.addActionListener(event -> {
			final var chooser = panel.outputChooser("data-verification.json");
			if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
				final var destination = chooser.getSelectedFile().toPath();
				if (!Files.exists(destination) || JOptionPane.showConfirmDialog(panel, "Replace " + destination + "?",
						"Replace verification report", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
					saveVerificationTo(destination);
			}
		});
		panel.pause.addActionListener(event -> {
			if (executing && control != null && control.requestPause())
				refreshProgress();
		});
		panel.saveExecution.addActionListener(event -> {
			final var chooser = panel.outputChooser("data-execution-audit.json");
			if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
				final var destination = chooser.getSelectedFile().toPath();
				if (!Files.exists(destination) || JOptionPane.showConfirmDialog(panel, "Replace " + destination + "?",
						"Replace audit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
					saveExecutionTo(destination);
				}
			}
		});
	}

	boolean isExecuting() {
		return executing;
	}

	/** Enables the last-result actions; called whenever the panel's busy state changes. */
	void refreshActions(final boolean busy) {
		panel.saveExecution.setEnabled(!busy && execution != null && execution.hasAudit());
		panel.showExecution.setEnabled(!busy && execution != null);
		panel.showVerification.setEnabled(!busy && execution != null && execution.verificationReport() != null);
		panel.saveVerification.setEnabled(panel.showVerification.isEnabled());
		// Result and progress rows only appear once there is something to show.
		setRowVisible(panel.showExecution, execution != null);
		setRowVisible(panel.progress, executing || execution != null);
	}

	private static void setRowVisible(final java.awt.Component member, final boolean visible) {
		final var row = member.getParent();
		if (row != null)
			row.setVisible(visible);
	}

	void confirmExecution() {
		if (panel.busy || panel.blockedByProfile())
			return;
		final var confirmed = panel.result;
		if (confirmed == null || !panel.apply.isEnabled())
			return;
		final var sourceSecret = new JPasswordField();
		final var targetSecret = new JPasswordField();
		final var message = new javax.swing.JTextArea(
				"Execute job: " + confirmed.report().jobId() + "\nSource: " + panel.reviewedSource.jdbcUrl() + "\nTarget: "
						+ panel.reviewedTarget.jdbcUrl() + "\nPlan: " + confirmed.report().planFingerprint()
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
		final int choice = JOptionPane.showOptionDialog(panel, content, "Execute data migration",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		final char[] sourcePassword = sourceSecret.getPassword();
		final char[] targetPassword = targetSecret.getPassword();
		sourceSecret.setText("");
		targetSecret.setText("");
		try {
			if (choice == 0 && panel.result == confirmed)
				executeReviewed(sourcePassword, targetPassword);
		} finally {
			Arrays.fill(sourcePassword, '\0');
			Arrays.fill(targetPassword, '\0');
		}
	}

	void executeReviewed(final char[] sourcePassword, final char[] targetPassword) {
		try {
			if (!panel.apply.isEnabled() || panel.result == null || executing || panel.busy || panel.blockedByProfile())
				return;
			final var confirmed = panel.result;
			final var sourceSpec = panel.reviewedSource;
			final var targetSpec = panel.reviewedTarget;
			final char[] sourceCopy = sourcePassword.clone();
			final char[] targetCopy = targetPassword.clone();
			panel.result = null;
			execution = null;
			executing = true;
			control = new DataMigrationExecutionControl();
			final var executionControl = control;
			panel.activity.setIndeterminate(true);
			refreshProgress();
			progressTimer.start();
			panel.busy(true);
			panel.status.setText("Executing reviewed data job. Wait for the outcome before closing.");
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
						panel.view.showReview(execution.review());
						panel.status.setText(execution.statusMessage());
						panel.statusPanel.setDetails(panel.status.getText() + "\n\n" + panel.view.summary.getText());
					} catch (final InterruptedException exception) {
						Thread.currentThread().interrupt();
						panel.failure(exception);
					} catch (final ExecutionException exception) {
						panel.failure(exception.getCause());
					} finally {
						executing = false;
						progressTimer.stop();
						panel.activity.setIndeterminate(false);
						panel.pause.setEnabled(false);
						final var last = executionControl.progress();
						panel.progress.setText("Execution ended." + (last.migrationId().isEmpty() ? ""
								: " Last checkpoint: " + last.migrationId() + ": " + last.checkpointRows() + " rows; "
										+ last.completedChunks() + " chunks"));
						panel.progress.setToolTipText(panel.progress.getText());
						panel.busy(false);
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
		panel.pause.setEnabled(snapshot.pauseAvailable() && !requested);
		panel.pause.setToolTipText("Stops after a committed chunk and saved checkpoint; does not interrupt JDBC calls.");
		panel.progress.setText((requested ? "Pause requested; waiting for a committed chunk. " : "") + snapshot.phase()
				+ (snapshot.migrationId().isEmpty() ? ""
						: " | " + snapshot.migrationId() + ": " + snapshot.checkpointRows() + " checkpoint rows; "
								+ snapshot.completedChunks() + " chunks"));
		panel.progress.setToolTipText(panel.progress.getText());
	}

	void saveExecutionTo(final Path destination) {
		if (execution == null || !panel.saveExecution.isEnabled() || panel.busy || panel.blockedByProfile())
			return;
		final var snapshot = execution;
		panel.busy(true);
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() throws Exception {
				executionSaver.save(snapshot, destination);
				return null;
			}

			protected void done() {
				try {
					get();
					panel.status.setText("Saved execution audit: " + destination);
					panel.savedReport.saved(destination, snapshot.failure() == null ? ReportReviewService.Type.BULK_EXECUTION
							: ReportReviewService.Type.BULK_FAILURE);
					panel.outputDialogs.remember(destination);
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					panel.failure(exception);
				} catch (final ExecutionException exception) {
					panel.failure(exception.getCause());
				} finally {
					panel.busy(false);
				}
			}
		}.execute();
	}

	void saveVerificationTo(final Path destination) {
		if (execution == null || !panel.saveVerification.isEnabled() || panel.busy || panel.blockedByProfile())
			return;
		final var snapshot = execution;
		panel.busy(true);
		new SwingWorker<Void, Void>() {
			protected Void doInBackground() {
				new DataMigrationExecutionService().saveVerification(snapshot, destination);
				return null;
			}

			protected void done() {
				try {
					get();
					panel.status.setText("Saved verification report: " + destination);
					panel.savedReport.saved(destination, ReportReviewService.Type.BULK_VERIFICATION);
					panel.outputDialogs.remember(destination);
				} catch (final InterruptedException exception) {
					Thread.currentThread().interrupt();
					panel.failure(exception);
				} catch (final ExecutionException exception) {
					panel.failure(exception.getCause());
				} finally {
					panel.busy(false);
				}
			}
		}.execute();
	}
}
