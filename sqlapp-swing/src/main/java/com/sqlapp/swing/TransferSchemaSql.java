/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;
import java.util.List;

import com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation;

/**
 * Content of a transfer screen's "Schema SQL" tab.
 *
 * @param display         text shown in the tab: the editable draft when one is
 *                        available, otherwise the reviewed statement list
 * @param draft           full SQL draft offered to the SQL editor, or null
 * @param protectedInputs files the draft must not overwrite; null when the
 *                        draft is not offered for editing
 */
record TransferSchemaSql(String display, String draft, List<Path> protectedInputs) {
	static void appendStatements(final StringBuilder text, final List<TransferSchemaPreparation.Statement> statements) {
		for (int index = 0; index < statements.size(); index++)
			text.append("-- Statement ").append(index + 1).append(" / ").append(statements.get(index).taskId())
					.append('\n').append(statements.get(index).sql()).append("\n\n");
	}
}
