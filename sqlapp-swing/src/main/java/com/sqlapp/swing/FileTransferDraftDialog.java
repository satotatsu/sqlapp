/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferFileJobDraftBuilder;

/** Reuses the existing table picker; saving never exports or imports rows. */
final class FileTransferDraftDialog {
	static Path show(java.awt.Component owner, DataMigrationDraftService.Prepared prepared) {
		final var selected = new AtomicReference<Path>();
		final var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Create CSV transfer job",
				java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		final var nullValue = new JTextField("__SQLAPP_NULL__", 20);
		final var createMissing = new JCheckBox(
				"Create missing target tables before importing (review generated DDL first)");
		final var temporalErrors = new JCheckBox(
				"Treat compatibility warnings as errors (default: try migration and report warnings)");
		final var panel = JobDraftPanel.fileTransfer(prepared.schema(), () -> {
			final String token = nullValue.getText(); // Capture Swing input before the background writer starts.
			final var mode = createMissing.isSelected()
					? com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.SchemaMode.CREATE_MISSING
					: com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING;
			final var policy = temporalErrors.isSelected()
					? com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.TemporalPolicy.ERROR
					: com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.TemporalPolicy.WARN;
			final var compatibilityPolicy = temporalErrors.isSelected()
					? com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.CompatibilityPolicy.ERROR
					: com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.CompatibilityPolicy.WARN;
			return (schema, tables, chunk,
					verify) -> new TransferFileJobDraftBuilder().save(schema, tables, chunk, verify, token, mode,
							policy, compatibilityPolicy, prepared.directory().resolve("transfer.yaml")).file()
							.toString();
		}, path -> {
			selected.set(path);
			dialog.dispose();
		}, dialog::dispose);
		nullValue.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent event) {
				panel.draftOptionsChanged();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent event) {
				panel.draftOptionsChanged();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent event) {
				panel.draftOptionsChanged();
			}
		});
		createMissing.addActionListener(event -> panel.draftOptionsChanged());
		temporalErrors.addActionListener(event -> panel.draftOptionsChanged());
		panel.inspect.addPropertyChangeListener("enabled", event -> {
			nullValue.setEnabled(!panel.isBusy());
			createMissing.setEnabled(!panel.isBusy());
			temporalErrors.setEnabled(!panel.isBusy());
		});
		final var content = new JPanel(new BorderLayout(8, 8));
		final var heading = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
		heading.add(new JLabel(
				"Save a shared GUI / Gradle job. Existing targets must be empty; saving never changes a database."));
		heading.add(createMissing);
		heading.add(temporalErrors);
		final var tokenRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		final var label = new JLabel("CSV NULL token (export stops if a real value matches)");
		label.setLabelFor(nullValue);
		tokenRow.add(label);
		tokenRow.add(nullValue);
		heading.add(tokenRow);
		final var destination = new JTextField(prepared.directory().resolve("transfer.yaml").toString());
		destination.setEditable(false);
		heading.add(destination);
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		content.add(heading, BorderLayout.NORTH);
		content.add(panel, BorderLayout.CENTER);
		dialog.setContentPane(content);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (!panel.isBusy())
					dialog.dispose();
			}
		});
		dialog.setSize(1050, 650);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return selected.get();
	}
}
