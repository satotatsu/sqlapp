/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Access file entry point composed with the shared direct and CSV workflows.
 */
final class MdbTransferPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField file = new JTextField();
	final JButton browse = new JButton("Browse…");
	final JLabel selection = new JLabel("Choose or drop an Access .mdb / .accdb file. Selection does not connect.");
	final DirectTransferPanel transfer;
	final FileTransferPanel csv;
	final JTabbedPane modes = new JTabbedPane();
	private final EditorFileChooser files = new EditorFileChooser();

	MdbTransferPanel() {
		this(new DirectTransferDesktopService(), null);
	}

	MdbTransferPanel(DirectTransferPanel.Backend backend,
			java.util.function.Function<DataMigrationDraftService.Prepared, Path> presenter) {
		this(backend, new FileTransferDesktopService(), presenter, null);
	}

	MdbTransferPanel(DirectTransferPanel.Backend backend, FileTransferPanel.Backend csvBackend,
			java.util.function.Function<DataMigrationDraftService.Prepared, Path> presenter,
			java.util.function.Function<DataMigrationDraftService.Prepared, Path> csvPresenter) {
		super(new BorderLayout(4, 4));
		transfer = new DirectTransferPanel((action, request, sourcePassword, targetPassword) -> {
			if (action != DirectTransferPanel.Action.TEST_TARGET)
				MdbSourceFile.validate(request.source());
			return backend.perform(action, request, sourcePassword, targetPassword);
		}, presenter);
		csv = new FileTransferPanel((action, input, workspace, spec, password, schema, reviewed) -> {
			if (action == FileTransferPanel.Action.DISCOVER || action == FileTransferPanel.Action.EXPORT
					|| action == FileTransferPanel.Action.TEST && spec != null
							&& MdbSourceFile.DRIVER.equals(spec.driverClass()))
				MdbSourceFile.validate(spec);
			return csvBackend.perform(action, input, workspace, spec, password, schema, reviewed);
		}, csvPresenter);
		csv.useFileSource(file);
		csv.source.driverClass.setText(MdbSourceFile.DRIVER);
		csv.create.setText("New Access CSV job…");
		transfer.useFileSource(file);
		transfer.source.driverClass.setText(MdbSourceFile.DRIVER);
		transfer.create.setText("New Access migration job…");
		transfer.summary.setText(
				"1. Choose an Access file and enter the target connection.\n2. Create a job and select tables; choose data transfer or schema only.\n3. Review warnings and Schema SQL.\n4. Confirm execution. The saved job also works with Gradle.");
		final var header = new JPanel(new BorderLayout(6, 6));
		header.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
		header.add(new JLabel("Access source file (.mdb / .accdb)"), BorderLayout.WEST);
		header.add(file, BorderLayout.CENTER);
		header.add(browse, BorderLayout.EAST);
		final var notices = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
		notices.add(selection);
		notices.add(new JLabel(
				"Tables, keys, indexes and supported values only. Access forms, reports and VBA are not transferred."));
		header.add(notices, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		modes.addTab("Direct transfer / schema only", transfer);
		modes.addTab("CSV package transfer", csv);
		add(modes, BorderLayout.CENTER);
		modes.addChangeListener(event -> {
			header.setVisible(modes.getSelectedComponent() != csv || csv.step.getSelectedIndex() == 0);
			updateSelectionHint();
		});
		csv.step.addActionListener(
				event -> header.setVisible(modes.getSelectedComponent() != csv || csv.step.getSelectedIndex() == 0));
		file.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent event) {
				selected();
			}

			public void removeUpdate(DocumentEvent event) {
				selected();
			}

			public void changedUpdate(DocumentEvent event) {
				selected();
			}
		});
		browse.addActionListener(event -> {
			if (isBusy())
				return;
			final var chooser = files.forFile(
					new FileNameExtensionFilter("Access database (*.mdb, *.accdb)", "mdb", "accdb"), file.getText());
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				files.remember(chooser.getSelectedFile().toPath());
				file.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		FileDropHandler.path(file, false, List.of("mdb", "accdb"), () -> !isBusy(),
				failure -> selection.setText(failure.getMessage()));
		for (var source : new ConnectionFields[] { transfer.source, csv.source })
			source.jdbcUrl.addPropertyChangeListener("enabled", event -> {
				file.setEnabled(Boolean.TRUE.equals(event.getNewValue()) && !isBusy());
				browse.setEnabled(file.isEnabled());
				modes.setEnabled(file.isEnabled());
			});
	}

	private void selected() {
		// Every edit invalidates the prior review, including invalid file selections.
		transfer.source.jdbcUrl.setText("");
		transfer.setSourceFiles(List.of());
		csv.source.jdbcUrl.setText("");
		csv.setSourceFiles(List.of());
		try {
			if (file.getText().isBlank()) {
				selection.setText("Choose or drop an Access .mdb / .accdb file. Selection does not connect.");
				return;
			}
			transfer.source.driverClass.setText(MdbSourceFile.DRIVER);
			transfer.source.jdbcUrl.setText(MdbSourceFile.jdbcUrl(Path.of(file.getText().trim())));
			transfer.setSourceFiles(List.of(Path.of(file.getText().trim()).toAbsolutePath().normalize()));
			csv.source.driverClass.setText(MdbSourceFile.DRIVER);
			csv.source.jdbcUrl.setText(transfer.source.jdbcUrl.getText());
			csv.setSourceFiles(List.of(Path.of(file.getText().trim()).toAbsolutePath().normalize()));
			updateSelectionHint();
		} catch (RuntimeException failure) {
			selection.setText(failure.getMessage());
		}
	}

	private void updateSelectionHint() {
		if (!transfer.source.jdbcUrl.getText().isBlank())
			selection.setText(modes.getSelectedComponent() == csv
					? "File selected. Create a CSV job and choose tables, then export. No connection has been opened."
					: "File selected. Set the target, then create a job and choose tables. No connection has been opened.");
	}

	boolean isBusy() {
		return transfer.isBusy() || csv.isBusy();
	}
}
