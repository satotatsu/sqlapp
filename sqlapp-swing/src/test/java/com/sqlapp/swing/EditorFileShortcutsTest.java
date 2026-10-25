/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;

class EditorFileShortcutsTest {
	@Test
	void saveSelectsExistingOrNewFilePathAndBusyActionsAreConsumedWithoutRunning() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var owner = new JPanel();
			final var editor = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, true);
			final var available = new AtomicBoolean(true);
			final var hasFile = new AtomicBoolean(false);
			final var saves = new AtomicInteger();
			final var saveAsCalls = new AtomicInteger();
			final var fresh = new JButton();
			final var open = new JButton();
			final var save = new JButton();
			final var saveAs = new JButton();
			save.addActionListener(event -> saves.incrementAndGet());
			saveAs.addActionListener(event -> saveAsCalls.incrementAndGet());
			EditorFileShortcuts.install(owner, editor, available::get, hasFile::get, fresh, open, save, saveAs);
			invoke(editor, "control S");
			assertEquals(1, saveAsCalls.get());
			assertEquals(0, saves.get());
			hasFile.set(true);
			invoke(editor, "control S");
			assertEquals(1, saves.get());
			invoke(editor, "control shift S");
			assertEquals(2, saveAsCalls.get());
			available.set(false);
			invoke(editor, "control S");
			invoke(editor, "meta shift S");
			assertEquals(1, saves.get());
			assertEquals(2, saveAsCalls.get());
			available.set(true);
			save.setEnabled(false);
			invoke(editor, "control S");
			assertEquals(1, saves.get());
			assertEquals(2, saveAsCalls.get());
		});
	}

	@Test
	void shortcutsBelongToEachEditorAndSupportParentFocusAndMacModifiers() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var first = new JPanel();
			final var second = new JPanel();
			final var firstText = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_SQL, true);
			final var secondText = CodeEditors.create(SyntaxConstants.SYNTAX_STYLE_YAML, true);
			final var calls = new AtomicInteger();
			final var otherCalls = new AtomicInteger();
			final var fresh = new JButton();
			final var open = new JButton();
			final var save = new JButton();
			final var saveAs = new JButton();
			fresh.addActionListener(event -> calls.addAndGet(1));
			open.addActionListener(event -> calls.addAndGet(10));
			save.addActionListener(event -> calls.addAndGet(100));
			final var otherSave = new JButton();
			otherSave.addActionListener(event -> otherCalls.incrementAndGet());
			EditorFileShortcuts.install(first, firstText, () -> true, () -> true, fresh, open, save, saveAs);
			EditorFileShortcuts.install(second, secondText, () -> true, () -> true, new JButton(), new JButton(),
					otherSave, new JButton());
			invoke(firstText, "meta N");
			invoke(firstText, "control O");
			final var key = KeyStroke.getKeyStroke("meta S");
			final Object id = first.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key);
			assertSame(firstText.getActionMap().get(firstText.getInputMap().get(key)), first.getActionMap().get(id));
			first.getActionMap().get(id).actionPerformed(new java.awt.event.ActionEvent(first, 0, "shortcut"));
			assertEquals(111, calls.get());
			assertEquals(0, otherCalls.get());
			invoke(secondText, "control S");
			assertEquals(1, otherCalls.get());
			assertEquals(111, calls.get());
			open.setEnabled(false);
			invoke(firstText, "control O");
			assertEquals(111, calls.get());
		});
	}

	static void invoke(final JComponent editor, final String keys) {
		final Object id = editor.getInputMap().get(KeyStroke.getKeyStroke(keys));
		assertNotNull(id, keys);
		final var action = editor.getActionMap().get(id);
		assertNotNull(action, keys);
		action.actionPerformed(new java.awt.event.ActionEvent(editor, 0, keys));
	}
}
