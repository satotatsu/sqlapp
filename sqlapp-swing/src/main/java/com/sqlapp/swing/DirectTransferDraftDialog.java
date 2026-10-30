/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.*;

final class DirectTransferDraftDialog {
	static Path show(Component owner, DataMigrationDraftService.Prepared prepared) {
		final var saved = new AtomicReference<Path>();
		final var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Create direct JDBC job",
				Dialog.ModalityType.APPLICATION_MODAL);
		final var strict = new JCheckBox("Treat compatibility warnings as errors (default: try and report warnings)");
		final var createMissing = new JCheckBox(
				"Create missing target tables before copying (review generated DDL first)");
		final var generatedKey = new JCheckBox("Add target-only generated PK to keyless new tables");
		final var generatedKeyColumn = new JTextField("ID", 20);
		final var generatedKeyRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		var generatedKeyLabel = new JLabel("Generated PK column (default: ID)");
		generatedKeyLabel.setLabelFor(generatedKeyColumn);
		generatedKeyRow.add(generatedKeyLabel);
		generatedKeyRow.add(generatedKeyColumn);
		generatedKeyRow.setVisible(false);
		generatedKey.setVisible(false);
		final var targetSchema = new JTextField(20);
		final var schemaOnly = new JCheckBox("Schema only — create tables, constraints and indexes; do not copy rows");
		final var applyDifferences = new JCheckBox("Apply differences to existing selected tables (review ALTER SQL)");
		final var allowDestructive = new JCheckBox("Allow removals and changes that may affect existing data");
		applyDifferences.setVisible(false);
		allowDestructive.setVisible(false);
		final var picker = JobDraftPanel.fileTransfer(prepared.schema(), () -> {
			final var policy = strict.isSelected() ? TransferJobConfiguration.CompatibilityPolicy.ERROR
					: TransferJobConfiguration.CompatibilityPolicy.WARN;
			final String schema = targetSchema.getText().isBlank() ? null : targetSchema.getText();
			final var mode = createMissing.isSelected()
					? generatedKey.isSelected() ? TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY
							: TransferJobConfiguration.SchemaMode.CREATE_MISSING
					: TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING;
			final boolean differences = schemaOnly.isSelected() && applyDifferences.isSelected();
			final var schemaMode = differences ? TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES : mode;
			final boolean destructive = differences && allowDestructive.isSelected();
			final String keyColumn = schemaMode == TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY
					? generatedKeyColumn.getText()
					: null;
			if (schemaOnly.isSelected())
				return (snapshot, tables, chunk,
						verify) -> new TransferJobDraftBuilder().saveSchemaOnly(snapshot, tables, policy, schema,
								schemaMode, destructive, prepared.directory().resolve("transfer.yaml"), keyColumn)
								.file().toString();
			return (snapshot, tables, chunk, verify) -> new TransferJobDraftBuilder().saveDirect(snapshot, tables,
					chunk, verify, policy, schema, mode, prepared.directory().resolve("transfer.yaml"), keyColumn)
					.file().toString();
		}, file -> {
			saved.set(file);
			dialog.dispose();
		}, dialog::dispose);
		picker.create.setText("Save direct transfer job");
		picker.fullScanInsert(true);
		generatedKey.addActionListener(event -> {
			generatedKeyRow.setVisible(generatedKey.isVisible() && generatedKey.isSelected());
			picker.draftOptionsChanged();
			dialog.revalidate();
		});
		generatedKeyColumn.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent event) {
				picker.draftOptionsChanged();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent event) {
				picker.draftOptionsChanged();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent event) {
				picker.draftOptionsChanged();
			}
		});
		createMissing.addActionListener(event -> {
			if (!createMissing.isSelected())
				generatedKey.setSelected(false);
			generatedKey.setVisible(createMissing.isSelected() && !applyDifferences.isSelected());
			generatedKeyRow.setVisible(generatedKey.isVisible() && generatedKey.isSelected());
			picker.draftOptionsChanged();
			dialog.revalidate();
		});
		picker.offerSchemaOnly(() -> schemaOnly.doClick(0));
		picker.status
				.setText("Select tables. Existing targets must be empty; saving never creates tables or copies rows.");
		strict.addActionListener(event -> picker.draftOptionsChanged());
		createMissing.addActionListener(event -> picker.draftOptionsChanged());
		applyDifferences.addActionListener(event -> {
			if (applyDifferences.isSelected())
				generatedKey.setSelected(false);
			generatedKey.setVisible(createMissing.isSelected() && !applyDifferences.isSelected());
			generatedKeyRow.setVisible(generatedKey.isVisible() && generatedKey.isSelected());
			if (!applyDifferences.isSelected())
				allowDestructive.setSelected(false);
			allowDestructive.setVisible(schemaOnly.isSelected() && applyDifferences.isSelected());
			picker.draftOptionsChanged();
			picker.status.setText(applyDifferences.isSelected()
					? "Schema differences: inspect generated ALTER/DROP SQL and any data-impact warnings before execution."
					: "Schema only: existing target tables are skipped with warnings; no rows are copied.");
			dialog.revalidate();
		});
		allowDestructive.addActionListener(event -> picker.draftOptionsChanged());
		schemaOnly.addActionListener(event -> {
			if (schemaOnly.isSelected())
				createMissing.setSelected(true);
			picker.schemaOnly(schemaOnly.isSelected());
			generatedKey.setVisible(
					createMissing.isSelected() && !(schemaOnly.isSelected() && applyDifferences.isSelected()));
			createMissing.setEnabled(!schemaOnly.isSelected());
			generatedKeyRow.setVisible(generatedKey.isVisible() && generatedKey.isSelected());
			applyDifferences.setVisible(schemaOnly.isSelected());
			allowDestructive.setVisible(schemaOnly.isSelected() && applyDifferences.isSelected());
			if (!schemaOnly.isSelected())
				allowDestructive.setSelected(false);
			picker.status.setText(schemaOnly.isSelected()
					? "Schema only: existing target tables are skipped with warnings. No rows are copied. The target schema must exist."
					: "Select tables to copy; existing targets must be empty.");
		});
		targetSchema.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				picker.draftOptionsChanged();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				picker.draftOptionsChanged();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				picker.draftOptionsChanged();
			}
		});
		picker.inspect.addPropertyChangeListener("enabled", event -> {
			strict.setEnabled(!picker.isBusy());
			targetSchema.setEnabled(!picker.isBusy());
			createMissing.setEnabled(!picker.isBusy() && !schemaOnly.isSelected());
			generatedKey.setEnabled(!picker.isBusy());
			generatedKeyColumn.setEnabled(!picker.isBusy());
			schemaOnly.setEnabled(!picker.isBusy());
			applyDifferences.setEnabled(!picker.isBusy());
			allowDestructive.setEnabled(!picker.isBusy());
		});
		final var heading = new JPanel(new GridLayout(0, 1, 4, 4));
		heading.add(new JLabel(
				"Save one job for this GUI and Gradle transferDatabase. Connection credentials remain runtime inputs."));
		final var row = new JPanel(new FlowLayout(FlowLayout.LEFT));
		final var label = new JLabel("Target schema (blank: current)");
		label.setLabelFor(targetSchema);
		row.add(label);
		row.add(targetSchema);
		heading.add(row);
		heading.add(schemaOnly);
		heading.add(applyDifferences);
		heading.add(allowDestructive);
		heading.add(createMissing);
		heading.add(generatedKey);
		heading.add(generatedKeyRow);
		heading.add(strict);
		final var path = new JTextField(prepared.directory().resolve("transfer.yaml").toString());
		path.setEditable(false);
		heading.add(path);
		final var content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		content.add(heading, BorderLayout.NORTH);
		content.add(picker, BorderLayout.CENTER);
		dialog.setContentPane(content);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				if (!picker.isBusy())
					dialog.dispose();
			}
		});
		dialog.setSize(1050, 650);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return saved.get();
	}
}
