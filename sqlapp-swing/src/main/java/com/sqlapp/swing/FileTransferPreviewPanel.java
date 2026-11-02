/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.*;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.*;

/** Explicit offline samples; no runtime connection or execution approval. */
final class FileTransferPreviewPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	@FunctionalInterface
	interface Inspector {
		TransferFilePackageIO.Snapshot inspect(Path path) throws Exception;
	}

	@FunctionalInterface
	interface Sampler {
		TransferFileImportService.Preview sample(Path path, String fingerprint, String task, int limit)
				throws Exception;
	}

	@FunctionalInterface
	interface Validator {
		void validate(Path path, String fingerprint) throws Exception;
	}

	private record Choice(TransferFilePackageIO.TableFile table) {
		@Override
		public String toString() {
			return (table.catalog() == null ? "" : table.catalog() + ".")
					+ (table.schema() == null ? "" : table.schema() + ".") + table.table() + " / " + table.taskId();
		}
	}

	final JComboBox<Choice> tables = new JComboBox<>();
	final JSpinner rowLimit = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
	final JButton preview = new JButton("Load sample");
	final JButton reload = new JButton("Reload package");
	final JButton validate = new JButton("Validate all values");
	final JButton close = new JButton("Close");
	final JTextArea status = new JTextArea("Checking package integrity; no database connection is opened…", 2, 1);
	final JTextArea evidence = new JTextArea(4, 1);
	private final JPanel results = new JPanel(new BorderLayout());
	private final Sampler sampler;
	private final Inspector inspector;
	private final Validator validator;
	private final Path input;
	private TransferFilePackageIO.Snapshot snapshot;
	private boolean busy;
	private long revision;

	private enum ValueCheck {
		NOT_CHECKED, RUNNING, PASSED, FAILED
	}

	private ValueCheck valueCheck = ValueCheck.NOT_CHECKED;
	ReportDetailTablePanel sample;

	static void show(Component owner, Path input) {
		var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Package data preview",
				Dialog.ModalityType.APPLICATION_MODAL);
		var panel = new FileTransferPreviewPanel(
				input, path -> new TransferFilePackageIO()
						.read(Files.isDirectory(path) ? path.resolve("manifest.json") : path, null),
				new TransferFileImportService()::preview, dialog::dispose);
		dialog.setContentPane(panel);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (!panel.busy)
					dialog.dispose();
			}
		});
		dialog.setSize(1050, 650);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	FileTransferPreviewPanel(Path input, Inspector inspector, Sampler sampler, Runnable closed) {
		this(input, inspector, sampler, new TransferFileImportService()::validatePackage, closed);
	}

	FileTransferPreviewPanel(Path input, Inspector inspector, Sampler sampler, Validator validator, Runnable closed) {
		super(new BorderLayout(8, 8));
		this.sampler = sampler;
		this.inspector = inspector;
		this.validator = validator;
		this.input = input;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		var tableLabel = new JLabel("Package table");
		tableLabel.setLabelFor(tables);
		controls.add(tableLabel);
		controls.add(tables);
		var limitLabel = new JLabel("Sample rows (1–100)");
		limitLabel.setLabelFor(rowLimit);
		controls.add(limitLabel);
		controls.add(rowLimit);
		controls.add(preview);
		var checks = new JPanel(new FlowLayout(FlowLayout.LEFT));
		checks.add(reload);
		checks.add(validate);
		reload.setToolTipText(
				"Recheck package bytes and refresh tables. Previous samples and full-check evidence are cleared.");
		validate.setToolTipText(
				"Check every packaged value with the shared import reader. No target connection or import approval.");
		var heading = new JPanel(new BorderLayout());
		heading.add(
				new JLabel(
						"Offline sample only. Full value validation and target Review remain required before import."),
				BorderLayout.NORTH);
		heading.add(controls, BorderLayout.CENTER);
		heading.add(checks, BorderLayout.SOUTH);
		add(heading, BorderLayout.NORTH);
		add(results, BorderLayout.CENTER);
		var footer = new JPanel(new BorderLayout(4, 4));
		status.setEditable(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		evidence.setEditable(false);
		evidence.setLineWrap(true);
		evidence.setWrapStyleWord(true);
		footer.add(new JScrollPane(evidence), BorderLayout.NORTH);
		footer.add(status, BorderLayout.CENTER);
		footer.add(close, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		close.addActionListener(event -> {
			if (!busy)
				closed.run();
		});
		tables.addActionListener(event -> clearSample());
		rowLimit.addChangeListener(event -> clearSample());
		preview.addActionListener(event -> loadSample());
		reload.addActionListener(event -> reloadPackage());
		validate.addActionListener(event -> validateValues());
		reloadPackage();
	}

	private void reloadPackage() {
		if (busy)
			return;
		var selected = (Choice) tables.getSelectedItem();
		String selectedTask = selected == null ? null : selected.table().taskId();
		clearSample();
		valueCheck = ValueCheck.NOT_CHECKED;
		snapshot = null;
		evidence.setText("");
		busy = true;
		tables.removeAllItems();
		refresh();
		status.setText("Checking package integrity; no database connection is opened…");
		new SwingWorker<TransferFilePackageIO.Snapshot, Void>() {
			@Override
			protected TransferFilePackageIO.Snapshot doInBackground() throws Exception {
				return inspector.inspect(input);
			}

			@Override
			protected void done() {
				try {
					snapshot = get();
					for (var table : snapshot.manifest().tables())
						tables.addItem(new Choice(table));
					for (int index = 0; index < tables.getItemCount(); index++)
						if (selected != null && tables.getItemAt(index).table().taskId().equals(selectedTask)
								&& java.util.Objects.equals(tables.getItemAt(index).table().catalog(),
										selected.table().catalog())
								&& java.util.Objects.equals(tables.getItemAt(index).table().schema(),
										selected.table().schema())
								&& tables.getItemAt(index).table().table().equals(selected.table().table()))
							tables.setSelectedIndex(index);
					refreshEvidence();
					status.setText(
							"Choose a table and Load sample. Hashes checked; full values have not been validated.");
				} catch (Exception failure) {
					status.setText("Could not inspect package: " + EditorStatusPanel.failureMessage(failure)
							+ " Fix the package selection or files, then Reload package.");
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private void refreshEvidence() {
		if (snapshot == null) {
			evidence.setText("");
			return;
		}
		evidence.setText(snapshot.file() + "\n" + snapshot.fingerprint() + "\nFormat: "
				+ snapshot.manifest().files().format() + "; encoding: " + snapshot.manifest().files().encoding()
				+ "; header: " + snapshot.manifest().files().headerMode() + "\nRecorded full value check: "
				+ switch (valueCheck) {
				case NOT_CHECKED -> "not run";
				case RUNNING -> "in progress";
				case PASSED -> "passed for this fingerprint";
				case FAILED -> "failed; fix the package and check again";
				} + ". Target Review and import confirmation remain separate.");
	}

	private void validateValues() {
		if (busy || snapshot == null)
			return;
		var captured = snapshot;
		long token = revision;
		valueCheck = ValueCheck.RUNNING;
		refreshEvidence();
		busy = true;
		refresh();
		status.setText("Validating all packaged rows, types, keys and counts; no database connection is opened…");
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				validator.validate(captured.file(), captured.fingerprint());
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					valueCheck = ValueCheck.PASSED;
					status.setText(
							"All packaged values checked for the recorded fingerprint. Target compatibility and import have not been approved.");
				} catch (Exception failure) {
					valueCheck = ValueCheck.FAILED;
					results.removeAll();
					sample = null;
					results.revalidate();
					results.repaint();
					status.setText("Full value check failed: " + EditorStatusPanel.failureMessage(failure)
							+ " Inspect file settings and warnings. Reload package after changing its files.");
				} finally {
					if (token != revision) {
						results.removeAll();
						sample = null;
						results.revalidate();
						results.repaint();
					}
					refreshEvidence();
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private void clearSample() {
		revision++;
		if (busy)
			return;
		results.removeAll();
		sample = null;
		results.revalidate();
		results.repaint();
		status.setText("Selection changed. Load a new sample; whole-package check status is recorded below.");
	}

	private void refresh() {
		tables.setEnabled(!busy && snapshot != null);
		rowLimit.setEnabled(!busy && snapshot != null);
		preview.setEnabled(!busy && snapshot != null && tables.getSelectedItem() != null);
		reload.setEnabled(!busy);
		validate.setEnabled(!busy && snapshot != null);
		if (sample != null)
			sample.refreshEnabled(!busy);
		close.setEnabled(!busy);
	}

	private void loadSample() {
		if (busy || snapshot == null || tables.getSelectedItem() == null)
			return;
		final int limit;
		try {
			rowLimit.commitEdit();
			limit = ((Number) rowLimit.getValue()).intValue();
		} catch (Exception failure) {
			status.setText("Choose a sample row limit from 1 to 100.");
			return;
		}
		var choice = (Choice) tables.getSelectedItem();
		clearSample();
		busy = true;
		refresh();
		long token = revision;
		status.setText("Reading bounded sample; checking package hashes may read all data files…");
		new SwingWorker<TransferFileImportService.Preview, Void>() {
			@Override
			protected TransferFileImportService.Preview doInBackground() throws Exception {
				return sampler.sample(snapshot.file(), snapshot.fingerprint(), choice.table().taskId(), limit);
			}

			@Override
			protected void done() {
				try {
					var value = get();
					if (token != revision) {
						status.setText("Selection changed while reading. Load a new sample.");
						return;
					}
					var columns = new java.util.ArrayList<String>();
					columns.add("Sample row");
					for (int index = 0; index < value.table().columns().size(); index++)
						columns.add(value.table().columns().get(index) + " / " + value.sourceTypes().get(index));
					var rows = new java.util.ArrayList<java.util.List<String>>();
					for (var row : value.rows()) {
						var fields = new java.util.ArrayList<String>();
						fields.add(Integer.toString(rows.size() + 1));
						row.forEach(cell -> fields.add(cell.toString()));
						rows.add(fields);
					}
					var model = new javax.swing.table.DefaultTableModel(
							rows.stream().map(java.util.List::toArray).toArray(Object[][]::new), columns.toArray()) {
						@Override
						public boolean isCellEditable(int row, int column) {
							return false;
						}
					};
					sample = new ReportDetailTablePanel(new JTable(model),
							"null is SQL NULL; quoted strings distinguish empty text and literal null. Long values are marked truncated. Only sampled values are checked; this is not target conversion or full row verification.",
							"Find sample", "sampled rows");
					sample.setNumericColumns(java.util.List.of(0));
					results.add(sample, BorderLayout.CENTER);
					results.revalidate();
					results.repaint();
					status.setText("Sampled " + rows.size() + " rows; recorded package total: " + value.table().rows()
							+ (value.moreRecordedRows() ? ". More recorded rows are not shown." : ".")
							+ " No import is approved.");
				} catch (Exception failure) {
					if (valueCheck == ValueCheck.PASSED)
						valueCheck = ValueCheck.NOT_CHECKED;
					refreshEvidence();
					status.setText("Could not preview data: " + EditorStatusPanel.failureMessage(failure)
							+ " Check file settings and package integrity; Reload package after changes.");
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}
}
