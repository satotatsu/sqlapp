/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.*;
import java.util.function.Consumer;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.*;

/**
 * Offline form over the shared immutable configuration and atomic, pinned IO.
 */
final class TransferJobSettingsPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField targetSchema = new JTextField(24);
	final JCheckBox createMissing = new JCheckBox("Create missing target tables (the schema itself must exist)");
	final JCheckBox verify = new JCheckBox("Verify copied data");
	final JCheckBox strict = new JCheckBox("Treat compatibility warnings as errors");
	final JSpinner chunk = new JSpinner(new SpinnerNumberModel(10000, 1, 1000000, 1));
	final JButton save = new JButton("Save settings");
	final JButton cancel = new JButton("Cancel");
	final JLabel status = new JLabel("Loading saved job…");
	final JLabel identity = new JLabel(" ");
	private final JPanel fields = new JPanel(new GridLayout(0, 1, 4, 4));
	private final TransferJobConfigurationIO io = new TransferJobConfigurationIO();
	private final TransferJobConfiguration.DataMode mode;
	private final Consumer<TransferJobConfigurationIO.Snapshot> saved;
	private TransferJobConfigurationIO.Snapshot snapshot;
	private boolean busy;

	TransferJobSettingsPanel(TransferJobConfiguration.DataMode mode,
			Consumer<TransferJobConfigurationIO.Snapshot> saved, Runnable cancelled) {
		super(new BorderLayout(8, 8));
		this.mode = mode;
		this.saved = saved;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		add(identity, BorderLayout.NORTH);
		final var schemaRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		final var label = new JLabel("Target schema (blank: current)");
		label.setLabelFor(targetSchema);
		schemaRow.add(label);
		schemaRow.add(targetSchema);
		fields.add(schemaRow);
		fields.add(createMissing);
		fields.add(verify);
		fields.add(strict);
		final var chunkRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		final var chunkLabel = new JLabel("Rows per chunk");
		chunkLabel.setLabelFor(chunk);
		chunkRow.add(chunkLabel);
		chunkRow.add(chunk);
		fields.add(chunkRow);
		fields.add(new JLabel("Saving changes only the job, never a database or completed package."));
		fields.add(new JLabel("YAML formatting/comments are rewritten; all other settings are retained."));
		fields.add(new JLabel(mode == TransferJobConfiguration.DataMode.FILE
				? "After saving, export a new package; existing packages keep their original settings."
				: "After saving, review again in the GUI or Gradle before execution."));
		add(fields, BorderLayout.CENTER);
		final var footer = new JPanel(new BorderLayout(4, 4));
		footer.add(status, BorderLayout.NORTH);
		final var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(save);
		actions.add(cancel);
		footer.add(actions, BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		save.addActionListener(event -> save());
		cancel.addActionListener(event -> {
			if (!busy)
				cancelled.run();
		});
		refresh();
	}

	boolean isBusy() {
		return busy;
	}

	void load(Path file) {
		if (busy)
			return;
		snapshot = null;
		busy = true;
		refresh();
		new SwingWorker<TransferJobConfigurationIO.Snapshot, Void>() {
			@Override
			protected TransferJobConfigurationIO.Snapshot doInBackground() throws Exception {
				checkEditable(file);
				final var loaded = io.read(file);
				if (loaded.configuration().dataMode() != mode)
					throw new IllegalArgumentException("Choose a saved job for this transfer route.");
				return loaded;
			}

			@Override
			protected void done() {
				try {
					snapshot = get();
					final var configuration = snapshot.configuration();
					targetSchema.setText(configuration.targetSchema() == null ? "" : configuration.targetSchema());
					createMissing.setSelected(
							configuration.schemaMode() == TransferJobConfiguration.SchemaMode.CREATE_MISSING);
					verify.setSelected(configuration.verify());
					strict.setSelected(
							configuration.compatibilityPolicy() == TransferJobConfiguration.CompatibilityPolicy.ERROR);
					chunk.setValue(configuration.chunkSize());
					identity.setText(snapshot.file() + " — " + configuration.tables().size() + " selected tables");
					status.setText("Adjust settings and save, or cancel to keep the original job.");
				} catch (Exception failure) {
					status.setText(message(failure));
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private void save() {
		if (busy || snapshot == null)
			return;
		final TransferJobConfiguration updated;
		try {
			chunk.commitEdit();
			final var original = snapshot.configuration();
			updated = new TransferJobConfiguration(original.formatVersion(), original.source(), original.target(),
					original.tables(),
					createMissing.isSelected() ? TransferJobConfiguration.SchemaMode.CREATE_MISSING
							: TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING,
					original.sourceSchema(), targetSchema.getText().isBlank() ? null : targetSchema.getText(),
					original.mappingFile(), new java.math.BigDecimal(chunk.getValue().toString()).intValueExact(),
					verify.isSelected(), original.dataMode(), original.files(),
					strict.isSelected() ? TransferJobConfiguration.CompatibilityPolicy.ERROR
							: TransferJobConfiguration.CompatibilityPolicy.WARN);
		} catch (Exception failure) {
			status.setText("Check chunk size (1..1000000) and target schema settings before saving.");
			return;
		}
		final var opened = snapshot;
		busy = true;
		refresh();
		status.setText("Saving checked job settings…");
		new SwingWorker<TransferJobConfigurationIO.Snapshot, Void>() {
			@Override
			protected TransferJobConfigurationIO.Snapshot doInBackground() throws Exception {
				checkEditable(opened.file());
				return io.write(opened.file(), io.format(updated), opened.fingerprint());
			}

			@Override
			protected void done() {
				try {
					snapshot = get();
					status.setText("Settings saved. Review again before execution; CSV jobs require a new export.");
					saved.accept(snapshot);
				} catch (Exception failure) {
					status.setText(message(failure));
				} finally {
					busy = false;
					refresh();
				}
			}
		}.execute();
	}

	private static void checkEditable(Path file) throws java.io.IOException {
		final var parent = file.toAbsolutePath().normalize().getParent();
		if (parent != null && Files.exists(parent.resolve("manifest.json"))
				&& Files.exists(parent.resolve("source-schema.xml")))
			throw new java.io.IOException(
					"Completed packages cannot be edited. Open the original job and export a new package.");
	}

	private static String message(Exception failure) {
		final var cause = failure instanceof java.util.concurrent.ExecutionException ? failure.getCause() : failure;
		if ((cause instanceof java.io.IOException || cause instanceof IllegalArgumentException)
				&& cause.getMessage() != null && !cause.getMessage().isBlank())
			return cause.getMessage();
		return "Unable to load or save settings. Check the job and file permissions; no database was changed.";
	}

	private void refresh() {
		for (var field : new java.awt.Component[] { targetSchema, createMissing, verify, strict, chunk })
			field.setEnabled(!busy && snapshot != null);
		save.setEnabled(!busy && snapshot != null);
		cancel.setEnabled(!busy);
	}
}
