/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.*;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.TransferFileImportService;

/** Offline recorded metadata, never a target review or a value check. */
final class TransferPackageTablesTabs {
	ReportDetailTablePanel panel;
	ReportDetailTablePanel columns;
	JButton showColumns;
	JButton showRenamed;
	JButton showAllColumns;
	JButton preview;

	record PreviewSelection(java.nio.file.Path manifest, String fingerprint, String task,
			TransferFileImportService.PackageDescription description) {
	}

	private java.util.function.Consumer<PreviewSelection> previewNavigator;

	void setPreviewNavigator(java.util.function.Consumer<PreviewSelection> navigator) {
		previewNavigator = navigator;
	}

	private TransferFileImportService.PackageDescription retained;
	private long generation;

	void clear(JTabbedPane tabs) {
		generation++;
		if (panel != null)
			tabs.remove(panel);
		if (columns != null)
			tabs.remove(columns);
		panel = null;
		columns = null;
		showColumns = null;
		showRenamed = null;
		showAllColumns = null;
		preview = null;
		retained = null;
	}

	void show(JTabbedPane tabs, Object value) {
		var previous = retained;
		clear(tabs);
		var description = value instanceof FileTransferDesktopService.Inspected inspected ? inspected.description()
				: matches(previous, value) ? previous : null;
		if (description == null)
			return;
		retained = description;
		var rows = description.tables().stream()
				.map(table -> List.of(table.taskId(),
						identity(table.sourceCatalog(), table.sourceSchema(), table.sourceTable()), table.targetTable(),
						Long.toString(table.recordedRows()), Integer.toString(table.columns().size()),
						table.fullScanInsert() ? "Full scan INSERT" : "Numeric keyset INSERT",
						table.generatedKeyIfCreated()
								? description.configuration().effectiveGeneratedKeyColumn() + " (if target created)"
								: "Not requested",
						table.file()))
				.toList();
		panel = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Package tables",
				"Recorded source metadata and requested names. Rows are recorded totals, not a value check. Target schema: "
						+ Objects.toString(description.configuration().targetSchema(),
								"current target connection schema (unresolved)")
						+ ". Retained package metadata, not live target state. Copy routes require supported values. Use Review transfer to resolve target types, existing tables and compatibility.",
				List.of("Task", "Source table", "Requested target table", "Recorded rows", "Columns", "Copy route",
						"Additional PK", "File"),
				rows));
		panel.setNumericColumns(List.of(3, 4));
		panel.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		for (int index = 0; index < 8; index++)
			panel.table.getColumnModel().getColumn(index).setPreferredWidth(index == 1 || index == 6 ? 240 : 160);
		showColumns = new JButton("Show columns");
		showColumns.setEnabled(false);
		var controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		controls.add(showColumns);
		preview = new JButton("Preview selected table…");
		preview.setEnabled(false);
		controls.add(preview);
		panel.setScopeControls(controls);
		long current = generation;
		panel.table.getSelectionModel().addListSelectionListener(event -> {
			if (current == generation) {
				showColumns.setEnabled(panel.table.getSelectedRow() >= 0);
				preview.setEnabled(showColumns.isEnabled() && previewNavigator != null && description.snapshot() != null
						&& description.snapshot().fingerprint() != null);
			}
		});
		preview.addActionListener(event -> {
			if (current != generation || !preview.isEnabled() || panel.table.getSelectedRow() < 0)
				return;
			int model = panel.table.convertRowIndexToModel(panel.table.getSelectedRow());
			previewNavigator.accept(new PreviewSelection(description.snapshot().file(),
					description.snapshot().fingerprint(), description.tables().get(model).taskId(), description));
		});
		showColumns.addActionListener(event -> {
			if (current != generation || panel.table.getSelectedRow() < 0)
				return;
			int model = panel.table.convertRowIndexToModel(panel.table.getSelectedRow());
			var table = description.tables().get(model);
			var columnRows = new ArrayList<List<String>>();
			for (int index = 0; index < table.columns().size(); index++) {
				var column = table.columns().get(index);
				columnRows.add(List.of(Integer.toString(index + 1), column.sourceName(), column.targetName(),
						column.sourceType(), Boolean.toString(column.notNull()), Boolean.toString(column.primaryKey()),
						column.sourceName().equals(column.targetName()) ? "" : "Renamed"));
			}
			if (columns != null)
				tabs.remove(columns);
			columns = new ReportDetailTablePanel(new ReportReviewService.DetailTable(
					"Package columns: " + table.sourceTable(),
					"Source types and source constraints; requested names are not resolved target metadata. Target-only generated PK columns are not packaged fields.",
					List.of("Order", "Source column", "Requested target column", "Source type", "Source NOT NULL",
							"Source PK", "Name change"),
					columnRows));
			columns.setNumericColumns(List.of(0));
			var currentColumns = columns;
			showRenamed = new JButton("Show renamed columns");
			showRenamed.setEnabled(columnRows.stream().anyMatch(row -> !row.get(6).isEmpty()));
			showAllColumns = new JButton("Show all columns");
			showRenamed.addActionListener(action -> {
				if (current == generation && columns == currentColumns) {
					currentColumns.filter.setText("");
					currentColumns.setNonBlankFilter(6, true);
				}
			});
			showAllColumns.addActionListener(action -> {
				if (current == generation && columns == currentColumns) {
					currentColumns.filter.setText("");
					currentColumns.setNonBlankFilter(6, false);
				}
			});
			var columnControls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
			columnControls.add(showRenamed);
			columnControls.add(showAllColumns);
			columns.setScopeControls(columnControls);
			tabs.addTab("Package columns", columns);
			tabs.setSelectedComponent(columns);
		});
		tabs.addTab("Package tables (" + rows.size() + ")", panel);
	}

	private static boolean matches(TransferFileImportService.PackageDescription description, Object value) {
		if (description == null || description.snapshot() == null || description.snapshot().fingerprint() == null)
			return false;
		String fingerprint = value instanceof FileTransferDesktopService.Reviewed reviewed
				? reviewed.review().manifestFingerprint()
				: value instanceof TransferFileImportService.Outcome outcome ? outcome.manifestFingerprint() : null;
		return description.snapshot().fingerprint().equals(fingerprint);
	}

	private static String identity(String... parts) {
		return Arrays.stream(parts).filter(Objects::nonNull).filter(part -> !part.isEmpty())
				.map(part -> "\"" + part.replace("\"", "\"\"") + "\"")
				.collect(java.util.stream.Collectors.joining("."));
	}
}
