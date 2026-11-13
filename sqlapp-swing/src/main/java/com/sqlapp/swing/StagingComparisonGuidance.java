/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement.Comparison;

/**
 * Interprets current data evidence without inferring the outcome of a past
 * commit.
 */
final class StagingComparisonGuidance {
	private StagingComparisonGuidance() {
	}

	static String describe(Comparison comparison) {
		if (comparison.tables().isEmpty())
			return "No tables were compared. There is no data evidence to assess.";
		if (comparison.tables().stream().anyMatch(table -> !table.matchesRecordedStaging()))
			return "Staging data has changed since the recorded verification. A target match against this staging data "
					+ "does not establish a match with the original copied data. Inspect the changed staging tables "
					+ "and the original result before deciding how to recover.";
		if (comparison.tables().stream().anyMatch(table -> !table.matchesTarget()))
			return "The target differs from the verified staging data. Inspect the differing tables and application "
					+ "activity before planning a retry. Keep the staging tables and original result for investigation.";
		return "All compared target rows match the verified staging data. This describes the copied columns "
				+ "at comparison time. Check application activity before deciding whether another transfer is needed.";
	}
}
