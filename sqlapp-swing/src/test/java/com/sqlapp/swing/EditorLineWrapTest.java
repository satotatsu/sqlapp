/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;

class EditorLineWrapTest {
	@Test
	void syntaxEditorWrapPreservesSourceSelectionLineCountHistoryAndSearchResult() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var editor = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, true);
			final var navigation = new EditorNavigationBar(editor);
			final String source = "-- 顧客\r\nSELECT " + "'long value', ".repeat(40) + "1;\r\n";
			editor.setText(source);
			editor.discardAllEdits();
			editor.select(10, 20);
			navigation.query.setText("value");
			navigation.countMatches();
			final String result = navigation.status.getText();
			assertFalse(editor.getLineWrap());
			assertFalse(navigation.wrapLines.isSelected());
			for (int index = 0; index < 2; index++) {
				navigation.wrapLines.doClick(0);
				assertEquals(index == 0, editor.getLineWrap());
				assertEquals(source, editor.getText());
				assertEquals(3, editor.getLineCount());
				assertEquals(10, editor.getSelectionStart());
				assertEquals(20, editor.getSelectionEnd());
				assertFalse(editor.canUndo());
				assertFalse(navigation.history.undo.isEnabled());
				assertEquals(result, navigation.status.getText());
			}
			navigation.setBusy(true);
			assertFalse(navigation.wrapLines.isEnabled());
			navigation.wrapLines.doClick(0);
			assertFalse(editor.getLineWrap());
			navigation.setBusy(false);
			editor.setLineWrap(true);
			assertTrue(navigation.wrapLines.isSelected());
		});
	}

	@Test
	void eachEditorKeepsItsOwnDisplayChoiceWithoutMarkingBufferDirty() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var sql = new SqlEditorPanel(path -> true);
			final var yaml = new JobConfigurationEditorPanel(snapshot -> {
			});
			sql.navigation.wrapLines.doClick(0);
			assertTrue(sql.text.getLineWrap());
			assertFalse(yaml.text.getLineWrap());
			assertFalse(sql.isDirty());
			assertFalse(yaml.isDirty());
			sql.fresh.doClick();
			assertTrue(sql.text.getLineWrap());
			yaml.navigation.wrapLines.doClick(0);
			assertTrue(yaml.text.getLineWrap());
			yaml.fresh.doClick();
			assertTrue(yaml.text.getLineWrap());
		});
	}
}
