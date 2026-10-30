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
	final JTextField mappingFile = new JTextField(24);
	final JButton browseMapping = new JButton("Browse…");
	final JButton editMapping = new JButton("Edit names…");
	private final JPanel mappingRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
	final JCheckBox createMissing = new JCheckBox("Create missing target tables (the schema itself must exist)");
	final JCheckBox generatedKey = new JCheckBox(
			"Add target-only generated PK to new tables without a primary key (SQLAPP_ROW_ID)");
	final JCheckBox verify = new JCheckBox("Verify copied data");
	final JCheckBox schemaOnly = new JCheckBox("Schema only (no row copy)");
	final JCheckBox applyDifferences = new JCheckBox(
			"Apply differences to existing selected tables (review ALTER SQL)");
	final JCheckBox allowDestructive = new JCheckBox("Allow removals and changes that may affect existing data");
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
		var mappingLabel = new JLabel("Name mapping YAML (optional)");
		mappingLabel.setLabelFor(mappingFile);
		mappingRow.add(mappingLabel);
		mappingRow.add(mappingFile);
		mappingRow.add(browseMapping);
		mappingRow.add(editMapping);
		editMapping.addActionListener(event -> {
			if (busy || snapshot == null)
				return;
			try {
				var job = snapshot.file();
				var mapping = mappingFile.getText().isBlank() ? null
						: job.getParent().resolve(mappingFile.getText()).normalize();
				var edited = TransferNameMappingDialog.show(this, job, mapping, snapshot.configuration().tables());
				if (edited != null) {
					mappingFile.setText(TransferMappingPaths.fromJob(job, edited));
					status.setText(
							"Mapping saved. Save job settings to attach it, then review again before execution.");
				}
			} catch (Exception failure) {
				status.setText(message(failure));
			}
		});
		fields.add(mappingRow);
		FileDropHandler.path(mappingFile, false, java.util.List.of("yaml", "yml"), () -> mappingFile.isEnabled(),
				failure -> status.setText(failure.getMessage()));
		mappingFile.setToolTipText(
				"Choose or drop one YAML file. Relative paths use the saved job folder; unspecified names stay unchanged.");
		browseMapping.addActionListener(event -> {
			var chooser = new EditorFileChooser().forFile(
					new javax.swing.filechooser.FileNameExtensionFilter("YAML mapping", "yaml", "yml"),
					snapshot == null ? mappingFile.getText()
							: TransferMappingPaths.browseHint(snapshot.file(), mappingFile.getText()));
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
				mappingFile.setText(chooser.getSelectedFile().getAbsolutePath());
		});
		if (mode != TransferJobConfiguration.DataMode.FILE)
			fields.add(schemaOnly);
		schemaOnly.addActionListener(event -> {
			if (!schemaOnly.isSelected())
				allowDestructive.setSelected(false);
			if (schemaOnly.isSelected()) {
				createMissing.setSelected(true);
				verify.setSelected(false);
			}
			refresh();
		});
		fields.add(createMissing);
		fields.add(generatedKey);
		createMissing.addActionListener(event -> {
			if (!createMissing.isSelected())
				generatedKey.setSelected(false);
			refresh();
		});
		fields.add(applyDifferences);
		fields.add(allowDestructive);
		applyDifferences.addActionListener(event -> {
			if (applyDifferences.isSelected())
				generatedKey.setSelected(false);
			if (!applyDifferences.isSelected())
				allowDestructive.setSelected(false);
			refresh();
		});
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
				if (loaded.configuration().dataMode() != mode && !(mode == TransferJobConfiguration.DataMode.JDBC_DIRECT
						&& loaded.configuration().dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY))
					throw new IllegalArgumentException("Choose a saved job for this transfer route.");
				return loaded;
			}

			@Override
			protected void done() {
				try {
					snapshot = get();
					final var configuration = snapshot.configuration();
					schemaOnly.setSelected(configuration.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY);
					applyDifferences.setSelected(
							configuration.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES);
					allowDestructive.setSelected(configuration.allowDestructiveSchemaChanges());
					targetSchema.setText(configuration.targetSchema() == null ? "" : configuration.targetSchema());
					mappingFile.setText(configuration.mappingFile() == null ? "" : configuration.mappingFile());
					createMissing.setSelected(
							configuration.schemaMode() != TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING);
					generatedKey.setSelected(configuration
							.schemaMode() == TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY);
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
					schemaOnly.isSelected() && applyDifferences.isSelected()
							? TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
							: schemaOnly.isSelected() || createMissing.isSelected()
									? generatedKey.isSelected()
											? TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY
											: TransferJobConfiguration.SchemaMode.CREATE_MISSING
									: TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING,
					original.sourceSchema(), targetSchema.getText().isBlank() ? null : targetSchema.getText(),
					mappingFile.getText().isBlank() ? null : mappingFile.getText(),
					new java.math.BigDecimal(chunk.getValue().toString()).intValueExact(),
					!schemaOnly.isSelected() && verify.isSelected(),
					mode == TransferJobConfiguration.DataMode.FILE ? mode
							: schemaOnly.isSelected() ? TransferJobConfiguration.DataMode.SCHEMA_ONLY
									: TransferJobConfiguration.DataMode.JDBC_DIRECT,
					original.files(),
					strict.isSelected() ? TransferJobConfiguration.CompatibilityPolicy.ERROR
							: TransferJobConfiguration.CompatibilityPolicy.WARN,
					schemaOnly.isSelected() && applyDifferences.isSelected() && allowDestructive.isSelected());
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
		mappingRow.setVisible(mode != TransferJobConfiguration.DataMode.FILE || !mappingFile.getText().isBlank());
		mappingFile.setEnabled(!busy && snapshot != null);
		browseMapping.setEnabled(mappingFile.isEnabled());
		editMapping.setEnabled(mappingFile.isEnabled() && mode != TransferJobConfiguration.DataMode.FILE);
		for (var field : new java.awt.Component[] { targetSchema, createMissing, verify, strict, chunk })
			field.setEnabled(!busy && snapshot != null);
		schemaOnly.setEnabled(!busy && snapshot != null);
		applyDifferences.setVisible(mode != TransferJobConfiguration.DataMode.FILE && schemaOnly.isSelected());
		applyDifferences.setEnabled(!busy && snapshot != null && schemaOnly.isSelected());
		allowDestructive.setVisible(applyDifferences.isVisible() && applyDifferences.isSelected());
		allowDestructive.setEnabled(applyDifferences.isEnabled() && applyDifferences.isSelected());
		generatedKey.setVisible(mode != TransferJobConfiguration.DataMode.FILE && createMissing.isSelected()
				&& !(schemaOnly.isSelected() && applyDifferences.isSelected()));
		generatedKey.setEnabled(!busy && snapshot != null && generatedKey.isVisible());
		if (schemaOnly.isSelected()) {
			createMissing.setEnabled(false);
			verify.setEnabled(false);
			chunk.setEnabled(false);
		}
		save.setEnabled(!busy && snapshot != null);
		cancel.setEnabled(!busy);
	}
}
