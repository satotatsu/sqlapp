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
		final var targetSchema = new JTextField(20);
		final var schemaOnly = new JCheckBox("Schema only — create tables, constraints and indexes; do not copy rows");
		final var picker = JobDraftPanel.fileTransfer(prepared.schema(), () -> {
			final var policy = strict.isSelected() ? TransferJobConfiguration.CompatibilityPolicy.ERROR
					: TransferJobConfiguration.CompatibilityPolicy.WARN;
			final String schema = targetSchema.getText().isBlank() ? null : targetSchema.getText();
			final var mode = createMissing.isSelected() ? TransferJobConfiguration.SchemaMode.CREATE_MISSING
					: TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING;
			if (schemaOnly.isSelected())
				return (snapshot, tables, chunk, verify) -> new TransferJobDraftBuilder()
						.saveSchemaOnly(snapshot, tables, policy, schema, prepared.directory().resolve("transfer.yaml"))
						.file().toString();
			return (snapshot, tables, chunk, verify) -> new TransferJobDraftBuilder().saveDirect(snapshot, tables,
					chunk, verify, policy, schema, mode, prepared.directory().resolve("transfer.yaml")).file()
					.toString();
		}, file -> {
			saved.set(file);
			dialog.dispose();
		}, dialog::dispose);
		picker.create.setText("Save direct transfer job");
		picker.status
				.setText("Select tables. Existing targets must be empty; saving never creates tables or copies rows.");
		strict.addActionListener(event -> picker.draftOptionsChanged());
		createMissing.addActionListener(event -> picker.draftOptionsChanged());
		schemaOnly.addActionListener(event -> {
			if (schemaOnly.isSelected())
				createMissing.setSelected(true);
			picker.schemaOnly(schemaOnly.isSelected());
			createMissing.setEnabled(!schemaOnly.isSelected());
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
			schemaOnly.setEnabled(!picker.isBusy());
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
		heading.add(createMissing);
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
