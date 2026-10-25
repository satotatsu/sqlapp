/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class EditorNavigationBarTest {
	@Test
	void literalSearchWrapsInBothDirectionsWithCaseOptionWithoutEditing() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final String content = "猫 Cat cat CAT\nend";
			final var text = new JTextArea(content);
			final var navigation = new EditorNavigationBar(text);
			text.setCaretPosition(0);
			navigation.query.setText("cat");
			navigation.find(true);
			assertEquals(2, text.getSelectionStart());
			navigation.find(true);
			assertEquals(6, text.getSelectionStart());
			navigation.find(true);
			assertEquals(10, text.getSelectionStart());
			navigation.find(true);
			assertEquals(2, text.getSelectionStart());
			assertTrue(navigation.status.getText().contains("wrapped"));
			navigation.find(false);
			assertEquals(10, text.getSelectionStart());
			final Object key = navigation.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
					.get(javax.swing.KeyStroke.getKeyStroke("F3"));
			assertNotNull(key);
			navigation.getActionMap().get(key)
					.actionPerformed(new java.awt.event.ActionEvent(navigation.query, 0, "next"));
			assertEquals(2, text.getSelectionStart());
			navigation.matchCase.setSelected(true);
			text.setCaretPosition(0);
			navigation.find(true);
			assertEquals(6, text.getSelectionStart());
			navigation.query.setText("absent");
			navigation.find(true);
			assertEquals("Not found", navigation.status.getText());
			assertEquals(6, text.getSelectionStart());
			navigation.query.setText("");
			navigation.find(true);
			assertEquals("Enter text", navigation.status.getText());
			assertEquals(content, text.getText());
			assertNotNull(text.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("control F")));
			assertNotNull(text.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("F3")));
		});
	}

	@Test
	void lineNavigationRejectsInvalidNumbersAndBusyControlsDoNotMoveCaret() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var text = new JTextArea("one\r\ntwo\r\n");
			final var navigation = new EditorNavigationBar(text);
			text.setCaretPosition(1);
			for (final String invalid : new String[] { "0", "-1", "99", "abc", "999999999999999" }) {
				navigation.line.setText(invalid);
				navigation.goToLine();
				assertEquals(1, text.getCaretPosition());
				assertTrue(navigation.status.getText().startsWith("Enter 1–"));
			}
			navigation.line.setText("2");
			navigation.goToLine();
			assertEquals(5, text.getCaretPosition());
			navigation.setBusy(true);
			navigation.line.setText("1");
			navigation.goToLine();
			assertEquals(5, text.getCaretPosition());
			assertFalse(navigation.query.isEnabled());
			navigation.setBusy(false);
			navigation.goToLine();
			assertEquals(0, text.getCaretPosition());
		});
	}

	@Test
	void replacementIsLiteralNonOverlappingAndUndoableAsOneOperation() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var text = new JTextArea("Cat cat CAT");
			final var bar = new EditorNavigationBar(text);
			bar.query.setText("cat");
			bar.replacement.setText("$1cat");
			bar.replaceAllMatches();
			assertEquals("$1cat $1cat $1cat", text.getText());
			assertEquals("Replaced 3 matches", bar.status.getText());
			bar.history.undo();
			assertEquals("Cat cat CAT", text.getText());
			bar.history.redo();
			assertEquals("$1cat $1cat $1cat", text.getText());
			bar.history.undo();
			bar.matchCase.setSelected(true);
			bar.replacement.setText("");
			bar.replaceAllMatches();
			assertEquals("Cat  CAT", text.getText());
			bar.history.undo();
			assertEquals("Cat cat CAT", text.getText());
			bar.query.setText("aa");
			bar.replacement.setText("b");
			text.setText("aaa");
			bar.replaceAllMatches();
			assertEquals("ba", text.getText());
		});
	}

	@Test
	void replaceSelectedRequiresASelectedMatchAndBusyOrOversizedChangesAreRejected() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var text = new JTextArea("first second first");
			final var bar = new EditorNavigationBar(text);
			text.setCaretPosition(0);
			bar.query.setText("first");
			bar.replacement.setText("new");
			bar.replaceSelected();
			assertEquals("first second first", text.getText());
			assertEquals("first", text.getSelectedText());
			bar.replaceSelected();
			assertEquals("new second first", text.getText());
			bar.history.undo();
			assertEquals("first second first", text.getText());
			bar.setBusy(true);
			bar.replaceAllMatches();
			assertEquals("first second first", text.getText());
			assertFalse(bar.replacement.isEnabled());
			bar.setBusy(false);
			bar.replacement.setText("x".repeat(1_048_576));
			bar.replaceAllMatches();
			assertEquals("first second first", text.getText());
			assertTrue(bar.status.getText().contains("exceeds 1 MiB"));
			bar.query.setText("");
			bar.replaceAllMatches();
			assertEquals("Enter text", bar.status.getText());
		});
	}

}
