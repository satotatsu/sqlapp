/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.jdbc.bulk.*;

/** Retained task evidence, not inferred scheduling state or a restart plan. */
final class TransferExecutionProgressTabs {
	ReportDetailTablePanel panel;
	JButton showStopped;
	JButton showIssues;
	JButton showSql;
	JButton showVerification;
	private Consumer<String> statementNavigator;
	private Consumer<String> verificationNavigator;
	private long generation;

	void setNavigators(Consumer<String> sql, Consumer<String> verification) {
		statementNavigator = sql;
		verificationNavigator = verification;
	}

	void clear(JTabbedPane tabs) {
		generation++;
		if (panel != null)
			tabs.remove(panel);
		panel = null;
		showStopped = null;
		showIssues = null;
		showSql = null;
		showVerification = null;
	}

	void show(JTabbedPane tabs, Object value, FileTransferDesktopService.Reviewed prior) {
		clear(tabs);
		var evidence = evidence(value, prior);
		if (evidence == null || evidence.ids().isEmpty())
			return;
		var targets = TransferRecordedTargets.names(value, prior);
		var sql = TransferSchemaStatementTabs.rows(evidence.schema().planned(), evidence.schema());
		var sqlByTask = sql.stream().collect(java.util.stream.Collectors.groupingBy(row -> row.get(1)));
		var rows = new ArrayList<List<String>>();
		for (String id : evidence.ids()) {
			var statements = sqlByTask.getOrDefault(id, List.of());
			var issue = new ArrayList<String>();
			if (id.equals(evidence.stopped()))
				issue.add("Stopped task during " + evidence.phase());
			if (statements.stream().anyMatch(row -> row.get(2).equals("Uncertain attempt")))
				issue.add("Uncertain SQL attempt");
			var verification = evidence.verification().get(id);
			if (verification != null && !verification.isMatch())
				issue.add("Verification mismatch");
			String copy = evidence.schemaOnly() ? "Not requested (schema only)"
					: evidence.results().containsKey(id)
							? "Copy completed; processed rows: " + evidence.results().get(id)
							: evidence.completed().contains(id) ? "Copy completion recorded; result unavailable"
									: "No copy completion recorded";
			var committed = evidence.progress().get(id);
			String schema = statements.isEmpty() ? "No SQL recorded"
					: statements.stream().filter(row -> row.get(2).equals("Completed")).count() + "/"
							+ statements.size() + " completed; uncertain: "
							+ statements.stream().filter(row -> row.get(2).equals("Uncertain attempt")).count();
			rows.add(List.of(id, targets.getOrDefault(id, "not recorded"), String.join("; ", issue), copy,
					committed == null ? "not recorded" : Long.toString(committed.getProcessedRowsAfter()),
					verification != null ? verification.isMatch() ? "Matched" : "Mismatch"
							: evidence.schemaOnly() ? "Not applicable"
									: !evidence.verify() ? "Not requested" : "Not recorded",
					schema));
		}
		panel = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Execution progress",
				"Recorded task evidence, not live database state. No completion record does not prove a task was never started. Committed rows are the last chunk record; missing progress is not zero or proof of rollback. Copy completion does not prove verification passed. This view never retries, resumes or changes data.",
				List.of("Task", "Target (reviewed)", "Recorded issue", "Copy evidence", "Committed rows (chunk record)",
						"Verification", "Schema SQL"),
				rows));
		panel.setNumericColumns(List.of(4));
		panel.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		int[] widths = { 140, 220, 230, 260, 200, 120, 200 };
		for (int index = 0; index < widths.length; index++)
			panel.table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
		long current = generation;
		showStopped = new JButton("Show stopped task");
		showStopped.setEnabled(evidence.stopped() != null && evidence.ids().contains(evidence.stopped()));
		showIssues = new JButton("Show recorded issues");
		showIssues.setEnabled(rows.stream().anyMatch(row -> !row.get(2).isBlank()));
		var all = new JButton("Show all tasks");
		showSql = new JButton("Show task SQL");
		showVerification = new JButton("Show task verification");
		var filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
		filters.add(showStopped);
		filters.add(showIssues);
		filters.add(all);
		var navigation = new JPanel(new FlowLayout(FlowLayout.LEFT));
		navigation.add(showSql);
		navigation.add(showVerification);
		var controls = new JPanel(new BorderLayout());
		controls.add(filters, BorderLayout.NORTH);
		controls.add(navigation, BorderLayout.CENTER);
		panel.setScopeControls(controls);
		Runnable refresh = () -> {
			if (current != generation)
				return;
			String task = selectedTask();
			showSql.setEnabled(task != null && statementNavigator != null && sqlByTask.containsKey(task));
			showVerification.setEnabled(
					task != null && verificationNavigator != null && evidence.verification().containsKey(task));
		};
		panel.table.getSelectionModel().addListSelectionListener(event -> refresh.run());
		showStopped.addActionListener(event -> {
			if (current != generation || !panel.table.isEnabled() || evidence.stopped() == null)
				return;
			panel.filter.setText("");
			panel.setNonBlankFilter(2, false);
			panel.setExactFilter(0, evidence.stopped());
			if (panel.table.getRowCount() > 0)
				panel.table.setRowSelectionInterval(0, 0);
		});
		showIssues.addActionListener(event -> {
			if (current != generation || !panel.table.isEnabled())
				return;
			panel.filter.setText("");
			panel.setExactFilters(Map.of());
			panel.setNonBlankFilter(2, true);
		});
		all.addActionListener(event -> {
			if (current != generation || !panel.table.isEnabled())
				return;
			panel.filter.setText("");
			panel.setExactFilters(Map.of());
			panel.setNonBlankFilter(2, false);
		});
		showSql.addActionListener(event -> {
			if (current != generation)
				return;
			refresh.run();
			if (showSql.isEnabled())
				statementNavigator.accept(selectedTask());
		});
		showVerification.addActionListener(event -> {
			if (current != generation)
				return;
			refresh.run();
			if (showVerification.isEnabled())
				verificationNavigator.accept(selectedTask());
		});
		refresh.run();
		tabs.addTab("Execution progress (" + rows.size() + ")", panel);
	}

	private String selectedTask() {
		return panel.table.isEnabled() && panel.table.getSelectedRowCount() == 1 ? panel.table.getModel()
				.getValueAt(panel.table.convertRowIndexToModel(panel.table.getSelectedRow()), 0).toString() : null;
	}

	private record Evidence(Set<String> ids, Set<String> completed, Map<String, Long> results,
			Map<String, ChunkedBulkMigrationProgress> progress,
			Map<String, BulkMigrationVerificationResult> verification, String stopped,
			TransferJobExecutionService.Phase phase, boolean schemaOnly, boolean verify,
			TransferSchemaPreparation.Progress schema) {
	}

	private static Evidence evidence(Object value, FileTransferDesktopService.Reviewed prior) {
		var ids = new LinkedHashSet<String>();
		var completed = new HashSet<String>();
		var results = new HashMap<String, Long>();
		var verification = new HashMap<String, BulkMigrationVerificationResult>();
		BulkMigrationJobResult copy;
		Map<String, ChunkedBulkMigrationProgress> progress;
		String stopped;
		TransferJobExecutionService.Phase phase;
		TransferSchemaPreparation.Progress schema;
		boolean schemaOnly = false;
		boolean verify;
		if (value instanceof DirectTransferDesktopService.Executed execution) {
			var outcome = execution.outcome();
			if (outcome.staging() != null) return null;
			ids.addAll(outcome.prepared().taskIds());
			completed.addAll(outcome.completedTasks());
			if (outcome.verificationResult() != null)
				outcome.verificationResult().getTasks()
						.forEach(task -> verification.put(task.getTaskId(), task.getVerificationResult()));
			copy = outcome.copyResult();
			progress = outcome.committedProgress();
			stopped = outcome.stoppedTaskId();
			phase = outcome.phase();
			schema = outcome.schemaPreparation();
			schemaOnly = outcome.prepared().job().configuration()
					.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY;
			verify = outcome.prepared().job().configuration().verify();
		} else if (value instanceof TransferFileImportService.Outcome outcome) {
			if (outcome.staging() != null) return null;
			if (TransferRecordedTargets.matches(outcome, prior) && prior.manifest() != null)
				prior.manifest().tables().forEach(table -> ids.add(table.taskId()));
			copy = outcome.copyResult();
			progress = outcome.committedProgress();
			verification.putAll(outcome.verification());
			stopped = outcome.stoppedTaskId();
			phase = outcome.phase();
			schema = outcome.schemaPreparation();
			verify = outcome.verificationRequested();
		} else
			return null;
		var plan = TransferRecordedTargets.plan(value, prior);
		if (plan != null)
			plan.tables().forEach(table -> ids.add(table.taskId()));
		schema.planned().forEach(statement -> ids.add(statement.taskId()));
		if (copy != null)
			copy.getTasks()
					.forEach(task -> results.put(task.getTaskId(), task.getMigrationResult().getProcessedRows()));
		ids.addAll(new TreeSet<>(completed));
		ids.addAll(new TreeSet<>(results.keySet()));
		ids.addAll(new TreeSet<>(progress.keySet()));
		ids.addAll(new TreeSet<>(verification.keySet()));
		if (stopped != null)
			ids.add(stopped);
		return new Evidence(ids, completed, results, progress, verification, stopped, phase, schemaOnly, verify,
				schema);
	}
}
