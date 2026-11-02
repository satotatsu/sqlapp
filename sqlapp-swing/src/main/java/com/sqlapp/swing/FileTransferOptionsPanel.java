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

	FileTransferOptionsPanel() {
		super(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		encoding.setEditable(true);
		encoding.setToolTipText(
				"Choose or type a Java character encoding. Default: UTF-8. Unrepresentable text fails export.");
		var encodingLabel = new JLabel("Character encoding");
		encodingLabel.setLabelFor(encoding);
		add(encodingLabel);
		add(encoding);
		var headerLabel = new JLabel("CSV / TSV header");
		headerLabel.setLabelFor(headerMode);
		add(headerLabel);
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
		add(headerMode);
	}

	void load(FileTransfer files) {
		encoding.setSelectedItem(files.encoding());
		headerMode.setSelectedItem(files.headerMode());
	}

	void refreshEnabled(boolean enabled, FileFormat format) {
		encoding.setEnabled(enabled);
		headerMode.setEnabled(enabled && format != FileFormat.JSONL);
	}

	FileTransfer settings(String directory, FileFormat format, String nullValue, TemporalPolicy policy) {
		String name = encoding.getEditor().getItem().toString().trim();
		try {
			java.nio.charset.Charset.forName(name);
		} catch (IllegalArgumentException failure) {
			throw new IllegalArgumentException("Choose a supported character encoding, such as UTF-8 or windows-31j.");
		}
		return new FileTransfer(directory, format, name, nullValue, policy,
				format == FileFormat.JSONL ? HeaderMode.NONE : (HeaderMode) headerMode.getSelectedItem());
	}
}
