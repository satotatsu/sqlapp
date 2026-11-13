/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.sqlapp.data.db.command.migration.transfer.TransferStagingComparisonReportIO.Report;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement.TableComparison;

/**
 * Session-only comparison of recorded counts and statuses, never row contents.
 */
final class StagingComparisonHistory {
	private Report previous;

	void clear() {
		previous = null;
	}

	ReportReviewService.Review show(Report current, ReportReviewService.Review view) {
		var prior = previous;
		previous = current;
		if (prior == null || !prior.sourceResultFingerprint().equals(current.sourceResultFingerprint())
				|| !prior.targetIdentity().equals(current.targetIdentity()))
			return view;
		var before = prior.comparison().tables().stream()
				.collect(Collectors.toMap(TableComparison::target, Function.identity()));
		var after = current.comparison().tables().stream()
				.collect(Collectors.toMap(TableComparison::target, Function.identity()));
		if (!before.keySet().equals(after.keySet()))
			return view;
		var rows = new ArrayList<List<String>>();
		for (var entry : current.comparison().tables()) {
			var old = before.get(entry.target());
			if (old.equals(entry))
				continue;
			var name = entry.target();
			rows.add(List.of(name.taskId(), text(name.catalog()), text(name.schema()), name.table(),
					Long.toString(old.stagingRows()), Long.toString(entry.stagingRows()),
					Long.toString(old.targetRows()), Long.toString(entry.targetRows()), match(old.matchesTarget()),
					match(entry.matchesTarget()), staging(old.matchesRecordedStaging()),
					staging(entry.matchesRecordedStaging()), change(old, entry)));
		}
		var sections = new ArrayList<>(view.tables());
		sections.add(new ReportReviewService.DetailTable("Changes since previous comparison", rows.size()
				+ " table(s) with changed counts or match statuses since the previous successful comparison in this session. "
				+ "Change describes both match statuses: Mixed status changes means one improved while the other worsened. "
				+ "An improvement may still leave another mismatch. "
				+ "Counts and match statuses only: unchanged values do not prove unchanged data. The original commit remains unconfirmed.",
				List.of("Task ID", "Catalog", "Schema", "Target table", "Previous staging rows", "Current staging rows",
						"Previous target rows", "Current target rows", "Previous target vs staging",
						"Current target vs staging", "Previous staging vs recorded", "Current staging vs recorded",
						"Change"),
				rows, List.of(4, 5, 6, 7)));
		return new ReportReviewService.Review(view.summary(), view.columns(), view.rows(), view.details(), sections,
				view.numericColumns());
	}

	private static String change(TableComparison before, TableComparison after) {
		boolean improved = !before.matchesTarget() && after.matchesTarget()
				|| !before.matchesRecordedStaging() && after.matchesRecordedStaging();
		boolean worsened = before.matchesTarget() && !after.matchesTarget()
				|| before.matchesRecordedStaging() && !after.matchesRecordedStaging();
		if (improved && worsened)
			return "Mixed status changes";
		if (worsened)
			return "New mismatch";
		if (improved)
			return "Match status improved";
		return "Row counts changed";
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	private static String match(boolean value) {
		return value ? "Match" : "Different";
	}

	private static String staging(boolean value) {
		return value ? "Unchanged" : "Changed";
	}
}
