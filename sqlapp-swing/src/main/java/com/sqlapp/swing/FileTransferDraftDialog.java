/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferFileJobDraftBuilder;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.FileFormat;

/** Reuses the existing table picker; saving never exports or imports rows. */
final class FileTransferDraftDialog {
	record Form(JPanel content, JobDraftPanel picker, JTextField targetSchema, JComboBox<FileFormat> format,
			JTextField nullValue, FileTransferOptionsPanel fileOptions, JCheckBox showImportSettings,
			JPanel importSettings, JCheckBox createMissing, JCheckBox generatedKey, JTextField generatedKeyColumn) {
	}

	static Path show(java.awt.Component owner, DataMigrationDraftService.Prepared prepared) {
		final var selected = new AtomicReference<Path>();
		final var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Create file transfer job",
				java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		final var form = form(prepared, path -> {
			selected.set(path);
			dialog.dispose();
		}, dialog::dispose);
		dialog.setContentPane(form.content());
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (!form.picker().isBusy())
					dialog.dispose();
			}
		});
		dialog.setSize(1050, 650);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return selected.get();
	}

	static Form form(DataMigrationDraftService.Prepared prepared, java.util.function.Consumer<Path> saved,
			Runnable cancelled) {
		final var format = new JComboBox<>(FileFormat.values());
		final var fileOptions = new FileTransferOptionsPanel();
		final var nullValue = fileOptions.nullValue;
		final var targetSchema = new JTextField(20);
		final var createMissing = new JCheckBox(
				"Create missing target tables before importing (review generated DDL first)");
		final var generatedKey = new JCheckBox("Add target-only generated PK to new tables without a primary key");
		final var generatedKeyColumn = new JTextField("ID", 20);
		final var generatedKeyRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		var generatedKeyLabel = new JLabel("Generated PK column (default: ID)");
		generatedKeyLabel.setLabelFor(generatedKeyColumn);
		generatedKeyRow.add(generatedKeyLabel);
		generatedKeyRow.add(generatedKeyColumn);
		final var showImportSettings = new JCheckBox("Optional import settings (no target access during Export)");
		final var importSettings = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
		importSettings.setVisible(false);
		final var temporalErrors = new JCheckBox(
				"Treat compatibility warnings as errors (default: try migration and report warnings)");
		final var panel = JobDraftPanel.fileTransfer(prepared.schema(), () -> {
			final var selectedFormat = (FileFormat) format.getSelectedItem();
			final String destinationSchema = targetSchema.getText().isBlank() ? null : targetSchema.getText();
			final var mode = createMissing.isSelected() ? generatedKey.isSelected()
					? com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.SchemaMode.CREATE_MISSING_WITH_IDENTITY
					: com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.SchemaMode.CREATE_MISSING
					: com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING;
			final var policy = temporalErrors.isSelected()
					? com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.TemporalPolicy.ERROR
					: com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.TemporalPolicy.WARN;
			final var compatibilityPolicy = temporalErrors.isSelected()
					? com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.CompatibilityPolicy.ERROR
					: com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.CompatibilityPolicy.WARN;
			final var files = fileOptions.settings("packages", selectedFormat, policy);
			final String keyColumn = createMissing.isSelected() && generatedKey.isSelected()
					? generatedKeyColumn.getText()
					: null;
			return (schema, tables, chunk, verify) -> new TransferFileJobDraftBuilder()
					.save(schema, tables, chunk, verify, mode, compatibilityPolicy, destinationSchema, files, keyColumn,
							prepared.directory().resolve("transfer.yaml"))
					.file().toString();
		}, saved, cancelled);
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
		targetSchema.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
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
		Runnable refreshKey = () -> {
			generatedKey.setVisible(createMissing.isSelected());
			generatedKey.setEnabled(createMissing.isSelected() && !panel.isBusy());
			generatedKeyRow.setVisible(createMissing.isSelected() && generatedKey.isSelected());
			generatedKeyColumn.setEnabled(generatedKeyRow.isVisible() && !panel.isBusy());
		};
		createMissing.addActionListener(event -> {
			if (!createMissing.isSelected())
				generatedKey.setSelected(false);
			refreshKey.run();
			panel.draftOptionsChanged();
		});
		generatedKey.addActionListener(event -> {
			refreshKey.run();
			panel.draftOptionsChanged();
		});
		generatedKeyColumn.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
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
		showImportSettings.addActionListener(event -> {
			importSettings.setVisible(showImportSettings.isSelected());
			importSettings.revalidate();
		});
		temporalErrors.addActionListener(event -> panel.draftOptionsChanged());
		fileOptions.encoding.addActionListener(event -> panel.draftOptionsChanged());
		fileOptions.headerMode.addActionListener(event -> panel.draftOptionsChanged());
		fileOptions.emptyFieldMode.addActionListener(event -> panel.draftOptionsChanged());
		fileOptions.useNullToken.addActionListener(event -> panel.draftOptionsChanged());
		format.addActionListener(event -> {
			fileOptions.refreshEnabled(!panel.isBusy(), (FileFormat) format.getSelectedItem());
			panel.draftOptionsChanged();
		});
		panel.inspect.addPropertyChangeListener("enabled", event -> {
			format.setEnabled(!panel.isBusy());
			fileOptions.refreshEnabled(!panel.isBusy(), (FileFormat) format.getSelectedItem());
			targetSchema.setEnabled(!panel.isBusy());
			createMissing.setEnabled(!panel.isBusy());
			refreshKey.run();
			showImportSettings.setEnabled(!panel.isBusy());
			temporalErrors.setEnabled(!panel.isBusy());
		});
		final var content = new JPanel(new BorderLayout(8, 8));
		final var heading = new JPanel();
		heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
		heading.add(new JLabel(
				"Save file export settings for GUI / Gradle. Export needs no target connection or primary key."));
		final var schemaRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		final var schemaLabel = new JLabel("Target schema (blank: current; must already exist)");
		schemaLabel.setLabelFor(targetSchema);
		schemaRow.add(schemaLabel);
		schemaRow.add(targetSchema);
		importSettings.add(schemaRow);
		importSettings.add(createMissing);
		importSettings.add(generatedKey);
		importSettings.add(generatedKeyRow);
		importSettings.add(panel.fileImportOptions());
		importSettings
				.add(new JLabel("To rename target tables or columns, save the job and use Job settings → Edit names."));
		heading.add(temporalErrors);
		var formatRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		var formatLabel = new JLabel("File format");
		formatLabel.setLabelFor(format);
		formatRow.add(formatLabel);
		formatRow.add(format);
		formatRow.add(new JLabel("JSONL uses JSON null; no NULL token is needed."));
		heading.add(formatRow);
		heading.add(fileOptions);
		heading.add(showImportSettings);
		heading.add(importSettings);
		final var destination = new JTextField(prepared.directory().resolve("transfer.yaml").toString());
		destination.setEditable(false);
		heading.add(destination);
		for (var component : heading.getComponents())
			if (component instanceof JComponent field)
				field.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		content.add(heading, BorderLayout.NORTH);
		content.add(panel, BorderLayout.CENTER);
		refreshKey.run();
		return new Form(content, panel, targetSchema, format, nullValue, fileOptions, showImportSettings,
				importSettings, createMissing, generatedKey, generatedKeyColumn);
	}
}
