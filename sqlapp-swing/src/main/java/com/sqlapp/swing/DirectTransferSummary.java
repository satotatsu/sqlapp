/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sqlapp.data.db.command.migration.transfer.*;

/**
 * Human-readable view of a reviewed or executed direct transfer; detailed
 * evidence remains in the JSON.
 */
final class DirectTransferSummary {
	private DirectTransferSummary() {
	}

	/**
	 * @param recoveryAvailable a retained staging comparison can be started
	 * @param progressShown     the Progress tab is shown for this result
	 */
	static String text(final Object value, final boolean recoveryAvailable, final boolean progressShown) {
		final var text = new StringBuilder();
		if (value instanceof DirectTransferDesktopService.Reviewed reviewed) {
			final var review = reviewed.review();
			final var config = review.prepared().job().configuration();
			if (config.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY)
				text.append(config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
						? "SCHEMA ONLY: apply selected table differences using reviewed CREATE/ALTER/DROP SQL. No source rows are transferred or verified.\n"
						: "SCHEMA ONLY: create missing tables, constraints and indexes. No row copy or row verification.\n")
						.append(config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
								? "Existing selected tables may change. Unselected tables are outside the migration scope. The target schema must already exist.\n\n"
								: "Existing target tables are skipped without comparison or modification. The target schema must already exist.\n\n");
			if (TransferSchemaChangeChecks.executionBlocked(config, review.prepared().schemaPreparation()))
				text.append(
						"EXECUTION BLOCKED: data-impacting changes require explicit permission in Job settings and a new review. SQL remains available for inspection.\n\n");
			if (config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES)
				text.append("Data-impacting Schema changes allowed by saved job: ")
						.append(config.allowDestructiveSchemaChanges()).append("\n\n");
			if (TransferSchemaChangeChecks.unresolvedCount(review.prepared().schemaPreparation()) > 0)
				text.append("Unresolved table-change warnings: ")
						.append(TransferSchemaChangeChecks.unresolvedCount(review.prepared().schemaPreparation()))
						.append(". These changes still require manual work; see Warnings.\n\n");
			text.append("Transfer reviewed. No rows have been copied. Execution requires your confirmation.\n\n");
			TransferGeneratedKeyNotice.append(text, review.prepared().warnings());
			TransferNameAdjustmentNotice.append(text, review.prepared().warnings());
			text.append("Source: ").append(review.sourceIdentity().productName()).append(" / ")
					.append(review.sourceIdentity().productVersion()).append('\n').append("Target: ")
					.append(review.targetIdentity().productName()).append(" / ")
					.append(review.targetIdentity().productVersion()).append('\n').append("Selected tables (")
					.append(config.tables().size()).append("):\n");
			for (String table : config.tables())
				text.append("  ").append(table).append('\n');
			if (config.dataMode() != TransferJobConfiguration.DataMode.SCHEMA_ONLY) {
				text.append("\nInsert method: ").append(config.effectiveInsertMethod());
				if (review.staging() != null) {
					text.append(
							"\nExisting rows: preserved until staging is verified, then replaced in one transaction.\n");
					review.staging().targets()
							.forEach(target -> text.append("  ").append(target.schema()).append('.')
									.append(target.stageTable()).append(" → ").append(target.schema()).append('.')
									.append(target.table()).append('\n'));
				}
				text.append("\nChunk rows: ").append(config.chunkSize()).append("; verification: ")
						.append(config.verify() ? "enabled" : "disabled").append('\n');
			}
			text.append("Target preparation: ").append(config.schemaMode())
					.append(config.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
							? "; Schema SQL statements: "
							: "; DDL statements: ")
					.append(review.prepared().schemaPreparation().statements().size()).append('\n');
			warnings(text, review.prepared().warnings());
			text.append("\nReview report: ").append(reviewed.report());
			text.append("\nRun this saved job from Gradle: see the Gradle tab for setup and commands.");
		} else if (value instanceof DirectTransferDesktopService.Executed execution) {
			final var outcome = execution.outcome();
			final boolean schemaOnly = outcome.prepared().job().configuration()
					.dataMode() == TransferJobConfiguration.DataMode.SCHEMA_ONLY;
			if (schemaOnly)
				text.append(outcome.prepared().job().configuration()
						.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
								? "SCHEMA ONLY: no source rows were transferred. Reviewed Schema SQL may change target data when explicitly allowed.\n"
								: "SCHEMA ONLY: no data-copy or row-verification operations were requested.\n");
			if (schemaOnly && outcome.phase() == TransferJobExecutionService.Phase.COMPLETE)
				text.append(TransferSchemaChangeChecks.unresolvedCount(outcome.prepared().schemaPreparation()) > 0
						? "Generated Schema SQL completed, but unresolved table changes remain. See Warnings before using the target.\n"
						: "Schema-only transfer completed. Inspect DDL results and compatibility warnings below.\n");
			else if (outcome.staging() != null) {
				appendStagingOutcome(text, outcome.staging(), outcome.stoppedTaskId());
				if (recoveryAvailable)
					text.append("Enter the target password if needed, then choose Compare retained staging.\n");
			} else
				TransferExecutionSummary.append(text, outcome.phase());
			text.append("Execution phase: ").append(outcome.phase()).append('\n');
			TransferExecutionSummary.appendFailure(text, outcome.failureDiagnostic());
			if (progressShown)
				TransferExecutionSummary.appendProgressGuide(text);
			TransferGeneratedKeyNotice.append(text, outcome.prepared().warnings());
			TransferNameAdjustmentNotice.append(text, outcome.prepared().warnings());
			if (schemaOnly)
				text.append("Next: inspect DDL results and warnings; test target definitions before loading data.\n");
			else if (outcome.staging() == null)
				TransferNextSteps.append(text, outcome.phase(), outcome.prepared().job().configuration().verify(),
						outcome.prepared().warnings());
			if (schemaOnly)
				text.append("Row copy: not requested. Row verification: not applicable (schema only).\n");
			else if (outcome.staging() == null)
				text.append("Completed tables: ").append(outcome.completedTasks().size()).append('\n')
						.append("Processed rows: ")
						.append(schemaOnly ? "not applicable (schema only)"
								: outcome.copyResult() == null ? "see retained committed progress in Details"
										: outcome.copyResult().getProcessedRows())
						.append('\n').append("Verification: ")
						.append(schemaOnly ? "not applicable (schema only)"
								: !outcome.prepared().job().configuration().verify() ? "disabled"
										: outcome.verificationResult() == null ? "not completed"
												: outcome.verificationResult().isMatch() ? "matched" : "mismatch")
						.append('\n');
			if (outcome.verificationResult() != null && !outcome.verificationResult().getTasks().isEmpty())
				TransferExecutionSummary.appendVerificationGuide(text);
			warnings(text, outcome.prepared().warnings());
			text.append("\nResult report: ").append(execution.report() == null ? "unavailable" : execution.report());
			text.append(outcome.prepared().job().configuration()
					.schemaMode() == TransferJobConfiguration.SchemaMode.APPLY_DIFFERENCES
							? "\nSchema SQL statements completed: "
							: "\nDDL statements completed: ")
					.append(outcome.schemaPreparation().completedStatements()).append(" / ")
					.append(outcome.schemaPreparation().planned().size());
			if (outcome.schemaPreparation().attemptedStatement() != null)
				text.append("; uncertain attempted statement: ")
						.append(outcome.schemaPreparation().attemptedStatement());
			TransferExecutionSummary.appendSchemaProgress(text, outcome.schemaPreparation());
			if (outcome.staging() == null && outcome.phase() != TransferJobExecutionService.Phase.COMPLETE)
				text.append("\nCommitted chunks may remain. Inspect Details before retrying.");
		}
		return text.toString();
	}

	/** Schema SQL tab content, or null when the result has no target preparation SQL. */
	static TransferSchemaSql schemaSql(final Object value, final Path definitionFile, final List<Path> sourceFiles) {
		final var statements = value instanceof DirectTransferDesktopService.Reviewed review
				? review.review().prepared().schemaPreparation().statements()
				: ((DirectTransferDesktopService.Executed) value).outcome().schemaPreparation().planned();
		final boolean hasSql = !statements.isEmpty()
				|| value instanceof DirectTransferDesktopService.Reviewed review && review.review().staging() != null
				|| value instanceof DirectTransferDesktopService.Executed execution
						&& execution.outcome().staging() != null;
		if (!hasSql)
			return null;
		final var sql = new StringBuilder(
				"-- Reviewed target preparation. Created objects may remain after failure.\n\n");
		TransferSchemaSql.appendStatements(sql, statements);
		final var prepared = value instanceof DirectTransferDesktopService.Reviewed review
				? review.review().prepared()
				: ((DirectTransferDesktopService.Executed) value).outcome().prepared();
		String draft = value instanceof DirectTransferDesktopService.Reviewed review ? review.schemaSqlDraft()
				: ((DirectTransferDesktopService.Executed) value).schemaSqlDraft();
		if (draft != null && value instanceof DirectTransferDesktopService.Executed execution)
			draft = "-- RECORDED EXECUTION: " + execution.outcome().phase() + "; completed statements: "
					+ execution.outcome().schemaPreparation().completedStatements() + "; uncertain attempt: "
					+ execution.outcome().schemaPreparation().attemptedStatement()
					+ "\n-- This is the FULL planned SQL, including statements that may already be applied.\n"
					+ "-- Inspect and repair the target before retrying; this is not an automatic resume script.\n\n"
					+ draft;
		final var protectedInputs = new ArrayList<Path>();
		protectedInputs.addAll(sourceFiles);
		protectedInputs.add(prepared.job().file());
		protectedInputs.add(prepared.sourceSchemaFile());
		if (prepared.job().configuration().mappingFile() != null) {
			protectedInputs.add(prepared.directory().resolve("transfer-mapping.yaml"));
			if (definitionFile != null)
				protectedInputs.add(definitionFile.toAbsolutePath().getParent()
						.resolve(prepared.job().configuration().mappingFile()).normalize());
		}
		if (prepared.dataJob() != null)
			protectedInputs.add(prepared.dataJob().file());
		if (definitionFile != null)
			protectedInputs.add(definitionFile);
		final var report = value instanceof DirectTransferDesktopService.Reviewed review ? review.report()
				: ((DirectTransferDesktopService.Executed) value).report();
		if (report != null)
			protectedInputs.add(report);
		return new TransferSchemaSql(draft == null ? sql.toString() : draft, draft,
				Collections.unmodifiableList(protectedInputs));
	}

	private static void warnings(StringBuilder text, List<String> warnings) {
		text.append("\nCompatibility warnings: ").append(warnings.size()).append('\n');
		for (String warning : warnings)
			text.append("  ").append(warning).append('\n');
	}

	private static void appendStagingOutcome(StringBuilder text,
			com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement.Status staging,
			String stoppedTask) {
		text.append(staging.state().summary()).append('\n');
		if (staging.state().committed())
			text.append("Rows committed: ")
					.append(staging.appliedRows().values().stream().mapToLong(Long::longValue).sum()).append('\n');
		staging.targets().stream().filter(target -> target.taskId().equals(stoppedTask)).findFirst()
				.ifPresent(target -> text.append("Stopped at target: ").append(target.schema()).append('.')
						.append(target.table()).append('\n'));
		if (staging.stageOutcome() != null)
			text.append("Staging phase: ").append(staging.stageOutcome().phase()).append('\n');
		if (staging.stageOutcome() != null)
			staging.targets().stream().filter(target -> !staging.cleanedStages().contains(target.stageTable()))
					.forEach(target -> text.append("Staging table to check: ").append(target.schema()).append('.')
							.append(target.stageTable()).append('\n'));
	}
}
