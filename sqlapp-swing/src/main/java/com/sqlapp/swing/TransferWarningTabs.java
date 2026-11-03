/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTabbedPane;
import com.sqlapp.data.db.command.migration.transfer.*;

/** Searchable recorded warnings; this view never grants execution approval. */
final class TransferWarningTabs {
	private static final String FILE_INSERT = "[FILE_FULL_SCAN_INSERT]";
	private static final String FILE_INSERT_GUIDANCE = "Import uses non-resumable INSERT into empty targets, preserving duplicates. WARN permits this route; ERROR stops target review before writes. Verification compares unordered hashes including duplicate counts; hash positions are not source row numbers. MERGE, resume and key-based repair are unavailable.";
	private ReportDetailTablePanel panel;
	private record Choice(String value, String label, long count) {
		@Override public String toString() { return label + " (" + count + ")"; }
	}
	javax.swing.JComboBox<Choice> categorySelector;
	javax.swing.JComboBox<Choice> taskSelector;
	javax.swing.JButton showAll;
	private long generation;

	void clear(JTabbedPane tabs) {
		generation++;
		if (panel != null)
			tabs.remove(panel);
		panel = null;
		categorySelector = null;
		taskSelector = null;
		showAll = null;
	}

	void show(JTabbedPane tabs, Object value) {
		show(tabs, value, null);
	}

	void show(JTabbedPane tabs, Object value, FileTransferDesktopService.Reviewed prior) {
		clear(tabs);
		final var rows = rows(value, prior);
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
		categorySelector = choices(rows, 0, "All categories");
		taskSelector = choices(rows, 1, "All tasks");
		categorySelector.setPrototypeDisplayValue(new Choice(null, "Target / Name mapping", 9999));
		taskSelector.setPrototypeDisplayValue(new Choice(null, "export-table-123456", 9999));
		var controls = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		var categoryLabel = new javax.swing.JLabel("Category"); categoryLabel.setLabelFor(categorySelector);
		var taskLabel = new javax.swing.JLabel("Task"); taskLabel.setLabelFor(taskSelector);
		controls.add(categoryLabel); controls.add(categorySelector); controls.add(taskLabel); controls.add(taskSelector);
		showAll = new javax.swing.JButton("Show all warnings"); controls.add(showAll);
		categorySelector.setToolTipText("Counts cover all retained warnings, before category, task or text filtering.");
		taskSelector.setToolTipText("Only explicitly recorded task IDs are used. Unassigned warnings are not linked by parsing message text.");
		panel.setScopeControls(controls);
		long current = generation;
		Runnable filter = () -> {
			if (current != generation || !panel.table.isEnabled()) return;
			var scopes = new java.util.HashMap<Integer, String>();
			String category = ((Choice) categorySelector.getSelectedItem()).value();
			String task = ((Choice) taskSelector.getSelectedItem()).value();
			if (category != null) scopes.put(0, category);
			if (task != null) scopes.put(1, task);
			panel.setExactFilters(scopes);
		};
		categorySelector.addActionListener(event -> filter.run());
		taskSelector.addActionListener(event -> filter.run());
		showAll.addActionListener(event -> {
			if (current != generation || !panel.table.isEnabled()) return;
			panel.filter.setText(""); categorySelector.setSelectedIndex(0); taskSelector.setSelectedIndex(0);
			panel.setExactFilters(java.util.Map.of());
		});
		tabs.addTab("Warnings (" + rows.size() + ")", panel);
	}

	private static javax.swing.JComboBox<Choice> choices(List<List<String>> rows, int column, String all) {
		var choices = new javax.swing.JComboBox<Choice>();
		choices.addItem(new Choice(null, all, rows.size()));
		var counts = rows.stream().collect(java.util.stream.Collectors.groupingBy(row -> row.get(column),
				java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
		counts.forEach((value, count) -> choices.addItem(new Choice(value, value.isEmpty() ? "No recorded task" : value, count)));
		return choices;
	}

	static List<List<String>> rows(Object value) {
		return rows(value, null);
	}

	static List<List<String>> rows(Object value, FileTransferDesktopService.Reviewed prior) {
		final var rows = new ArrayList<List<String>>();
		if (value instanceof DirectTransferDesktopService.Reviewed reviewed)
			compatibility(rows, reviewed.review().prepared().warnings());
		else if (value instanceof DirectTransferDesktopService.Executed executed)
			compatibility(rows, executed.outcome().prepared().warnings());
		else if (value instanceof FileTransferDesktopService.Reviewed reviewed) {
			export(rows, reviewed.manifest());
			compatibility(rows, reviewed.review().schemaPreparation().warnings());
		} else if (value instanceof TransferFileImportService.Outcome outcome) {
			if (TransferRecordedTargets.matches(outcome, prior) && prior.manifest() != null) export(rows, prior.manifest());
			compatibility(rows, outcome.schemaPreparation().warnings());
		} else if (value instanceof TransferFilePackageIO.Snapshot snapshot)
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
			if (warning.startsWith(FILE_INSERT)) {
				rows.add(List.of("Import / File INSERT", "", "", warning, FILE_INSERT_GUIDANCE));
				continue;
			}
			if (warning.startsWith(TransferKeylessChecks.CODE)) {
				rows.add(List.of("Source / Keyless INSERT", "", "", warning,
						"Review full-scan INSERT and multiset-hash verification. Do not use MERGE, resume or key-based repair. For new keyless target tables, optionally choose generated target PK creation; inspect partial data before any retry."));
				continue;
			}
			if (warning.startsWith(TransferKeylessChecks.GENERATED_TARGET_KEY)) {
				rows.add(List.of("Target / Generated PK", "", "", warning,
						"Review the generated PK column (default: ID; configurable as generatedKeyColumn) and identity syntax in Schema SQL. Source values and FK mappings are unchanged; this is target-only key creation, not a source row identity or a resume key."));
				continue;
			}
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
			} else if (warning.startsWith(TransferSchemaChangeChecks.MAPPING_EXPRESSIONS)) {
				category = "Target / Name mapping";
				action = "Compare the mapping with Schema SQL. SQL expressions retain source identifiers verbatim; inspect checks, defaults, generated expressions and predicates. Saving an edited SQL draft does not change the reviewed job or authorize execution.";
			}
			rows.add(List.of(category, "", "", warning, action));
		}
	}

	private static void export(List<List<String>> rows, TransferFilePackageIO.Manifest manifest) {
		for (var warning : manifest.warnings())
			rows.add(List.of(warning.message().startsWith(FILE_INSERT) ? "Package export / File INSERT" : "Package export",
					warning.taskId(), warning.column(), warning.message(), warning.message().startsWith(FILE_INSERT) ? FILE_INSERT_GUIDANCE
							: "Inspect export warnings and resolve unsupported value/type conversions before importing."));
	}
}
