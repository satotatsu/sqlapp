/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import com.sqlapp.data.db.command.migration.transfer.TransferStagingComparisonReportIO;

/**
 * Saves the displayed comparison snapshot and offers the saved report to the
 * viewer.
 */
final class StagingComparisonSaveActions extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton save = new JButton("Save comparison…");
	final JButton repeat = new JButton("Compare again");
	final SavedReportButton saved;
	private final BooleanSupplier busy;
	private final Consumer<Boolean> setBusy;
	private final Consumer<String> status;
	private final EditorFileChooser files = new EditorFileChooser();
	private TransferStagingComparisonReportIO.Report report;
	private long revision;
	private Runnable repeatAction;

	StagingComparisonSaveActions(BooleanSupplier busy, Consumer<Boolean> setBusy, Consumer<String> status,
			Supplier<String> folderHint) {
		super(new FlowLayout(FlowLayout.LEFT));
		this.busy = busy;
		this.setBusy = setBusy;
		this.status = status;
		saved = new SavedReportButton(busy);
		saved.setText("Open last saved comparison");
		add(repeat);
		add(save);
		add(saved);
		repeat.setToolTipText(
				"Read the current target and retained staging again using the same result. No rows are changed.");
		repeat.addActionListener(event -> {
			if (!busy.getAsBoolean() && repeatAction != null)
				repeatAction.run();
		});
		save.addActionListener(event -> {
			if (busy.getAsBoolean() || report == null)
				return;
			var chooser = files.forDirectory(folderHint.get());
			chooser.setDialogTitle("Choose a folder for the staging comparison");
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
				saveTo(chooser.getSelectedFile().toPath());
		});
		refresh();
	}

	void show(TransferStagingComparisonReportIO.Report value) {
		revision++;
		report = value;
		refresh();
	}

	void clear() {
		beginComparison();
		saved.clear();
	}

	void beginComparison() {
		repeatAction = null;
		show(null);
	}

	void setRepeatAction(Runnable action) {
		repeatAction = java.util.Objects.requireNonNull(action);
		refresh();
	}

	void refresh() {
		repeat.setEnabled(!busy.getAsBoolean() && repeatAction != null);
		save.setEnabled(!busy.getAsBoolean() && report != null);
		saved.refresh();
	}

	void saveTo(Path folder) {
		if (busy.getAsBoolean() || report == null)
			return;
		var snapshot = report;
		var requested = revision;
		setBusy.accept(true);
		refresh();
		new SwingWorker<Path, Void>() {
			@Override
			protected Path doInBackground() throws Exception {
				return new TransferStagingComparisonReportIO().writeNew(folder, snapshot);
			}

			@Override
			protected void done() {
				try {
					var file = get();
					files.rememberDirectory(folder);
					if (requested == revision)
						saved.saved(file, ReportReviewService.Type.STAGING_COMPARISON);
					status.accept("Saved staging comparison: " + file);
				} catch (Exception failure) {
					status.accept(saveFailure(failure));
				} finally {
					setBusy.accept(false);
					refresh();
				}
			}
		}.execute();
	}

	static String saveFailure(Exception failure) {
		Throwable cause = failure;
		if (cause instanceof java.util.concurrent.ExecutionException && cause.getCause() != null)
			cause = cause.getCause();
		String guidance;
		if (cause instanceof java.nio.file.AccessDeniedException || cause instanceof SecurityException)
			guidance = "Access to the destination was denied. Choose a writable folder and save again.";
		else if (cause instanceof java.nio.file.FileAlreadyExistsException
				|| cause instanceof java.nio.file.NotDirectoryException)
			guidance = "The destination or one of its parents is a file. Choose a folder and save again.";
		else if (cause instanceof java.nio.file.NoSuchFileException)
			guidance = "The destination is unavailable. Reconnect the drive or choose another folder and save again.";
		else
			guidance = "Check available disk space and folder access, or choose another folder and save again.";
		return "Could not save the staging comparison. " + guidance
				+ " The displayed comparison is retained. Exception type: " + cause.getClass().getSimpleName();
	}
}
