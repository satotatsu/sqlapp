/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

/**
 * Caret position and selection length, without changing the source document.
 */
final class EditorPositionLabel extends JLabel {
	private static final long serialVersionUID = 1L;

	EditorPositionLabel(final JTextArea editor) {
		setToolTipText(
				"Line and column start at 1; tabs count as one column. Selected characters include line endings.");
		editor.addCaretListener(event -> refresh(editor));
		refresh(editor);
	}

	private void refresh(final JTextArea editor) {
		try {
			final int offset = editor.getCaretPosition();
			final int line = editor.getLineOfOffset(offset);
			final String prefix = editor.getText(editor.getLineStartOffset(line),
					offset - editor.getLineStartOffset(line));
			final int column = prefix.codePointCount(0, prefix.length()) + 1;
			String value = "Line " + (line + 1) + ", column " + column;
			final String selected = editor.getSelectedText();
			if (selected != null && !selected.isEmpty()) {
				final int count = selected.codePointCount(0, selected.length());
				value += " | Selected " + count + (count == 1 ? " character" : " characters");
			}
			setText(value);
		} catch (final BadLocationException exception) {
			throw new IllegalStateException("Editor caret is outside its document.", exception);
		}
	}
}
