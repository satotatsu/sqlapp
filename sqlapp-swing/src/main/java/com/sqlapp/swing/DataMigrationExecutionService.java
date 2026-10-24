/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.sqlapp.data.db.command.ConsoleOutputLevel;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReportIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReportIO;
import com.sqlapp.data.db.command.migration.bulk.ExecuteBulkMigrationJobCommand;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanCommand;

/**
 * Executes the reviewed YAML through the shared executor and preserves partial
 * outcomes.
 */
final class DataMigrationExecutionService {
	record Outcome(BulkMigrationJobExecutionReport execution, BulkMigrationJobFailureReport failure,
			ReportReviewService.Review review, String operationFailure,
			com.sqlapp.jdbc.bulk.BulkMigrationJobResult result,
			com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult verificationResult) {
		Outcome(BulkMigrationJobExecutionReport execution, BulkMigrationJobFailureReport failure,
				ReportReviewService.Review review, String operationFailure) {
			this(execution, failure, review, operationFailure, null, null);
		}

		boolean successful() {
			return execution != null && operationFailure == null;
		}

		boolean paused() {
			return failure != null && "PAUSED".equals(failure.status());
		}

		boolean hasAudit() {
			return execution != null || failure != null;
		}
	}

	static String restriction(final DataMigrationPlanningService.Result reviewed) {
		if (reviewed == null || reviewed.executionSnapshot() == null) {
			return "Inspect a live data migration plan before executing.";
		}
		return reviewed.executionSnapshot().restriction();
	}

	Outcome execute(final DataMigrationPlanningService.Result reviewed, final PlanningJdbc.Spec source,
			final char[] sourcePassword, final PlanningJdbc.Spec target, final char[] targetPassword) {
		return execute(reviewed, source, sourcePassword, target, targetPassword, new DataMigrationExecutionControl());
	}

	Outcome execute(final DataMigrationPlanningService.Result reviewed, final PlanningJdbc.Spec source,
			final char[] sourcePassword, final PlanningJdbc.Spec target, final char[] targetPassword,
			final DataMigrationExecutionControl control) {
		try {
			return executeInternal(reviewed, source, sourcePassword, target, targetPassword, control);
		} finally {
			Arrays.fill(sourcePassword, '\0');
			Arrays.fill(targetPassword, '\0');
			control.finish();
		}
	}

	private Outcome executeInternal(final DataMigrationPlanningService.Result reviewed, final PlanningJdbc.Spec source,
			final char[] sourcePassword, final PlanningJdbc.Spec target, final char[] targetPassword,
			final DataMigrationExecutionControl control) {
		final var command = new ExecuteBulkMigrationJobCommand();
		Path directory = null;
		Throwable problem = null;
		ReportReviewService.Review review = null;
		try {
			final String blocked = restriction(reviewed);
			if (blocked != null) {
				throw new IllegalArgumentException(blocked);
			}
			final var request = new DataMigrationPlanningService.Request(reviewed.configurationFile(),
					reviewed.report().provenance().configurationFingerprint());
			final var current = new DataMigrationPlanningService().plan(request, source, sourcePassword.clone(), target,
					targetPassword.clone());
			if (!Objects.equals(reviewed.executionSnapshot(), current.executionSnapshot())
					|| !Objects.equals(reviewed.report().planFingerprint(), current.report().planFingerprint())) {
				throw new IllegalArgumentException("Plan or database identity changed. Inspect and confirm again.");
			}
			control.configurePause(current.executionSnapshot().pauseRestriction() == null);
			command.setListener(control.jobListener());
			command.setChunkListener(control.chunkListener());
			directory = Files.createTempDirectory("sqlapp-data-execution-");
			command.setConfigurationFile(reviewed.configurationFile().toFile());
			command.setExpectedConfigurationFingerprint(request.expectedConfigurationFingerprint());
			command.setCloseDataSource(false);
			command.setConsoleOutputLevel(ConsoleOutputLevel.ERROR);
			command.setExecutionReportFile(directory.resolve("execution.json").toFile());
			command.setExecutionFailureReportFile(directory.resolve("failure.json").toFile());
			try (var sourcePool = PlanningJdbc.open(source, sourcePassword);
					var targetPool = PlanningJdbc.openForDataExecution(target, targetPassword)) {
				try (var sourceConnection = sourcePool.getConnection();
						var targetConnection = targetPool.getConnection()) {
					if (!Objects.equals(reviewed.executionSnapshot().source(),
							MigrationPlanCommand.databaseIdentity(sourceConnection))
							|| !Objects.equals(reviewed.executionSnapshot().target(),
									MigrationPlanCommand.databaseIdentity(targetConnection))) {
						throw new IllegalArgumentException(
								"Database identity changed before execution. Inspect again.");
					}
				}
				command.setSourceDataSource(sourcePool);
				command.setDataSource(targetPool);
				command.run();
			}
		} catch (final Exception exception) {
			problem = exception;
		} finally {
			if (directory != null) {
				try {
					final var reader = new ReportReviewService();
					if (command.getExecutionFailureReport() != null) {
						review = reader.read(directory.resolve("failure.json"), ReportReviewService.Type.BULK_FAILURE);
					} else if (command.getExecutionReport() != null) {
						review = reader.read(directory.resolve("execution.json"),
								ReportReviewService.Type.BULK_EXECUTION);
					}
				} catch (final Exception exception) {
					if (problem == null)
						problem = exception;
					else
						problem.addSuppressed(exception);
				}
				for (final Path path : List.of(directory.resolve("execution.json"), directory.resolve("failure.json"),
						directory)) {
					try {
						Files.deleteIfExists(path);
					} catch (final Exception exception) {
						if (problem == null)
							problem = exception;
						else
							problem.addSuppressed(exception);
					}
				}
			}
		}
		try {
			String message = problem == null ? null
					: problem.toString() + (problem.getSuppressed().length == 0 ? ""
							: "; additional failures: " + Arrays.toString(problem.getSuppressed()));
			if (message != null) {
				for (final char[] secret : new char[][] { sourcePassword, targetPassword }) {
					if (secret.length > 0)
						message = message.replace(new String(secret), "[redacted]");
				}
				for (final var spec : new PlanningJdbc.Spec[] { source, target }) {
					if (spec != null)
						message = message.replace(spec.jdbcUrl(), "[JDBC URL]");
				}
			}
			if (review == null) {
				review = new ReportReviewService.Review(
						"No execution audit is available. Check target and checkpoints before retrying.", List.of(),
						List.of(), "");
			}
			final String verification = command.getVerificationResult() == null ? "not completed"
					: command.getVerificationResult().isMatch() ? "MATCH" : "MISMATCH";
			final var details = new java.util.LinkedHashMap<String, Object>();
			details.put("execution", command.getExecutionReport());
			details.put("failure", command.getExecutionFailureReport());
			details.put("result", command.getResult());
			details.put("verification", command.getVerificationResult());
			details.put("operationFailure", message);
			details.put("lastProgress", control.progress());
			final var converter = new com.sqlapp.util.JsonConverter();
			converter.setIndentOutput(true);
			review = new ReportReviewService.Review(
					review.summary() + "\nVerification: " + verification
							+ (message == null ? ""
									: (command.getExecutionFailureReport() != null
											&& "PAUSED".equals(command.getExecutionFailureReport().status())
													? "\nOperation paused: "
													: "\nOperation failed: ")
											+ message
											+ "\nCommitted chunks are retained. Inspect target and checkpoints before retrying."),
					review.columns(), review.rows(), converter.toJsonString(details));
			return new Outcome(command.getExecutionReport(), command.getExecutionFailureReport(), review, message,
					command.getResult(), command.getVerificationResult());
		} finally {
			Arrays.fill(sourcePassword, '\0');
			Arrays.fill(targetPassword, '\0');
		}
	}

	void save(final Outcome outcome, final Path destination) {
		if (outcome == null || !outcome.hasAudit()) {
			throw new IllegalArgumentException("No execution audit is available to save.");
		}
		if (destination == null || destination.getFileName() == null
				|| !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
			throw new IllegalArgumentException("Choose a .json file for the execution audit.");
		}
		if (outcome.failure() != null) {
			new BulkMigrationJobFailureReportIO().writeSnapshot(destination, outcome.failure(), null);
		} else {
			new BulkMigrationJobExecutionReportIO().writeSnapshot(destination, outcome.execution(), null);
		}
	}
}
