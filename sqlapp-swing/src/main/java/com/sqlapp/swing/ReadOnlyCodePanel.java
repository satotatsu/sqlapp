/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/** Shared navigation for generated SQL, reviews and parsed report JSON. */
final class ReadOnlyCodePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	final RSyntaxTextArea editor;
	final EditorNavigationBar navigation;
	final EditorPositionLabel position;

	ReadOnlyCodePanel(final RSyntaxTextArea editor) {
		super(new BorderLayout(4, 4));
		if (editor.isEditable())
			throw new IllegalArgumentException("A read-only code view requires a non-editable editor.");
		this.editor = editor;
		navigation = new EditorNavigationBar(editor, false);
		position = new EditorPositionLabel(editor);
		add(navigation, BorderLayout.NORTH);
		add(CodeEditors.scroll(editor), BorderLayout.CENTER);
		add(position, BorderLayout.SOUTH);
	}

	void showText(final String text) {
		navigation.history.resetWith(() -> editor.setText(text));
		editor.setCaretPosition(0);
		navigation.query.setText("");
		navigation.line.setText("");
		navigation.column.setText("");
		navigation.status.setText(" ");
	}

	void showJson(final Object value) {
		final var converter = new com.sqlapp.util.JsonConverter();
		converter.setIndentOutput(true);
		showText(converter.toJsonString(value));
	}
}
