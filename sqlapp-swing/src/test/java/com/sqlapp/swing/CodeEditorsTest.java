/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;

class CodeEditorsTest {
	@Test
	void sqlYamlAndJsonViewsUseSyntaxComponentsAndReadOnlyViewsCannotUndoTheirSnapshot() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var comparison = new SchemaComparisonPanel();
			assertEquals(SyntaxConstants.SYNTAX_STYLE_SQL, comparison.sqlPreview.getSyntaxEditingStyle());
			comparison.sqlPreview.setText("SELECT 1; -- example\n");
			boolean keyword = false;
			for (var token = comparison.sqlPreview.getTokenListForLine(0); token != null
					&& token.isPaintable(); token = token.getNextToken()) {
				keyword |= token.getType() == Token.RESERVED_WORD;
			}
			assertTrue(keyword, "SQL keywords must be tokenized, not just displayed in a plain text area");
			comparison.sqlPreview.undoLastAction();
			assertEquals("SELECT 1; -- example\n", comparison.sqlPreview.getText());
			assertFalse(comparison.sqlPreview.isEditable());
			final var editor = new JobConfigurationEditorPanel(snapshot -> {
			});
			assertEquals(SyntaxConstants.SYNTAX_STYLE_YAML, editor.text.getSyntaxEditingStyle());
			assertTrue(editor.editorScroll.getLineNumbersEnabled());
			assertTrue(editor.text.isCodeFoldingEnabled());
			final var report = new ReportReviewView();
			assertEquals(SyntaxConstants.SYNTAX_STYLE_JSON, report.details.getSyntaxEditingStyle());
			assertFalse(report.details.isEditable());
		});
	}

	@Test
	void nativeUndoAndToolbarShareOneHistoryAndResetCannotRestoreAnotherDocument() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var area = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_YAML, true);
			final var navigation = new EditorNavigationBar(area);
			navigation.history.resetWith(() -> area.setText("schemaFile: before.xml\n"));
			assertFalse(navigation.history.undo.isEnabled());
			navigation.query.setText("before.xml");
			navigation.replacement.setText("after.xml");
			navigation.replaceAllMatches();
			assertTrue(navigation.history.undo.isEnabled());
			area.undoLastAction();
			assertEquals("schemaFile: before.xml\n", area.getText());
			assertFalse(navigation.history.undo.isEnabled());
			assertTrue(navigation.history.redo.isEnabled());
			navigation.history.redo();
			assertEquals("schemaFile: after.xml\n", area.getText());
			assertFalse(navigation.history.redo.isEnabled());
			area.setEditable(false);
			area.undoLastAction();
			assertEquals("schemaFile: after.xml\n", area.getText());
			area.setEditable(true);
			navigation.history.resetWith(() -> area.setText("tasks: []\n"));
			assertFalse(area.canUndo());
			assertFalse(area.canRedo());
			assertFalse(navigation.history.undo.isEnabled());
			final var other = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_YAML, true);
			other.append("other editor");
			assertFalse(area.canUndo());
			assertFalse(navigation.history.undo.isEnabled());
		});
	}

	@Test
	void revealingHiddenOffsetOpensFoldWithoutEditingText() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var area = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_JSON, true);
			final String source = "{\n  \"name\": \"customers\"\n}\n";
			area.setText(source);
			area.getFoldManager().reparse();
			assertTrue(area.getFoldManager().getFoldCount() > 0);
			final var fold = area.getFoldManager().getFold(0);
			fold.setCollapsed(true);
			assertTrue(fold.isCollapsed());
			final var navigation = new EditorNavigationBar(area);
			navigation.query.setText("customers");
			navigation.find(true);
			assertEquals("customers", area.getSelectedText());
			assertFalse(fold.isCollapsed());
			fold.setCollapsed(true);
			navigation.line.setText("2");
			navigation.goToLine();
			assertEquals(2, area.getCaretPosition());
			assertFalse(fold.isCollapsed());
			assertEquals(source, area.getText());
		});
	}

	@Test
	void flatLafInstallsBeforeUiConstruction() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var previous = UIManager.getLookAndFeel();
			try {
				DesktopAppearance.install();
				assertInstanceOf(com.formdev.flatlaf.FlatLightLaf.class, UIManager.getLookAndFeel());
				final var editor = new JobConfigurationEditorPanel(snapshot -> {
				});
				editor.text.setText("schemaFile: example.xml\ntasks: []\n");
				assertTrue(editor.isDirty());
				assertEquals(3, editor.text.getLineCount());
			} finally {
				try {
					UIManager.setLookAndFeel(previous);
				} catch (final javax.swing.UnsupportedLookAndFeelException exception) {
					throw new AssertionError(exception);
				}
			}
		});
	}
}
