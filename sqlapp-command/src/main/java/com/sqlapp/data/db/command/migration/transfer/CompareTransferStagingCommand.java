/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.File;
import java.nio.file.Path;

/**
 * Compares retained staging against the current target and saves separate
 * evidence.
 */
@lombok.Getter
@lombok.Setter
public class CompareTransferStagingCommand extends AbstractTransferFileCommand {
	private File resultFile;
	private File workspaceDirectory;
	private boolean failOnDifference;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private TransferStagedReplacement.Comparison result;
	@lombok.Setter(lombok.AccessLevel.NONE)
	private Path comparisonReport;

	/** Checks the saved bundle without borrowing a connection. */
	public void validateInputs() {
		checkPaths();
		SavedStagingComparison.read(resultFile.toPath());
	}

	private void checkPaths() {
		if (resultFile == null || workspaceDirectory == null)
			throw new IllegalArgumentException("resultFile and workspaceDirectory are required.");
	}

	@Override
	protected void doRun() {
		result = null;
		comparisonReport = null;
		withRuntimeSource(() -> {
			checkPaths();
			var report = TransferStagedReplacement.compareRetainedStageReport(resultFile.toPath(), getDataSource());
			result = report.comparison();
			comparisonReport = new TransferStagingComparisonReportIO().writeNew(workspaceDirectory.toPath(), report);
			long targetDifferences = result.tables().stream().filter(table -> !table.matchesTarget()).count();
			long stagingChanges = result.tables().stream().filter(table -> !table.matchesRecordedStaging()).count();
			info("Staging comparison: ", result.tables().size(), " table(s), ", targetDifferences,
					" target difference(s), ", stagingChanges, " staging change(s). Report: ", comparisonReport);
			if (failOnDifference && (targetDifferences != 0 || stagingChanges != 0))
				throw new IllegalStateException("Staging comparison found differences. See " + comparisonReport);
			return null;
		});
	}
}
