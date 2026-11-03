/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.sqlapp.data.db.command.migration.transfer.TransferFileImportService;
import com.sqlapp.jdbc.bulk.BulkMigrationVerificationResult;

/**
 * Bounded, read-only views of recorded comparisons; never reruns verification.
 */
final class TransferVerificationTabs {
	static final int PAGE_SIZE = 200;
	private static final List<String> CHUNK_COLUMNS = List.of("Chunk index (zero based)", "Status", "Expected rows",
			"Actual rows", "Expected hash", "Actual hash");

	private record Entry(String task, BulkMigrationVerificationResult result) {
	}

	ReportDetailTablePanel tables;
	ReportDetailTablePanel chunks;
	JButton showChunks;
	JButton showMismatches;
	JButton previous;
	JButton next;
	JCheckBox mismatchedChunks;
	JLabel page;
	private Entry selected;
	private long offset;
	private long generation;

	void clear(JTabbedPane tabs) {
		generation++;
		if (tables != null)
			tabs.remove(tables);
		if (chunks != null)
			tabs.remove(chunks);
		tables = null;
		chunks = null;
		selected = null;
		showChunks = null;
		showMismatches = null;
		previous = null;
		next = null;
		mismatchedChunks = null;
		page = null;
		offset = 0;
	}

	void show(JTabbedPane tabs, Object value) {
		show(tabs, value, null);
	}

	void show(JTabbedPane tabs, Object value, FileTransferDesktopService.Reviewed prior) {
		clear(tabs);
		var entries = entries(value);
		if (entries.isEmpty())
			return;
		var targets = TransferRecordedTargets.names(value, prior);
		long current = generation;
		var rows = entries.stream()
				.map(entry -> List.of(entry.task(), targets.getOrDefault(entry.task(), "not recorded"),
						status(entry.result().isMatch()), Long.toString(entry.result().getExpectedRows()),
						Long.toString(entry.result().getActualRows()),
						Integer.toString(entry.result().getChunks().size()),
						Long.toString(entry.result().getChunks().stream().filter(chunk -> !chunk.isMatch()).count()),
						order(entry.result())))
				.toList();
		tables = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Verification",
				"Recorded comparisons only. Missing tasks were not verified; matched recorded tasks do not prove the whole transfer completed. A mismatch may involve row counts or hashes. Select a task and Show chunks to inspect its recorded comparisons without connecting to a database.",
				List.of("Task", "Target (reviewed)", "Status", "Expected rows", "Actual rows", "Chunks",
						"Mismatched chunks", "Comparison order"),
				rows));
		tables.setNumericColumns(List.of(3, 4, 5, 6));
		tables.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		int[] widths = { 140, 240, 85, 100, 100, 75, 135, 225 };
		for (int column = 0; column < widths.length; column++)
			tables.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
		showChunks = new JButton("Show chunks");
		showMismatches = new JButton("Show mismatched tables");
		showMismatches.setEnabled(entries.stream().anyMatch(entry -> !entry.result().isMatch()));
		var all = new JButton("Show all tables");
		var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(showMismatches);
		controls.add(all);
		controls.add(showChunks);
		tables.setScopeControls(controls);
		Runnable selection = () -> {
			if (current != generation)
				return;
			showChunks.setEnabled(tables.table.isEnabled() && tables.table.getSelectedRowCount() == 1);
		};
		tables.table.getSelectionModel().addListSelectionListener(event -> selection.run());
		showMismatches.addActionListener(event -> {
			if (current != generation || !tables.table.isEnabled())
				return;
			tables.filter.setText("");
			tables.setExactFilter(2, "Mismatch");
			if (tables.table.getRowCount() > 0)
				tables.table.setRowSelectionInterval(0, 0);
		});
		all.addActionListener(event -> {
			if (current != generation || !tables.table.isEnabled())
				return;
			tables.filter.setText("");
			tables.setExactFilter(2, null);
		});
		showChunks.addActionListener(event -> {
			if (current != generation || !tables.table.isEnabled() || tables.table.getSelectedRowCount() != 1)
				return;
			int row = tables.table.convertRowIndexToModel(tables.table.getSelectedRow());
			selected = entries.get(row);
			offset = 0;
			if (chunks == null)
				createChunks(tabs, current);
			mismatchedChunks.setSelected(false);
			refreshChunks();
			tabs.setSelectedComponent(chunks);
		});
		selection.run();
		tabs.addTab("Verification (" + entries.size() + ")", tables);
	}

	private void createChunks(JTabbedPane tabs, long current) {
		chunks = new ReportDetailTablePanel(new ReportReviewService.DetailTable("Verification chunks",
				"Only this page is searched, sorted and copied. Chunk indexes are zero based, not source row numbers. Multiset comparisons sort hashes and preserve duplicate counts; their positions do not locate source records. Matching recorded chunks do not prove the whole transfer completed. No data or SQL is executed here.",
				CHUNK_COLUMNS, List.of()));
		previous = new JButton("Previous page");
		next = new JButton("Next page");
		mismatchedChunks = new JCheckBox("Mismatched chunks only");
		page = new JLabel();
		var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(mismatchedChunks);
		controls.add(previous);
		controls.add(next);
		var scope = new JPanel(new java.awt.BorderLayout(4, 4));
		scope.add(controls, java.awt.BorderLayout.NORTH);
		scope.add(page, java.awt.BorderLayout.CENTER);
		chunks.setScopeControls(scope);
		previous.addActionListener(event -> {
			if (current != generation || !chunks.table.isEnabled() || offset == 0)
				return;
			offset = Math.max(0, offset - PAGE_SIZE);
			refreshChunks();
		});
		next.addActionListener(event -> {
			if (current != generation || !chunks.table.isEnabled() || !next.isEnabled())
				return;
			offset += PAGE_SIZE;
			refreshChunks();
		});
		mismatchedChunks.addActionListener(event -> {
			if (current != generation || !chunks.table.isEnabled())
				return;
			offset = 0;
			refreshChunks();
		});
		tabs.addTab("Verification chunks", chunks);
	}

	private void refreshChunks() {
		var recorded = selected.result().getChunks();
		boolean onlyMismatch = mismatchedChunks.isSelected();
		long count = onlyMismatch ? recorded.stream().filter(chunk -> !chunk.isMatch()).count() : recorded.size();
		var model = new DefaultTableModel(CHUNK_COLUMNS.toArray(), 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		recorded.stream().filter(chunk -> !onlyMismatch || !chunk.isMatch()).skip(offset).limit(PAGE_SIZE)
				.forEach(chunk -> model.addRow(new Object[] { Long.toString(chunk.getIndex()), status(chunk.isMatch()),
						Integer.toString(chunk.getExpectedRows()), Integer.toString(chunk.getActualRows()),
						chunk.getExpectedHash(), chunk.getActualHash() }));
		chunks.table.setModel(model);
		chunks.setNumericColumns(List.of(0, 2, 3));
		chunks.table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		int[] widths = { 170, 100, 120, 120, 420, 420 };
		for (int column = 0; column < widths.length; column++)
			chunks.table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
		previous.setEnabled(offset > 0);
		next.setEnabled(offset + PAGE_SIZE < count);
		page.setText(selected.task() + " — " + (count == 0 ? "0" : offset + 1) + "–"
				+ Math.min(offset + PAGE_SIZE, count) + " of " + count + " chunk records; " + order(selected.result()));
		page.setToolTipText("Recorded chunks: " + recorded.size() + "; displayed rows are limited to " + PAGE_SIZE
				+ ". Return to Verification to select another task.");
	}

	private static String status(boolean match) {
		return match ? "Matched" : "Mismatch";
	}

	private static String order(BulkMigrationVerificationResult result) {
		if (result.isUnordered())
			return "Unordered multiset hashes";
		return result.getExpectedKeysetFingerprint() != null ? "Keyset order" : "Recorded chunk order";
	}

	private static List<Entry> entries(Object value) {
		var entries = new ArrayList<Entry>();
		if (value instanceof DirectTransferDesktopService.Executed executed
				&& executed.outcome().verificationResult() != null)
			executed.outcome().verificationResult().getTasks()
					.forEach(task -> entries.add(new Entry(task.getTaskId(), task.getVerificationResult())));
		else if (value instanceof TransferFileImportService.Outcome outcome)
			outcome.verification().entrySet().stream().sorted(Map.Entry.comparingByKey())
					.forEach(entry -> entries.add(new Entry(entry.getKey(), entry.getValue())));
		return List.copyOf(entries);
	}

	boolean showTask(JTabbedPane tabs, String task) {
		if (tables == null || tabs.indexOfComponent(tables) < 0)
			return false;
		var model = tables.table.getModel();
		for (int index = 0; index < model.getRowCount(); index++)
			if (model.getValueAt(index, 0).equals(task)) {
				tables.filter.setText("");
				tables.setExactFilter(2, null);
				int row = tables.table.convertRowIndexToView(index);
				tables.table.setRowSelectionInterval(row, row);
				tables.table.scrollRectToVisible(tables.table.getCellRect(row, 0, true));
				tabs.setSelectedComponent(tables);
				return true;
			}
		return false;
	}
}
