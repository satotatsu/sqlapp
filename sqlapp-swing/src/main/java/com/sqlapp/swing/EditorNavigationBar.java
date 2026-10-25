/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * Literal search/replacement and line navigation, with grouped undo support.
 */
final class EditorNavigationBar extends JPanel {
	private static final long serialVersionUID = 1L;
	final JTextField replacement = new JTextField(18);
	final JButton replace = new JButton("Replace selected");
	final JButton replaceAll = new JButton("Replace all");
	final EditorEditHistory history;
	final JTextField query = new JTextField(18);
	final JCheckBox matchCase = new JCheckBox("Match case");
	final JTextField line = new JTextField(5);
	final JButton next = new JButton("Next");
	final JButton previous = new JButton("Previous");
	final JButton go = new JButton("Go");
	final JLabel status = new JLabel(" ");
	private final JTextArea editor;
	private Runnable activateEditor = () -> {
	};

	EditorNavigationBar(final JTextArea editor) {
		super(new java.awt.BorderLayout(4, 4));
		this.editor = editor;
		history = new EditorEditHistory(editor);
		final var search = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		search.add(new JLabel("Find"));
		search.add(query);
		search.add(previous);
		search.add(next);
		search.add(matchCase);
		search.add(new JLabel("Line"));
		search.add(line);
		search.add(go);
		search.add(status);
		add(search, java.awt.BorderLayout.NORTH);
		final var edits = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		edits.add(new JLabel("Replace"));
		edits.add(replacement);
		edits.add(replace);
		edits.add(replaceAll);
		edits.add(history.undo);
		edits.add(history.redo);
		history.undo.addActionListener(event -> activateEditor.run());
		history.redo.addActionListener(event -> activateEditor.run());
		add(edits, java.awt.BorderLayout.SOUTH);
		replace.addActionListener(event -> replaceSelected());
		replaceAll.addActionListener(event -> replaceAllMatches());
		next.addActionListener(event -> find(true));
		previous.addActionListener(event -> find(false));
		query.addActionListener(event -> find(true));
		go.addActionListener(event -> goToLine());
		line.addActionListener(event -> goToLine());
		bind("control F", "find", () -> {
			if (isEnabled()) {
				query.requestFocusInWindow();
				query.selectAll();
			}
		});
		bind("F3", "next", () -> find(true));
		bind("shift F3", "previous", () -> find(false));
		bind("control H", "replace", () -> {
			if (isEnabled()) {
				replacement.requestFocusInWindow();
				replacement.selectAll();
			}
		});
		bind("control L", "line", () -> {
			if (isEnabled()) {
				line.requestFocusInWindow();
				line.selectAll();
			}
		});
	}

	void setEditorActivation(final Runnable activation) {
		activateEditor = java.util.Objects.requireNonNull(activation);
	}

	void setBusy(final boolean busy) {
		setEnabled(!busy);
		enableChildren(this, !busy);
		history.setBusy(busy);
	}

	private static void enableChildren(final java.awt.Container parent, final boolean enabled) {
		for (final var child : parent.getComponents()) {
			child.setEnabled(enabled);
			if (child instanceof java.awt.Container nested)
				enableChildren(nested, enabled);
		}
	}

	void replaceSelected() {
		if (!isEnabled() || !editor.isEditable())
			return;
		activateEditor.run();
		final String needle = query.getText();
		if (needle.isEmpty()) {
			status.setText("Enter text");
			return;
		}
		final String selected = editor.getSelectedText();
		if (selected == null || selected.length() != needle.length()
				|| !selected.regionMatches(!matchCase.isSelected(), 0, needle, 0, needle.length())) {
			find(true);
			if (editor.getSelectedText() != null
					&& editor.getSelectedText().regionMatches(!matchCase.isSelected(), 0, needle, 0, needle.length()))
				status.setText("Match selected; press Replace selected");
			return;
		}
		final String value = replacement.getText();
		if (value.equals(selected)) {
			status.setText("No text changes");
			return;
		}
		final String source = editor.getText();
		final int start = editor.getSelectionStart(), end = editor.getSelectionEnd();
		if ((long) source.length() - selected.length() + value.length() > 1_048_576L) {
			tooLarge();
			return;
		}
		if (!fits(source.substring(0, start) + value + source.substring(end)))
			return;
		history.compoundChange(() -> editor.replaceRange(value, start, end));
		editor.select(start, start + value.length());
		status.setText("Replaced 1 match");
	}

	void replaceAllMatches() {
		if (!isEnabled() || !editor.isEditable())
			return;
		activateEditor.run();
		final String needle = query.getText(), value = replacement.getText(), source = editor.getText();
		if (needle.isEmpty()) {
			status.setText("Enter text");
			return;
		}
		final var result = new StringBuilder();
		int cursor = 0, count = 0, found;
		while ((found = search(source, needle, cursor, true)) >= 0) {
			if ((long) result.length() + found - cursor + value.length() > 1_048_576L) {
				tooLarge();
				return;
			}
			result.append(source, cursor, found).append(value);
			cursor = found + needle.length();
			count++;
		}
		if (count == 0) {
			status.setText("Not found");
			return;
		}
		if ((long) result.length() + source.length() - cursor > 1_048_576L) {
			tooLarge();
			return;
		}
		result.append(source, cursor, source.length());
		final String changed = result.toString();
		if (!fits(changed))
			return;
		if (source.equals(changed)) {
			status.setText("No text changes");
			return;
		}
		history.compoundChange(() -> editor.replaceRange(changed, 0, editor.getDocument().getLength()));
		status.setText("Replaced " + count + " matches");
	}

	private boolean fits(final String value) {
		if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 1_048_576)
			return true;
		tooLarge();
		return false;
	}

	private void tooLarge() {
		status.setText("Replacement exceeds 1 MiB; no changes made");
	}

	void find(final boolean forward) {
		if (!isEnabled())
			return;
		activateEditor.run();
		final String needle = query.getText();
		if (needle.isEmpty()) {
			status.setText("Enter text");
			return;
		}
		final String haystack = editor.getText();
		final int start = forward ? editor.getSelectionEnd() : editor.getSelectionStart() - 1;
		int found = search(haystack, needle, start, forward);
		final boolean wrapped = found < 0;
		if (wrapped)
			found = search(haystack, needle, forward ? 0 : haystack.length() - needle.length(), forward);
		if (found < 0) {
			status.setText("Not found");
			return;
		}
		CodeEditors.reveal(editor, found);
		editor.select(found, found + needle.length());
		editor.requestFocusInWindow();
		status.setText(wrapped ? "Found (wrapped)" : "Found");
	}

	private int search(final String haystack, final String needle, final int start, final boolean forward) {
		final int last = haystack.length() - needle.length();
		for (int index = forward ? Math.max(0, start) : Math.min(last, start); forward ? index <= last
				: index >= 0; index += forward ? 1 : -1) {
			if (haystack.regionMatches(!matchCase.isSelected(), index, needle, 0, needle.length()))
				return index;
		}
		return -1;
	}

	void goToLine() {
		if (!isEnabled())
			return;
		activateEditor.run();
		final int number;
		try {
			number = Integer.parseInt(line.getText().trim());
		} catch (final NumberFormatException failure) {
			invalidLine();
			return;
		}
		if (number < 1 || number > editor.getLineCount()) {
			invalidLine();
			return;
		}
		try {
			final int offset = editor.getLineStartOffset(number - 1);
			CodeEditors.reveal(editor, offset);
			editor.setCaretPosition(offset);
			editor.requestFocusInWindow();
			status.setText("Line " + number);
		} catch (final javax.swing.text.BadLocationException failure) {
			throw new IllegalStateException("Line is outside the editor document.", failure);
		}
	}

	private void invalidLine() {
		status.setText("Enter 1–" + editor.getLineCount());
	}

	private void bind(final String keys, final String name, final Runnable action) {
		final String id = "job-editor-" + name;
		editor.getInputMap().put(KeyStroke.getKeyStroke(keys), id);
		final var binding = new javax.swing.AbstractAction() {
			public void actionPerformed(final java.awt.event.ActionEvent event) {
				action.run();
			}
		};
		editor.getActionMap().put(id, binding);
		getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(keys), id);
		getActionMap().put(id, binding);
	}
}
