/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReportIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReportIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReportIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReportIO;
import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReportIO;
import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReport;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanIO;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact;
import com.sqlapp.util.JsonConverter;

/** Uses existing artifact readers without connecting to a database. */
final class ReportReviewService {
	enum Type {
		SCHEMA_PLAN("Schema migration plan"), SCHEMA_EXECUTION("Schema execution result"),
		BULK_OPERATIONAL("Data migration status / dry run"), BULK_EXECUTION("Data execution result"),
		BULK_FAILURE("Data failure / pause result"), BULK_VERIFICATION("Data verification result");

		private final String label;

		Type(final String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	record Review(String summary, List<String> columns, List<List<String>> rows, String details) {
		Review {
			Objects.requireNonNull(summary, "summary");
			columns = List.copyOf(columns);
			rows = rows.stream().map(List::copyOf).toList();
			Objects.requireNonNull(details, "details");
		}
	}

	Review read(final Path file, final Type type) {
		Objects.requireNonNull(type, "report type");
		if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
			throw new IllegalArgumentException("Select a readable report JSON file: " + file);
		}
		return switch (type) {
		case SCHEMA_PLAN -> schemaPlan(file);
		case SCHEMA_EXECUTION -> schemaExecution(file);
		case BULK_OPERATIONAL -> operational(file);
		case BULK_EXECUTION -> execution(file);
		case BULK_FAILURE -> failure(file);
		case BULK_VERIFICATION -> verification(file);
		};
	}

	private Review schemaPlan(final Path file) {
		return schemaPlan(requireReport(new MigrationPlanIO().read(file)));
	}

	Review schemaPlan(final MigrationPlanArtifact artifact) {
		final var plan = artifact.plan();
		final var rows = new ArrayList<List<String>>();
		for (final var entry : plan.pending()) {
			rows.add(row("Version " + entry.version(), entry.description(), entry.statements(), entry.transactional(),
					entry.rollbackAvailable(), entry.source()));
		}
		for (final var entry : plan.pendingRepeatables()) {
			rows.add(row("Repeatable", entry.name(), entry.statements(), entry.transactional(), "n/a", entry.source()));
		}
		return review(
				"Plan policy: " + (plan.hasBlockers() ? "KNOWN BLOCKERS PRESENT" : "no known blockers") + "\nVersion: "
						+ value(plan.currentVersion()) + " → "
						+ (Long.valueOf(Long.MAX_VALUE).equals(plan.targetVersion()) ? "all available"
								: value(plan.targetVersion()))
						+ "; pending: " + rows.size() + "; history issues: " + plan.historyIssues().size()
						+ "\nSetup statements: " + plan.setupStatements() + "; finalize statements: "
						+ plan.finalizeStatements() + "\nChecksums: "
						+ (plan.checksumValidation() == null ? "not recorded" : plan.checksumValidation().entries())
						+ "; Schema drift: "
						+ (plan.schemaDrift() == null ? "not checked"
								: plan.schemaDrift().isCompatible() ? "compatible" : "INCOMPATIBLE")
						+ "\nOut-of-order versions: " + plan.outOfOrderVersions() + "\nPolicies — reject out of order: "
						+ plan.outOfOrderRejected() + "; reject non-transactional: " + plan.nonTransactionalRejected()
						+ "; require rollback: " + plan.downMigrationRequired() + "\nCreated: "
						+ (artifact.createdAtEpochMillis() > 0 ? Instant.ofEpochMilli(artifact.createdAtEpochMillis())
								: "not recorded")
						+ "\nPlan fingerprint: " + artifact.planFingerprint(),
				List.of("Migration", "Description", "Statements", "Transactional", "Rollback available", "Source"),
				rows, artifact);
	}

	private Review schemaExecution(final Path file) {
		return schemaExecution(requireReport(new MigrationExecutionReportIO().read(file)));
	}

	Review schemaExecution(final MigrationExecutionReport report) {
		final var rows = new ArrayList<List<String>>();
		for (final var version : report.selectedVersions()) {
			rows.add(row("Version " + version,
					report.committedVersions().contains(version) ? "COMMITTED" : "NOT RECORDED AS COMMITTED"));
		}
		for (final var name : report.selectedRepeatables()) {
			rows.add(row(name,
					report.committedRepeatables().contains(name) ? "COMMITTED" : "NOT RECORDED AS COMMITTED"));
		}
		final String outcome = !report.successful() ? "FAILED"
				: report.executionRequested() ? "COMPLETED" : "NO EXECUTION REQUESTED";
		return review(
				"Saved outcome: " + outcome + "\nFinished: " + Instant.ofEpochMilli(report.finishedAtEpochMillis())
						+ "\nPlan fingerprint: " + value(report.planFingerprint())
						+ (report.failure() == null ? ""
								: "\nFailure: " + report.failure() + "\n" + report.failure().recoveryAdvice()),
				List.of("Migration", "Recorded outcome"), rows, report);
	}

	private Review operational(final Path file) {
		return operational(new BulkMigrationOperationalReportIO().read(file));
	}

	Review operational(final BulkMigrationOperationalReport report) {
		final var rows = report.tasks().stream()
				.map(task -> row(task.taskId(), task.catalogName(), task.schemaName(), task.tableName(), task.state(),
						task.mode(), task.checkpoint() == null ? "not recorded" : task.checkpoint().processedRows()))
				.toList();
		return review("Saved job: " + report.jobId() + "; compatible: " + report.compatible() + "\nCompleted tasks: "
				+ report.completedTasks() + "/" + report.totalTasks() + "; processed rows: " + report.processedRows()
				+ "\nSaved event: " + (report.execution() == null ? "not recorded" : report.execution().event())
				+ "\nMaintenance: " + (report.maintenance() == null ? "not recorded" : report.maintenance().status())
				+ "\nGenerated: " + report.generatedAt() + "\nPlan fingerprint: " + report.planFingerprint(),
				List.of("Task", "Catalog", "Schema", "Table", "Saved state", "Mode", "Checkpoint rows"), rows, report);
	}

	private Review execution(final Path file) {
		final var report = new BulkMigrationJobExecutionReportIO().read(file);
		final var rows = report.tasks().stream().map(task -> row(task.taskId(), task.previouslyProcessedRows(),
				task.processedRows(), task.completedChunks(), task.alreadyComplete())).toList();
		return review(
				"Saved outcome: COMPLETED; job: " + report.jobId() + "\nProcessed rows in this invocation: "
						+ report.processedRows() + "; already complete tasks: " + report.alreadyCompleteTasks()
						+ "\nVerification is recorded in a separate verification report." + "\nCompleted: "
						+ report.completedAt() + "\nPlan fingerprint: " + report.planFingerprint(),
				List.of("Task", "Previous rows", "Invocation rows", "Completed chunks", "Already complete"), rows,
				report);
	}

	private Review failure(final Path file) {
		final var report = new BulkMigrationJobFailureReportIO().read(file, null);
		final var rows = report.completedTasks().stream().map(task -> row(task.taskId(), task.previouslyProcessedRows(),
				task.processedRows(), task.completedChunks())).toList();
		return review(
				"Saved outcome: " + report.status() + "; job: " + report.jobId() + "\nStopped task: "
						+ value(report.stoppedTaskId()) + "; fully completed tasks recorded below: "
						+ report.completedTasks().size()
						+ "\nStopped-task committed chunks may exist; inspect checkpoints before resuming."
						+ "\nFailure: " + report.failureType() + ": " + report.failureMessage() + "\nRecorded: "
						+ report.failedAt() + "\nPlan fingerprint: " + report.planFingerprint(),
				List.of("Completed task", "Previous rows", "Invocation rows", "Completed chunks"), rows, report);
	}

	private Review verification(final Path file) {
		final var report = new BulkMigrationVerificationReportIO().read(file);
		final var rows = report.tasks().stream().map(task -> row(task.taskId(), task.match() ? "MATCH" : "MISMATCH",
				task.expectedRows(), task.actualRows(), task.mismatchedChunks())).toList();
		return review(
				"Saved verification: " + (report.match() ? "MATCH" : "MISMATCH") + "; mismatched tasks: "
						+ report.mismatchedTasks() + "\nExpected rows: " + report.expectedRows() + "; actual rows: "
						+ report.actualRows() + "; isolation: " + report.isolation() + "\nGenerated: "
						+ report.generatedAt() + "\nPlan fingerprint: " + report.planFingerprint(),
				List.of("Task", "Verification", "Expected rows", "Actual rows", "Mismatched chunks"), rows, report);
	}

	private Review review(final String summary, final List<String> columns, final List<List<String>> rows,
			final Object model) {
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		return new Review(summary, columns, rows, converter.toJsonString(model));
	}

	private static <T> T requireReport(final T report) {
		if (report == null) {
			throw new IllegalArgumentException("Report JSON must contain a report object, not null.");
		}
		return report;
	}

	private static List<String> row(final Object... values) {
		return Arrays.stream(values).map(ReportReviewService::value).toList();
	}

	private static String value(final Object value) {
		return value == null ? "not recorded" : value.toString();
	}
}
