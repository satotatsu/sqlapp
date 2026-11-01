/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.transfer.*;

/** Offline selection editor; only the parent settings form may save the job. */
final class TransferTableSelectionPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField schemaFile = new JTextField();
	final JButton read = new JButton("Read Schema");
	final JButton browse = new JButton("Browse…");
	final JButton cancel = new JButton("Cancel");
	final JLabel status = new JLabel("Read the captured source Schema. No database connection or file write.");
	final JPanel body = new JPanel(new BorderLayout());
	JobDraftPanel selector;
	private boolean reading;
	private long revision;
	private List<String> pending;
	private final TransferJobConfiguration.DataMode mode;
	private final Consumer<List<String>> chosen;

	TransferTableSelectionPanel(Path job, List<String> initial, TransferJobConfiguration.DataMode mode,
			Consumer<List<String>> chosen, Runnable cancelled) {
		super(new BorderLayout(8, 8));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		this.pending = List.copyOf(initial);
		this.mode = mode;
		this.chosen = chosen;
		schemaFile.setText(job.toAbsolutePath().getParent().resolve("source-schema.xml").toString());
		var top = new JPanel(new BorderLayout(4, 4));
		var label = new JLabel("Captured source Schema XML");
		label.setLabelFor(schemaFile);
		top.add(label, BorderLayout.NORTH);
		top.add(schemaFile, BorderLayout.CENTER);
		var files = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		files.add(browse);
		files.add(read);
		top.add(files, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		var bottom = new JPanel(new BorderLayout());
		bottom.add(status, BorderLayout.CENTER);
		bottom.add(cancel, BorderLayout.EAST);
		add(bottom, BorderLayout.SOUTH);
		FileDropHandler.path(schemaFile, false, List.of("xml"), () -> !isBusy(),
				failure -> status.setText(failure.getMessage()));
		schemaFile.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
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
		browse.addActionListener(event -> {
			var chooser = new EditorFileChooser().forFile(
					new javax.swing.filechooser.FileNameExtensionFilter("Schema XML", "xml"), schemaFile.getText());
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				schemaFile.setText(chooser.getSelectedFile().getAbsolutePath());
		});
		read.addActionListener(event -> load());
		cancel.addActionListener(event -> {
			if (!isBusy())
				cancelled.run();
		});
		body.add(new JLabel("Read Schema to edit the saved table selection."));
	}

	boolean isBusy() {
		return reading || selector != null && selector.isBusy();
	}

	private void changed() {
		revision++;
		if (selector != null)
			pending = selector.selectedTables();
		selector = null;
		body.removeAll();
		body.add(new JLabel("Schema path changed. Read Schema again; the job has not changed."));
		body.revalidate();
		body.repaint();
	}

	private void refresh() {
		boolean enabled = !isBusy();
		read.setEnabled(enabled);
		browse.setEnabled(enabled);
		schemaFile.setEnabled(enabled);
		cancel.setEnabled(enabled);
	}

	void load() {
		if (isBusy())
			return;
		final Path file;
		try {
			if (schemaFile.getText().isBlank())
				throw new IllegalArgumentException("Choose a source Schema XML.");
			file = Path.of(schemaFile.getText());
		} catch (RuntimeException failure) {
			status.setText(failure.getMessage());
			return;
		}
		if (selector != null)
			pending = selector.selectedTables();
		var names = pending;
		long token = revision;
		selector = null;
		reading = true;
		refresh();
		body.removeAll();
		status.setText("Reading captured Schema and resolving the previous selection…");
		new SwingWorker<Loaded, Void>() {
			@Override
			protected Loaded doInBackground() throws Exception {
				var snapshot = new BulkMigrationJobDraftBuilder().inspect(file);
				if (mode == TransferJobConfiguration.DataMode.FILE)
					snapshot = new TransferFileJobDraftBuilder().inspect(snapshot);
				var resolved = names.isEmpty() ? List.<String>of()
						: new TransferJobDraftBuilder().resolveTables(snapshot, names, mode);
				return new Loaded(snapshot, resolved);
			}

			@Override
			protected void done() {
				try {
					var loaded = get();
					if (token != revision) {
						status.setText("Inputs changed. Read Schema again.");
						return;
					}
					selector = JobDraftPanel.tableSelection(loaded.schema(), loaded.selected(), mode, value -> {
						if (token == revision)
							chosen.accept(value);
						else
							status.setText("Inputs changed. Read Schema again before using the selection.");
					});
					selector.inspect.addPropertyChangeListener("enabled", event -> refresh());
					body.add(selector, BorderLayout.CENTER);
					status.setText(
							"Use selected tables returns to settings. Save settings and review again; CSV jobs require a new export.");
				} catch (Exception failure) {
					var cause = failure instanceof java.util.concurrent.ExecutionException ? failure.getCause()
							: failure;
					status.setText(cause.getMessage() == null ? "Unable to read source Schema." : cause.getMessage());
					body.add(new JLabel(
							"Selection was not changed. Choose a Schema containing the saved selection and read again."));
				} finally {
					reading = false;
					refresh();
					body.revalidate();
					body.repaint();
				}
			}
		}.execute();
	}

	private record Loaded(BulkMigrationJobDraftBuilder.SchemaSnapshot schema, List<String> selected) {
	}
}
