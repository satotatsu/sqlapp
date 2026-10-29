/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTabbedPane;
import com.sqlapp.data.db.command.migration.transfer.*;

/** Searchable statements in original execution order; never a resume plan. */
final class TransferSchemaStatementTabs {
	private ReportDetailTablePanel panel;

	void clear(JTabbedPane tabs) {
		if (panel != null)
			tabs.remove(panel);
		panel = null;
	}

	void show(JTabbedPane tabs, Object value) {
		clear(tabs);
		final var rows = rows(value);
		if (rows.isEmpty())
			return;
		panel = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Schema statements",
				"Recorded SQL in execution order. Search by task or SQL and select a row for full text. Completed SQL may already be committed; an uncertain attempt may have taken effect. This is not a resume plan or execution approval.",
				List.of("Order", "Table / task", "Recorded status", "SQL"), rows));
		tabs.addTab("Schema statements (" + rows.size() + ")", panel);
	}

	static List<List<String>> rows(Object value) {
		List<TransferSchemaPreparation.Statement> statements;
		TransferSchemaPreparation.Progress progress = null;
		if (value instanceof DirectTransferDesktopService.Reviewed review)
			statements = review.review().prepared().schemaPreparation().statements();
		else if (value instanceof FileTransferDesktopService.Reviewed review)
			statements = review.review().schemaPreparation().statements();
		else if (value instanceof DirectTransferDesktopService.Executed execution) {
			progress = execution.outcome().schemaPreparation();
			statements = progress.planned();
		} else if (value instanceof TransferFileImportService.Outcome outcome) {
			progress = outcome.schemaPreparation();
			statements = progress.planned();
		} else
			return List.of();
		return rows(statements, progress);
	}

	static List<List<String>> rows(List<TransferSchemaPreparation.Statement> statements,
			TransferSchemaPreparation.Progress progress) {
		final var rows = new ArrayList<List<String>>();
		for (int index = 0; index < statements.size(); index++) {
			final int order = index + 1;
			final String status = progress == null ? "Reviewed / not executed"
					: order <= progress.completedStatements() ? "Completed"
							: Integer.valueOf(order).equals(progress.attemptedStatement()) ? "Uncertain attempt"
									: "Not executed";
			final var statement = statements.get(index);
			rows.add(List.of(Integer.toString(order), statement.taskId(), status, statement.sql()));
		}
		return List.copyOf(rows);
	}
}
