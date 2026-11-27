/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;

class ReadOnlyCodePanelTest {
	@Test
	void resetsNavigationAndHistoryWhileRetainingViewPreference() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var text = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, false);
			final var panel = new ReadOnlyCodePanel(text);
			final String source = "SELECT '日😀';\r\nSELECT 2;\r\n";
			panel.showText(source);
			panel.navigation.query.setText("select");
			panel.navigation.next.doClick(0);
			assertEquals("SELECT", text.getSelectedText());
			assertTrue(panel.position.getText().contains("Selected 6 characters"));
			panel.navigation.line.setText("2");
			panel.navigation.column.setText("2");
			panel.navigation.go.doClick(0);
			panel.navigation.wrapLines.doClick(0);
			assertEquals(source, text.getText());
			panel.showText("-- replacement\n");
			assertTrue(panel.navigation.query.getText().isEmpty());
			assertTrue(panel.navigation.line.getText().isEmpty());
			assertTrue(panel.navigation.column.getText().isEmpty());
			assertTrue(panel.navigation.status.getText().isBlank());
			assertEquals("Line 1, column 1", panel.position.getText());
			assertTrue(text.getLineWrap());
			assertFalse(text.isEditable());
			assertFalse(panel.navigation.history.undo.isEnabled());
			panel.navigation.history.undo();
			assertEquals("-- replacement\n", text.getText());
		});
	}

	@Test
	void jsonIsIndentedWithoutChangingUnicodeOrEmbeddedSqlValues() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new ReadOnlyCodePanel(CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_JSON, false));
			final var model = java.util.Map.of("schema", "日本語", "sql", "SELECT 'line';\nSELECT 2;", "tables",
					java.util.List.of("ITEMS", "ORDERS"));
			panel.showJson(model);
			assertTrue(panel.editor.getLineCount() > 3);
			final var converter = new com.sqlapp.util.JsonConverter();
			assertEquals(converter.getObjectMapper().readTree(converter.toJsonString(model)),
					converter.getObjectMapper().readTree(panel.editor.getText()));
			assertFalse(panel.editor.isEditable());
			assertEquals(0, panel.editor.getCaretPosition());
		});
	}

	@Test
	void refusesEditableDocuments() throws Exception {
		SwingUtilities.invokeAndWait(() -> assertThrows(IllegalArgumentException.class,
				() -> new ReadOnlyCodePanel(CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, true))));
	}
}
