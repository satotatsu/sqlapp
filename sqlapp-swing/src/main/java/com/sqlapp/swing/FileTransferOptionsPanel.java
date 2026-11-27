/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.*;

/** Common file interpretation controls for new and saved jobs. */
final class FileTransferOptionsPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final JComboBox<String> encoding = new JComboBox<>(
			new String[] { "UTF-8", "windows-31j", "Shift_JIS", "EUC-JP", "UTF-16LE", "UTF-16BE", "ISO-8859-1" });
	final JComboBox<HeaderMode> headerMode = new JComboBox<>(HeaderMode.values());
	final JComboBox<EmptyFieldMode> emptyFieldMode = new JComboBox<>(EmptyFieldMode.values());
	final JCheckBox useNullToken = new JCheckBox("Use explicit NULL token");
	final JTextField nullValue = new JTextField("__SQLAPP_NULL__", 16);
	private boolean inputsEnabled = true;
	private FileFormat activeFormat = FileFormat.CSV;

	FileTransferOptionsPanel() {
		super(new java.awt.BorderLayout());
		var row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		add(row, java.awt.BorderLayout.NORTH);
		encoding.setEditable(true);
		encoding.setToolTipText(
				"Choose or type a Java character encoding. Default: UTF-8. Unrepresentable text fails export.");
		var encodingLabel = new JLabel("Character encoding");
		encodingLabel.setLabelFor(encoding);
		row.add(encodingLabel);
		row.add(encoding);
		var headerLabel = new JLabel("CSV / TSV header");
		headerLabel.setLabelFor(headerMode);
		row.add(headerLabel);
		headerMode.setRenderer(new DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				return super.getListCellRendererComponent(list, value instanceof HeaderMode mode ? switch (mode) {
				case VALIDATE -> "Validate names and skip (default)";
				case SKIP -> "Skip without checking names";
				case NONE -> "No header row";
				} : value, index, selected, focus);
			}
		});
		headerMode.setToolTipText(
				"Export writes a header for VALIDATE/SKIP. Import consumes one parsed header record; NONE keeps the first row as data. SKIP/NONE use captured Schema column order and do not remap columns. JSONL has no header.");
		row.add(headerMode);
		var nulls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		var emptyLabel = new JLabel("Unquoted empty fields");
		emptyLabel.setLabelFor(emptyFieldMode);
		nulls.add(emptyLabel);
		nulls.add(emptyFieldMode);
		nulls.add(useNullToken);
		nulls.add(nullValue);
		emptyFieldMode.setRenderer(new DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus) {
				return super.getListCellRendererComponent(list, value instanceof EmptyFieldMode mode ? switch (mode) {
				case NULL -> "SQL NULL (default)";
				case EMPTY_STRING -> "Empty text";
				} : value, index, selected, focus);
			}
		});
		emptyFieldMode.setToolTipText(
				"Quoted empty fields remain empty text. Empty-text mode requires a NULL token for exported SQL NULL.");
		useNullToken.addActionListener(event -> {
			refreshEnabled(inputsEnabled, activeFormat);
		});
		emptyFieldMode.addActionListener(event -> {
			if (activeFormat != FileFormat.JSONL && emptyFieldMode.getSelectedItem() != EmptyFieldMode.NULL)
				useNullToken.setSelected(true);
			refreshEnabled(inputsEnabled, activeFormat);
		});
		add(nulls, java.awt.BorderLayout.CENTER);
		refreshEnabled(true, FileFormat.CSV);
	}

	void load(FileTransfer files) {
		encoding.setSelectedItem(files.encoding());
		headerMode.setSelectedItem(files.headerMode());
		emptyFieldMode.setSelectedItem(files.emptyFieldMode());
		useNullToken.setSelected(files.nullValue() != null);
		nullValue.setText(files.nullValue() == null ? "__SQLAPP_NULL__" : files.nullValue());
	}

	void refreshEnabled(boolean enabled, FileFormat format) {
		inputsEnabled = enabled;
		activeFormat = format;
		encoding.setEnabled(enabled);
		headerMode.setEnabled(enabled && format != FileFormat.JSONL);
		emptyFieldMode.setEnabled(enabled && format != FileFormat.JSONL);
		useNullToken.setEnabled(enabled && format != FileFormat.JSONL);
		nullValue.setEnabled(enabled && format != FileFormat.JSONL && useNullToken.isSelected());
	}

	FileTransfer settings(String directory, FileFormat format, TemporalPolicy policy) {
		return settings(directory, format,
				format != FileFormat.JSONL && useNullToken.isSelected() ? nullValue.getText() : null, policy);
	}

	FileTransfer settings(String directory, FileFormat format, String nullValue, TemporalPolicy policy) {
		String name = encoding.getEditor().getItem().toString().trim();
		try {
			java.nio.charset.Charset.forName(name);
		} catch (IllegalArgumentException failure) {
			throw new IllegalArgumentException("Choose a supported character encoding, such as UTF-8 or windows-31j.");
		}
		return new FileTransfer(directory, format, name, nullValue, policy,
				format == FileFormat.JSONL ? HeaderMode.NONE : (HeaderMode) headerMode.getSelectedItem(),
				format == FileFormat.JSONL ? EmptyFieldMode.NULL : (EmptyFieldMode) emptyFieldMode.getSelectedItem());
	}
}
