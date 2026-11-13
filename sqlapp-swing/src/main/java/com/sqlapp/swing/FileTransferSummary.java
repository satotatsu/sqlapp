/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.math.BigInteger;
import java.nio.file.Path;
import com.sqlapp.data.db.command.migration.transfer.*;

/**
 * Human-readable view of recorded results; detailed evidence remains in the
 * JSON.
 */
final class FileTransferSummary {
	private FileTransferSummary() {
	}

	static String readyForImport(TransferFilePackageIO.Snapshot exported) {
		final var text = new StringBuilder("Ready for target review. No import has started.\n\n");
		packageDetails(text, exported.manifest());
		return text.append("\nManifest: ").append(exported.file())
				.append("\n\nNext: enter or load the target connection, then choose Review import.\n")
				.append("The source connection is not needed. The Gradle tab provides target-only import settings.\n")
				.toString();
	}

	static String text(Object value) {
		final var text = new StringBuilder();
		if (value instanceof FileTransferDesktopService.Inspected inspected) {
			text.append("Package file integrity checked. No database connection was opened.\n")
					.append(inspected.valuesValidated()
							? "Packaged values and recorded row counts checked. Target compatibility has not been reviewed.\n\n"
							: "Packaged values, recorded row counts and target compatibility have not been reviewed.\n\n");
			if (inspected.configuration() != null) {
				final var configuration = inspected.configuration();
				text.append("\nSaved target schema: ")
						.append(configuration.targetSchema() == null ? "current schema of the import connection"
								: configuration.targetSchema())
						.append('\n').append("Target table preparation: ").append(configuration.schemaMode())
						.append('\n').append("Verification after import: ")
						.append(configuration.verify() ? "enabled" : "disabled").append('\n').append("Insert method: ")
						.append(configuration.effectiveInsertMethod()).append('\n').append("Compatibility policy: ")
						.append(configuration.compatibilityPolicy()).append('\n');
			}
			if (inspected.description() != null)
				text.append(
						"\nPackage tables shows requested names and source metadata. Select a table and Show columns for the name correspondence.\n");
			packageDetails(text, inspected.snapshot().manifest());
			text.append("\nManifest: ").append(inspected.snapshot().file()).append('\n').append("\nFingerprint: ")
					.append(inspected.snapshot().fingerprint()).append('\n')
					.append("\nNext: enter the target connection and choose Review import.\n")
					.append("File hashes are integrity checks, not proof of authenticity or import approval.\n");
		} else if (value instanceof TransferFilePackageIO.Snapshot exported) {
			text.append("file package exported. No rows have been imported.\n\n");
			packageDetails(text, exported.manifest());
			text.append("\nManifest: ").append(exported.file()).append('\n');
			text.append("\nNext: choose Import file package, enter the target connection, and review.\n")
					.append("Move the whole package if importing on another machine.\n")
					.append("The Gradle tab contains settings for this package and optional re-export.\n");
		} else if (value instanceof FileTransferDesktopService.Reviewed reviewed) {
			text.append("Package and target reviewed. No tables or rows have been created.\n\n");
			TransferGeneratedKeyNotice.append(text, reviewed.review().schemaPreparation().warnings());
			TransferNameAdjustmentNotice.append(text, reviewed.review().schemaPreparation().warnings());
			text.append("Target: ").append(reviewed.review().targetIdentity().productName()).append(" / ")
					.append(reviewed.review().targetIdentity().productVersion()).append('\n');
			packageDetails(text, reviewed.manifest());
			if (reviewed.review().staging() != null)
				text.append("\nExisting rows: replace after staging and verification.\n");
			text.append("\nMissing-table DDL statements: ")
					.append(reviewed.review().schemaPreparation().statements().size()).append('\n');
			warnings(text, reviewed.review().schemaPreparation().warnings());
			text.append("\nManifest: ").append(reviewed.review().manifestFile()).append('\n')
					.append("\nNext: inspect warnings and Schema SQL, then confirm Execute reviewed import.\n")
					.append("The source connection is not needed. The Gradle tab provides target-only settings.\n");
		} else if (value instanceof TransferFileImportService.Outcome outcome) {
			if (outcome.staging() != null) {
				var staging = outcome.staging();
				text.append(staging.state().summary()).append('\n');
				text.append("Import execution phase: ").append(outcome.phase()).append('\n');
				TransferExecutionSummary.appendFailure(text, outcome.failureDiagnostic());
				if (staging.state().committed())
					text.append("Rows committed: ")
							.append(staging.committedRows().values().stream().mapToLong(Long::longValue).sum())
							.append('\n');
				if (staging.state() != TransferStagedReplacement.State.COMMITTED
						&& staging.state() != TransferStagedReplacement.State.NOT_STARTED)
					staging.targets().stream().filter(table -> !staging.cleanedStages().contains(table.stageTable()))
							.forEach(table -> text.append("Staging table to check: ").append(table.schema()).append('.')
									.append(table.stageTable()).append('\n'));
				if (outcome.stoppedTaskId() != null)
					text.append("Stopped task: ").append(outcome.stoppedTaskId()).append('\n');
				if (!outcome.verification().isEmpty())
					TransferExecutionSummary.appendVerificationGuide(text);
				return text.append("\nAudit report: ")
						.append(outcome.auditFile() == null ? "unavailable" : outcome.auditFile()).toString();
			}
			TransferExecutionSummary.append(text, outcome.phase());
			text.append("Import execution phase: ").append(outcome.phase()).append('\n');
			TransferExecutionSummary.appendFailure(text, outcome.failureDiagnostic());
			TransferGeneratedKeyNotice.append(text, outcome.schemaPreparation().warnings());
			TransferNameAdjustmentNotice.append(text, outcome.schemaPreparation().warnings());
			TransferNextSteps.append(text, outcome.phase(), outcome.verificationRequested(),
					outcome.schemaPreparation().warnings());
			if (outcome.copyResult() != null)
				text.append("Completed tables: ").append(outcome.copyResult().getTasks().size())
						.append("\nProcessed rows in completed tables: ")
						.append(outcome.copyResult().getProcessedRows()).append('\n');
			else
				text.append("Copy result: unavailable; inspect retained committed progress.\n");
			text.append("Verification: ");
			if (!outcome.verificationRequested())
				text.append("disabled by job configuration");
			else if (outcome.verification().isEmpty())
				text.append("not completed");
			else if (outcome.verification().values().stream().anyMatch(result -> !result.isMatch()))
				text.append("mismatch; inspect Details");
			else
				text.append("matched for ").append(outcome.verification().size()).append(" recorded tables");
			text.append('\n');
			if (!outcome.verification().isEmpty())
				TransferExecutionSummary.appendVerificationGuide(text);
			outcome.committedProgress().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
					.forEach(entry -> text.append("Committed rows / ").append(entry.getKey()).append(": ")
							.append(entry.getValue().getProcessedRowsAfter()).append('\n'));
			final var schema = outcome.schemaPreparation();
			text.append("DDL statements completed: ").append(schema.completedStatements()).append(" / ")
					.append(schema.planned().size()).append('\n');
			if (schema.attemptedStatement() != null)
				text.append("Uncertain attempted DDL statement: ").append(schema.attemptedStatement()).append('\n');
			TransferExecutionSummary.appendSchemaProgress(text, schema);
			if (outcome.stoppedTaskId() != null)
				text.append("Stopped task: ").append(outcome.stoppedTaskId()).append('\n');
			warnings(text, schema.warnings());
			text.append("Package export warnings remain in the package manifest and review.\n")
					.append("\nAudit report: ")
					.append(outcome.auditFile() == null ? "unavailable" : outcome.auditFile()).append('\n');
			if (outcome.phase() == TransferJobExecutionService.Phase.COMPLETE)
				text.append("\nInspect verification and warnings before using the migrated data.\n");
			else
				text.append(
						"\nImport did not complete. Created objects and committed rows may remain.\nInspect Details and repair the target before another review; no automatic retry or reset.\n");
		} else
			throw new IllegalArgumentException("Unsupported file result type.");
		return text.toString();
	}

	private static void packageDetails(StringBuilder text, TransferFilePackageIO.Manifest manifest) {
		text.append("File format: ").append(manifest.files().format()).append("; encoding: ")
				.append(manifest.files().encoding()).append('\n');
		if (manifest.files().format() != TransferJobConfiguration.FileFormat.JSONL)
			text.append("Unquoted empty fields: ").append(manifest.files().emptyFieldMode()).append("; NULL token: ")
					.append(manifest.files().nullValue() == null ? "not used"
							: new com.sqlapp.util.JsonConverter().toJsonString(manifest.files().nullValue()))
					.append('\n');
		if (manifest.files().format() != TransferJobConfiguration.FileFormat.JSONL)
			text.append("Header handling: ").append(switch (manifest.files().headerMode()) {
			case VALIDATE -> "validate column names and skip one header record";
			case SKIP -> "skip one header record without checking names; use captured column order";
			case NONE -> "no header; first record is data in captured column order";
			}).append('\n');
		text.append("Tables: ").append(manifest.tables().size()).append("; exported rows: ").append(manifest.tables()
				.stream().map(table -> BigInteger.valueOf(table.rows())).reduce(BigInteger.ZERO, BigInteger::add))
				.append('\n');
		for (var table : manifest.tables())
			text.append("  ").append(table.catalog() == null ? "" : table.catalog() + ".")
					.append(table.schema() == null ? "" : table.schema() + ".").append(table.table()).append(": ")
					.append(table.rows()).append(" rows\n");
		text.append("\nExport warnings: ").append(manifest.warnings().size()).append('\n');
		for (var warning : manifest.warnings())
			text.append("  ").append(warning.taskId()).append(" / ").append(warning.column()).append(": ")
					.append(warning.message()).append('\n');
	}

	private static void warnings(StringBuilder text, java.util.List<String> warnings) {
		text.append("\nCompatibility warnings: ").append(warnings.size()).append('\n');
		warnings.forEach(warning -> text.append("  ").append(warning).append('\n'));
	}

	/**
	 * Schema SQL tab content, or null when the result has no target preparation
	 * SQL. Protected inputs are null when the draft cannot be offered for editing.
	 */
	static TransferSchemaSql schemaSql(final Object value, final FileTransferDesktopService.Reviewed prior,
			final java.util.List<Path> sourceFiles) {
		final var statements = value instanceof FileTransferDesktopService.Reviewed review
				? review.review().schemaPreparation().statements()
				: value instanceof TransferFileImportService.Outcome outcome ? outcome.schemaPreparation().planned()
						: java.util.List.<com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation.Statement>of();
		final boolean hasSql = !statements.isEmpty()
				|| value instanceof FileTransferDesktopService.Reviewed review && review.review().staging() != null
				|| value instanceof TransferFileImportService.Outcome outcome && outcome.staging() != null;
		if (!hasSql)
			return null;
		final var text = new StringBuilder(
				"-- Reviewed missing-table preparation. DDL may remain applied after failure.\n\n");
		final var warnings = value instanceof FileTransferDesktopService.Reviewed review
				? review.review().schemaPreparation().warnings()
				: ((TransferFileImportService.Outcome) value).schemaPreparation().warnings();
		for (String warning : warnings)
			text.append("-- WARNING: ").append(warning.replace('\n', ' ').replace('\r', ' ')).append('\n');
		TransferSchemaSql.appendStatements(text, statements);
		final var context = value instanceof FileTransferDesktopService.Reviewed review ? review : prior;
		String draft = value instanceof FileTransferDesktopService.Reviewed review ? review.schemaSqlDraft()
				: FileTransferDesktopService.draftFor((TransferFileImportService.Outcome) value, prior);
		if (draft != null && value instanceof TransferFileImportService.Outcome outcome)
			draft = "-- RECORDED IMPORT: " + outcome.phase() + "; completed statements: "
					+ outcome.schemaPreparation().completedStatements() + "; uncertain attempt: "
					+ outcome.schemaPreparation().attemptedStatement()
					+ "\n-- FULL planned SQL includes statements that may already be applied. Inspect and repair the target before retrying.\n"
					+ "-- This is not an automatic resume script.\n\n" + draft;
		java.util.List<Path> inputs = null;
		if (draft != null && context != null) {
			inputs = new java.util.ArrayList<>();
			protectPackage(inputs, context.review().manifestFile().toAbsolutePath().getParent(), context.manifest());
			if (value instanceof TransferFileImportService.Outcome outcome && outcome.auditFile() != null) {
				inputs.add(outcome.auditFile());
				protectPackage(inputs, outcome.auditFile().toAbsolutePath().getParent(), context.manifest());
			}
			inputs.addAll(sourceFiles);
		}
		return new TransferSchemaSql(draft == null ? text.toString() : draft, draft, inputs);
	}

	private static void protectPackage(java.util.List<Path> inputs, Path folder,
			TransferFilePackageIO.Manifest manifest) {
		for (var name : java.util.List.of("manifest.json", "transfer.yaml", "source-schema.xml",
				"transfer-mapping.yaml"))
			inputs.add(folder.resolve(name));
		for (var table : manifest.tables())
			inputs.add(folder.resolve(table.file()));
	}
}
