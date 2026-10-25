/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.Font;

import javax.swing.JTextArea;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.RUndoManager;

/** Consistent code views and an observable, single native undo history. */
final class CodeEditors {
	static final String UNDO_STATE = "sqlapp.undoState";

	private CodeEditors() {
	}

	static RSyntaxTextArea create(final String syntax, final boolean editable) {
		final var area = new CodeArea();
		area.setSyntaxEditingStyle(syntax);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		area.setCodeFoldingEnabled(true);
		area.setTabSize(2);
		area.setTabsEmulated(true);
		area.setAutoIndentEnabled(editable);
		area.setCloseCurlyBraces(false);
		area.setCloseMarkupTags(false);
		area.setHyperlinksEnabled(false);
		area.setHighlightCurrentLine(editable);
		area.setEditable(editable);
		return area;
	}

	static RTextScrollPane scroll(final RSyntaxTextArea area) {
		return new RTextScrollPane(area, true);
	}

	static void reveal(final JTextArea area, final int offset) {
		if (area instanceof RSyntaxTextArea syntax)
			syntax.getFoldManager().ensureOffsetNotInClosedFold(offset);
	}

	private static final class CodeArea extends RSyntaxTextArea {
		private static final long serialVersionUID = 1L;

		@Override
		protected RUndoManager createUndoManager() {
			final var manager = new RUndoManager(this) {
				private static final long serialVersionUID = 1L;

				@Override
				public void updateActions() {
					super.updateActions();
					firePropertyChange(UNDO_STATE, false, true);
				}
			};
			manager.setLimit(200);
			return manager;
		}

		@Override
		public void undoLastAction() {
			if (isEditable())
				super.undoLastAction();
		}

		@Override
		public void redoLastAction() {
			if (isEditable())
				super.redoLastAction();
		}
	}
}
