/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.math.BigInteger;
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
				.append("\n\nNext: enter or load the target connection, then choose Review package and target.\n")
				.append("The source connection is not needed. The Gradle tab provides target-only import settings.\n")
				.toString();
	}

	static String text(Object value) {
		final var text = new StringBuilder();
		if (value instanceof FileTransferDesktopService.Inspected inspected) {
			text.append("Package file integrity checked. No database connection was opened.\n")
					.append(inspected.valuesValidated()
							? "CSV values and recorded row counts checked. Target compatibility has not been reviewed.\n\n"
							: "CSV values, recorded row counts and target compatibility have not been reviewed.\n\n");
			if (inspected.configuration() != null) {
				final var configuration = inspected.configuration();
				text.append("\nSaved target schema: ")
						.append(configuration.targetSchema() == null ? "current schema of the import connection"
								: configuration.targetSchema())
						.append('\n').append("Target table preparation: ").append(configuration.schemaMode())
						.append('\n').append("Verification after import: ")
						.append(configuration.verify() ? "enabled" : "disabled").append('\n')
						.append("Compatibility policy: ").append(configuration.compatibilityPolicy()).append('\n');
			}
			packageDetails(text, inspected.snapshot().manifest());
			text.append("\nManifest: ").append(inspected.snapshot().file()).append('\n').append("\nFingerprint: ")
					.append(inspected.snapshot().fingerprint()).append('\n')
					.append("\nNext: enter the target connection and choose Review package and target.\n")
					.append("File hashes are integrity checks, not proof of authenticity or import approval.\n");
		} else if (value instanceof TransferFilePackageIO.Snapshot exported) {
			text.append("CSV package exported. No rows have been imported.\n\n");
			packageDetails(text, exported.manifest());
			text.append("\nManifest: ").append(exported.file()).append('\n');
			text.append("\nNext: choose Import CSV package, enter the target connection, and review.\n")
					.append("Move the whole package if importing on another machine.\n")
					.append("The Gradle tab contains settings for this package and optional re-export.\n");
		} else if (value instanceof FileTransferDesktopService.Reviewed reviewed) {
			text.append("Package and target reviewed. No tables or rows have been created.\n\n").append("Target: ")
					.append(reviewed.review().targetIdentity().productName()).append(" / ")
					.append(reviewed.review().targetIdentity().productVersion()).append('\n');
			packageDetails(text, reviewed.manifest());
			text.append("\nMissing-table DDL statements: ")
					.append(reviewed.review().schemaPreparation().statements().size()).append('\n');
			warnings(text, reviewed.review().schemaPreparation().warnings());
			text.append("\nManifest: ").append(reviewed.review().manifestFile()).append('\n')
					.append("\nNext: inspect warnings and Schema SQL, then confirm Import reviewed package.\n")
					.append("The source connection is not needed. The Gradle tab provides target-only settings.\n");
		} else if (value instanceof TransferFileImportService.Outcome outcome) {
			text.append("Import execution phase: ").append(outcome.phase()).append('\n');
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
			outcome.committedProgress().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
					.forEach(entry -> text.append("Committed rows / ").append(entry.getKey()).append(": ")
							.append(entry.getValue().getProcessedRowsAfter()).append('\n'));
			final var schema = outcome.schemaPreparation();
			text.append("DDL statements completed: ").append(schema.completedStatements()).append(" / ")
					.append(schema.planned().size()).append('\n');
			if (schema.attemptedStatement() != null)
				text.append("Uncertain attempted DDL statement: ").append(schema.attemptedStatement()).append('\n');
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
			throw new IllegalArgumentException("Unsupported CSV result type.");
		return text.toString();
	}

	private static void packageDetails(StringBuilder text, TransferFilePackageIO.Manifest manifest) {
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
}
