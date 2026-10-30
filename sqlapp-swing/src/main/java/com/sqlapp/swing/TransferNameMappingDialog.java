/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;

final class TransferNameMappingDialog {
	static Path show(Component owner, Path job, Path file, java.util.List<String> selected) {
		var result = new java.util.concurrent.atomic.AtomicReference<Path>();
		var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Table and column names",
				Dialog.ModalityType.APPLICATION_MODAL);
		var editor = new TransferNameMappingPanel();
		var status = new JLabel("Loading mapping…");
		var save = new JButton("Save mapping…");
		var cancel = new JButton("Close");
		var location = new JLabel(file == null ? "New mapping — not saved" : "Mapping: " + file);
		location.setToolTipText(location.getText());
		var schemaFile = new JTextField(job.getParent().resolve("source-schema.xml").toString(), 30);
		var browseSchema = new JButton("Browse…");
		var loadColumns = new JButton("Load columns");
		var schemaRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		var schemaLabel = new JLabel("Source Schema XML");
		schemaLabel.setLabelFor(schemaFile);
		schemaRow.add(schemaLabel);
		schemaRow.add(schemaFile);
		schemaRow.add(browseSchema);
		schemaRow.add(loadColumns);
		var footer = new JPanel(new BorderLayout());
		var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(save);
		actions.add(cancel);
		footer.add(status, BorderLayout.NORTH);
		footer.add(actions, BorderLayout.SOUTH);
		var content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		var top = new JPanel(new BorderLayout(4, 4));
		top.add(location, BorderLayout.NORTH);
		top.add(schemaRow, BorderLayout.SOUTH);
		content.add(top, BorderLayout.NORTH);
		content.add(editor, BorderLayout.CENTER);
		content.add(footer, BorderLayout.SOUTH);
		dialog.setContentPane(content);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		var io = new TransferNameMappingIO();
		var opened = new java.util.concurrent.atomic.AtomicReference<TransferNameMappingIO.Snapshot>();
		var busy = new boolean[] { true };
		var available = new boolean[] { false };
		var closeGuard = new TransferNameMappingCloseGuard(editor::configuration, () -> {
			var choices = new String[] { "Keep editing", "Discard name changes" };
			return JOptionPane.showOptionDialog(dialog,
					"The mapping has unsaved name changes. Save the mapping to keep them, or discard to close.",
					"Unsaved name mapping", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices,
					choices[0]) == 1;
		});
		Runnable refresh = () -> {
			save.setEnabled(!busy[0] && available[0]);
			cancel.setEnabled(!busy[0]);
			loadColumns.setEnabled(save.isEnabled());
			browseSchema.setEnabled(save.isEnabled());
			schemaFile.setEnabled(save.isEnabled());
			enable(editor, save.isEnabled());
			editor.tableTools.refreshCounts();
			editor.columnTools.refreshCounts();
			editor.goToIssue.setEnabled(save.isEnabled() && editor.issueList.getSelectedValue() != null);
			editor.preview.refreshEnabled(save.isEnabled());
		};
		refresh.run();
		FileDropHandler.path(schemaFile, false, java.util.List.of("xml"), schemaFile::isEnabled,
				failure -> status.setText(failure.getMessage()));
		browseSchema.addActionListener(event -> {
			var chooser = new EditorFileChooser().forFile(
					new javax.swing.filechooser.FileNameExtensionFilter("Schema XML", "xml"), schemaFile.getText());
			if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION)
				schemaFile.setText(chooser.getSelectedFile().getAbsolutePath());
		});
		loadColumns.addActionListener(event -> {
			final java.util.List<String> sources;
			final Path schema;
			try {
				sources = java.util.List.copyOf(editor.configuration().tables().keySet());
				schema = Path.of(schemaFile.getText());
				if (schemaFile.getText().isBlank())
					throw new IllegalArgumentException("Choose a source Schema XML file.");
			} catch (Exception failure) {
				status.setText(message(failure));
				return;
			}
			busy[0] = true;
			refresh.run();
			status.setText("Reading columns for listed source tables…");
			new SwingWorker<Map<String, java.util.List<BulkMigrationJobDraftBuilder.SourceColumn>>, Void>() {
				@Override
				protected Map<String, java.util.List<BulkMigrationJobDraftBuilder.SourceColumn>> doInBackground()
						throws Exception {
					var builder = new BulkMigrationJobDraftBuilder();
					return builder.inspectColumns(builder.inspect(schema), sources);
				}

				@Override
				protected void done() {
					try {
						editor.includeColumns(get());
						status.setText(
								"Source columns loaded; existing names retained. Source types are informational. Review against the database before execution.");
					} catch (Exception failure) {
						status.setText(message(failure));
					} finally {
						busy[0] = false;
						refresh.run();
					}
				}
			}.execute();
		});
		Runnable close = () -> {
			if (!busy[0] && closeGuard.canClose())
				dialog.dispose();
		};
		cancel.addActionListener(event -> close.run());
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				close.run();
			}
		});
		dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),
				"closeMapping");
		dialog.getRootPane().getActionMap().put("closeMapping", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				close.run();
			}
		});
		new SwingWorker<TransferNameMappingIO.Configuration, Void>() {
			@Override
			protected TransferNameMappingIO.Configuration doInBackground() throws Exception {
				if (file != null) {
					checkDestination(job, file);
					var snapshot = io.read(file);
					opened.set(snapshot);
					return snapshot.configuration();
				}
				var tables = new LinkedHashMap<String, TransferNameMappingIO.TableMapping>();
				selected.forEach(name -> tables.put(name, new TransferNameMappingIO.TableMapping(null, Map.of())));
				return new TransferNameMappingIO.Configuration(1, tables);
			}

			@Override
			protected void done() {
				try {
					editor.load(get());
					closeGuard.markClean();
					available[0] = true;
					status.setText(
							"Edit names or load columns from a source Schema XML. Saving rewrites YAML comments and formatting.");
				} catch (Exception failure) {
					status.setText(message(failure));
				} finally {
					busy[0] = false;
					refresh.run();
				}
			}
		}.execute();
		save.addActionListener(event -> {
			final String text;
			try {
				text = editor.formattedMapping();
			} catch (Exception failure) {
				status.setText(message(failure));
				return;
			}
			var chooser = new EditorFileChooser().forOutput(
					new javax.swing.filechooser.FileNameExtensionFilter("YAML mapping", "yaml", "yml"),
					file == null ? "name-mapping.yaml" : file.getFileName().toString(), job.toString());
			if (file != null)
				chooser.setSelectedFile(file.toFile());
			if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION)
				return;
			var destination = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
			busy[0] = true;
			refresh.run();
			status.setText("Saving checked mapping…");
			new SwingWorker<TransferNameMappingIO.Snapshot, Void>() {
				@Override
				protected TransferNameMappingIO.Snapshot doInBackground() throws Exception {
					checkDestination(job, destination);
					var previous = opened.get();
					return io.write(destination, text,
							previous != null && previous.file().equals(destination) ? previous.fingerprint() : null);
				}

				@Override
				protected void done() {
					try {
						result.set(get().file());
						dialog.dispose();
					} catch (Exception failure) {
						status.setText(message(failure));
					} finally {
						busy[0] = false;
						refresh.run();
					}
				}
			}.execute();
		});
		dialog.setSize(940, 660);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return result.get();
	}

	private static void enable(Component component, boolean enabled) {
		component.setEnabled(enabled);
		if (component instanceof Container container)
			for (var child : container.getComponents())
				enable(child, enabled);
	}

	static void checkDestination(Path job, Path file) throws java.io.IOException {
		if (job.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())
				|| Files.exists(file) && Files.isSameFile(job, file))
			throw new java.io.IOException("Choose a mapping file separate from the transfer job.");
		var parent = file.toAbsolutePath().normalize().getParent();
		if (parent != null && Files.exists(parent.resolve("manifest.json"))
				&& Files.exists(parent.resolve("source-schema.xml")))
			throw new java.io.IOException("Completed packages cannot be edited. Choose a mapping outside the package.");
	}

	private static String message(Exception failure) {
		var cause = failure instanceof java.util.concurrent.ExecutionException ? failure.getCause() : failure;
		return cause.getMessage() == null ? "Unable to load or save mapping. Check the file and permissions."
				: cause.getMessage();
	}
}
