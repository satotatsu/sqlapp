/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTabbedPane;
import com.sqlapp.data.db.command.migration.transfer.*;

/** Searchable recorded warnings; this view never grants execution approval. */
final class TransferWarningTabs {
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
		panel = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Warnings",
				"Recorded warnings and suggested next actions. Search by category, table, column or message; select a row to read the full text. Guidance does not authorize execution or change settings.",
				List.of("Origin / category", "Table / task", "Column", "Message", "Next action"), rows));
		panel.table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
		final int[] widths = { 180, 140, 100, 360, 300 };
		for (int column = 0; column < widths.length; column++) {
			panel.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
			panel.table.getColumnModel().getColumn(column).setWidth(widths[column]);
		}
		tabs.addTab("Warnings (" + rows.size() + ")", panel);
	}

	static List<List<String>> rows(Object value) {
		final var rows = new ArrayList<List<String>>();
		if (value instanceof DirectTransferDesktopService.Reviewed reviewed)
			compatibility(rows, reviewed.review().prepared().warnings());
		else if (value instanceof DirectTransferDesktopService.Executed executed)
			compatibility(rows, executed.outcome().prepared().warnings());
		else if (value instanceof FileTransferDesktopService.Reviewed reviewed) {
			export(rows, reviewed.manifest());
			compatibility(rows, reviewed.review().schemaPreparation().warnings());
		} else if (value instanceof TransferFileImportService.Outcome outcome)
			compatibility(rows, outcome.schemaPreparation().warnings());
		else if (value instanceof TransferFilePackageIO.Snapshot snapshot)
			export(rows, snapshot.manifest());
		else if (value instanceof FileTransferDesktopService.Inspected inspected)
			export(rows, inspected.snapshot().manifest());
		else
			throw new IllegalArgumentException("Unsupported transfer warning result.");
		return List.copyOf(rows);
	}

	private static void compatibility(List<List<String>> rows, List<String> warnings) {
		for (var warning : warnings) {
			String category = "Target compatibility";
			String action = "Review vendor differences and test the target definitions before application use.";
			if (warning.startsWith(TransferSchemaChangeChecks.DATA_IMPACT)) {
				category = "Target / Data impact";
				action = "Review Schema SQL. If execution is blocked and the changes are intended, enable data-impacting changes in Job settings, save and review again before executing.";
			} else if (warning.startsWith(TransferSchemaChangeChecks.UNRESOLVED)) {
				category = "Target / Manual work";
				action = "Inspect the affected definition and apply remaining changes manually. Generated SQL does not resolve this warning.";
			} else if (warning.startsWith(TransferGeneratedKeyChecks.CODE)) {
				category = "Target / Generated keys";
				action = "Check the target generator before automatic INSERTs. Counters were not checked or synchronized; this is not applicable if the target has no generator.";
			} else if (warning.startsWith(TransferSchemaChangeChecks.NAME_ADJUSTMENT)) {
				category = "Target / Object names";
				action = "Compare the source and target names in this message and Schema SQL. Check scripts or application code that refer to constraint/index names. Source Schema retains the original names. This warning does not confirm SQL execution.";
			}
			rows.add(List.of(category, "", "", warning, action));
		}
	}

	private static void export(List<List<String>> rows, TransferFilePackageIO.Manifest manifest) {
		for (var warning : manifest.warnings())
			rows.add(List.of("Package export", warning.taskId(), warning.column(), warning.message(),
					"Inspect export warnings and resolve unsupported value/type conversions before importing."));
	}
}
