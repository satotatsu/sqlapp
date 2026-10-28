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
				"Recorded warnings for this result. Search by table, column or message; select a row to read its full text. These warnings do not authorize execution.",
				List.of("Origin", "Table / task", "Column", "Message"), rows));
		panel.table.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
		final int[] widths = { 150, 180, 120, 550 };
		for (int column = 0; column < widths.length; column++)
			panel.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
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
		for (var warning : warnings)
			rows.add(List.of("Target compatibility", "", "", warning));
	}

	private static void export(List<List<String>> rows, TransferFilePackageIO.Manifest manifest) {
		for (var warning : manifest.warnings())
			rows.add(List.of("Package export", warning.taskId(), warning.column(), warning.message()));
	}
}
