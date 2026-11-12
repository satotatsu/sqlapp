/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.FlowLayout;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;

/** Saves a captured view separately from the source report. */
final class ReportTableCsvSaveActions extends JPanel {
	private static final long serialVersionUID = 1L;
	final JButton save = new JButton("Save visible CSV…");
	final JButton openFolder = new JButton("Open saved folder");
	final JLabel status = new JLabel();
	private final JTable table;
	private final BooleanSupplier enabled;
	private final EditorFileChooser files = new EditorFileChooser();
	private boolean saving;
	private boolean opening;
	private Path savedOutput;
	private final FolderOpener opener;

	@FunctionalInterface
	interface FolderOpener {
		void open(Path folder) throws Exception;
	}

	ReportTableCsvSaveActions(JTable table, BooleanSupplier enabled) {
		this(table, enabled, folder -> {
			if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
				throw new IOException("Opening folders is unavailable.");
			Desktop.getDesktop().open(folder.toFile());
		});
	}

	ReportTableCsvSaveActions(JTable table, BooleanSupplier enabled, FolderOpener opener) {
		super(new FlowLayout(FlowLayout.LEFT, 4, 0));
		this.table = table;
		this.enabled = enabled;
		this.opener = java.util.Objects.requireNonNull(opener);
		add(save);
		add(openFolder);
		add(status);
		openFolder.addActionListener(event -> openSavedFolder());
		save.setToolTipText("Save visible report rows with headers in current row/column order as UTF-8 CSV, up to 16 MiB.");
		save.addActionListener(event -> {
			if (!save.isEnabled()) return;
			var chooser = files.forDirectory(null);
			chooser.setDialogTitle("Choose a folder for the report CSV snapshot");
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
				saveTo(chooser.getSelectedFile().toPath());
		});
		refresh();
	}

	void refresh() {
		save.setEnabled(!saving && !opening && enabled.getAsBoolean() && table.getRowCount() > 0 && table.getColumnCount() > 0);
		openFolder.setEnabled(!saving && !opening && enabled.getAsBoolean() && savedOutput != null);
		openFolder.setToolTipText(savedOutput == null ? "Save a CSV snapshot first."
				: "Open the last successfully saved CSV folder: " + savedOutput.getParent());
	}

	void saveTo(Path folder) {
		if (!save.isEnabled()) return;
		final String snapshot;
		try {
			snapshot = ReportTableCsv.format(table);
		} catch (IllegalArgumentException failure) {
			status.setText(failure.getMessage());
			return;
		}
		saving = true;
		refresh();
		status.setToolTipText(null);
		status.setText("Saving CSV snapshot…");
		new SwingWorker<Path, Void>() {
			@Override protected Path doInBackground() throws Exception {
				var directory = Files.createTempDirectory(Files.createDirectories(folder.toAbsolutePath().normalize()), "report-table-");
				var output = directory.resolve("visible-rows.csv");
				AtomicMigrationFile.write(output, temporary -> Files.writeString(temporary, snapshot, StandardCharsets.UTF_8));
				return output;
			}

			@Override protected void done() {
				try {
					var output = get();
					savedOutput = output;
					files.rememberDirectory(folder);
					status.setToolTipText(output.toString());
					status.setText("Saved CSV snapshot (hover for path)");
				} catch (Exception failure) {
					status.setToolTipText(savedOutput == null ? null : savedOutput.toString());
					status.setText("Could not save CSV. Choose a writable folder, check disk space and try again.");
				} finally {
					saving = false;
					refresh();
				}
			}
		}.execute();
	}

	private void openSavedFolder() {
		if (saving || opening || !enabled.getAsBoolean() || savedOutput == null) return;
		var output = savedOutput;
		opening = true;
		refresh();
		status.setText("Opening saved CSV folder…");
		new SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() throws Exception {
				if (!Files.isDirectory(output.getParent()))
					throw new IOException("Saved CSV folder is unavailable.");
				opener.open(output.getParent());
				return null;
			}

			@Override protected void done() {
				try {
					get();
					status.setText("Opened saved CSV folder");
				} catch (Exception failure) {
					status.setText("Could not open folder. Use the saved path (hover here), or try again.");
				} finally {
					status.setToolTipText(output.toString());
					opening = false;
					refresh();
				}
			}
		}.execute();
	}
}
