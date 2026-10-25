/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

import org.fife.ui.rtextarea.RTextArea;

/**
 * Per-document, in-memory undo history; replacements can be grouped as one
 * edit.
 */
final class EditorEditHistory {
	final JButton undo = new JButton("Undo");
	final JButton redo = new JButton("Redo");
	private final JTextArea editor;
	private final UndoManager manager = new UndoManager();
	private final RTextArea nativeEditor;
	private CompoundEdit compound;
	private boolean resetting;
	private boolean busy;

	EditorEditHistory(final JTextArea editor) {
		this.editor = editor;
		nativeEditor = editor instanceof RTextArea area ? area : null;
		manager.setLimit(200);
		if (nativeEditor != null)
			editor.addPropertyChangeListener(CodeEditors.UNDO_STATE, event -> refresh());
		else
			editor.getDocument().addUndoableEditListener(event -> {
				if (!resetting) {
					if (compound == null)
						manager.addEdit(event.getEdit());
					else
						compound.addEdit(event.getEdit());
				}
				refresh();
			});
		undo.addActionListener(event -> undo());
		redo.addActionListener(event -> redo());
		bind(editor, "control Z", "undo", this::undo);
		bind(editor, "control Y", "redo", this::redo);
		bind(editor, "control shift Z", "redo-alternative", this::redo);
		refresh();
	}

	void setBusy(final boolean busy) {
		this.busy = busy;
		refresh();
	}

	void undo() {
		if (busy || !canUndo())
			return;
		if (nativeEditor != null)
			nativeEditor.undoLastAction();
		else
			manager.undo();
		refresh();
		editor.requestFocusInWindow();
	}

	void redo() {
		if (busy || !canRedo())
			return;
		if (nativeEditor != null)
			nativeEditor.redoLastAction();
		else
			manager.redo();
		refresh();
		editor.requestFocusInWindow();
	}

	void compoundChange(final Runnable change) {
		if (busy)
			return;
		if (nativeEditor != null) {
			nativeEditor.beginAtomicEdit();
			try {
				change.run();
			} finally {
				nativeEditor.endAtomicEdit();
				refresh();
			}
			return;
		}
		if (compound != null) {
			change.run();
			return;
		}
		compound = new CompoundEdit();
		try {
			change.run();
		} finally {
			compound.end();
			if (compound.isSignificant())
				manager.addEdit(compound);
			compound = null;
			refresh();
		}
	}

	void resetWith(final Runnable change) {
		resetting = true;
		try {
			change.run();
		} finally {
			resetting = false;
			manager.discardAllEdits();
			if (nativeEditor != null)
				nativeEditor.discardAllEdits();
			refresh();
		}
	}

	private void refresh() {
		undo.setEnabled(!busy && canUndo());
		redo.setEnabled(!busy && canRedo());
	}

	private boolean canUndo() {
		return nativeEditor == null ? manager.canUndo() : nativeEditor.canUndo();
	}

	private boolean canRedo() {
		return nativeEditor == null ? manager.canRedo() : nativeEditor.canRedo();
	}

	private void bind(final JTextArea editor, final String keys, final String name, final Runnable operation) {
		final String id = "job-history-" + name;
		editor.getInputMap().put(KeyStroke.getKeyStroke(keys), id);
		editor.getActionMap().put(id, new javax.swing.AbstractAction() {
			public void actionPerformed(java.awt.event.ActionEvent event) {
				operation.run();
			}
		});
	}
}
