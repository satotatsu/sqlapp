/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;

class EditorPositionLabelTest {
	@Test
	void countsUnicodeCharactersAndCrLfSelectionsInEitherDirection() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var editor = new JTextArea();
			final var label = new EditorPositionLabel(editor);
			assertEquals("Line 1, column 1", label.getText());
			final String source = "\tA😀\r\n日B";
			editor.setText(source);
			editor.setCaretPosition(4);
			assertEquals("Line 1, column 4", label.getText());
			editor.setCaretPosition(6);
			assertEquals("Line 2, column 1", label.getText());
			editor.select(1, 7);
			assertEquals("Line 2, column 2 | Selected 5 characters", label.getText());
			editor.getCaret().setDot(7);
			editor.getCaret().moveDot(1);
			assertEquals("Line 1, column 2 | Selected 5 characters", label.getText());
			editor.select(6, 7);
			assertEquals("Line 2, column 2 | Selected 1 character", label.getText());
			assertEquals(source, editor.getText());
			editor.setText("");
			assertEquals("Line 1, column 1", label.getText());
		});
	}

	@Test
	void syntaxEditorSelectionDoesNotChangeDocumentOrUndoHistoryAndUndoUpdatesPosition() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var editor = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, true);
			final var label = new EditorPositionLabel(editor);
			editor.setText("SELECT 1;\nSELECT 2;");
			editor.discardAllEdits();
			editor.select(0, 9);
			assertTrue(label.getText().contains("Selected 9 characters"));
			assertFalse(editor.canUndo());
			assertEquals("SELECT 1;\nSELECT 2;", editor.getText());
			editor.setCaretPosition(editor.getDocument().getLength());
			editor.append("\n");
			assertEquals("Line 3, column 1", label.getText());
			editor.undoLastAction();
			assertEquals("Line 2, column 10", label.getText());
		});
	}
}
