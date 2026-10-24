/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.schema.MigrationCommand;
import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReport;
import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReportIO;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanArtifact;
import com.sqlapp.data.db.dialect.Dialect;

/** Applies only the reviewed GUI plan through the existing guarded command. */
final class SchemaMigrationExecutionService {
	record Outcome(MigrationExecutionReport report, ReportReviewService.Review review, String failureMessage) {
		boolean successful() {
			return report != null && report.successful() && failureMessage == null;
		}
	}

	static String restriction(final SchemaMigrationPlanningService.Request request,
			final SchemaMigrationPlanningService.Result reviewed) {
		if (request == null || reviewed == null || reviewed.artifact().plan().databaseIdentity() == null) {
			return "Build and review a connected migration plan first.";
		}
		if (request.setupDirectory() != null || request.finalizeDirectory() != null
				|| reviewed.artifact().plan().setupStatements() != 0
				|| reviewed.artifact().plan().finalizeStatements() != 0) {
			return "GUI execution does not support setup/finalize SQL; their bodies are not fingerprinted in the plan.";
		}
		if (reviewed.artifact().plan().hasBlockers()) {
			return "Resolve the plan's blockers and build a new plan before applying.";
		}
		if (reviewed.artifact().plan().pending().isEmpty()
				&& reviewed.artifact().plan().pendingRepeatables().isEmpty()) {
			return "No pending migrations to apply.";
		}
		return null;
	}

	/**
	 * Password is consumed and cleared on every attempt, including preflight
	 * rejection.
	 */
	Outcome execute(final SchemaMigrationPlanningService.Request request, final PlanningJdbc.Spec connection,
			final SchemaMigrationPlanningService.Result reviewed, final char[] password) {
		Objects.requireNonNull(password, "password");
		final String secret = new String(password);
		MigrationCommand command = null;
		Throwable failure = null;
		Path expectedPlan = null;
		try {
			final String blocked = restriction(request, reviewed);
			if (blocked != null) {
				throw new IllegalArgumentException(blocked);
			}
			final var planning = new SchemaMigrationPlanningService();
			try (var readOnly = PlanningJdbc.open(connection, password)) {
				final var current = planning.plan(request, readOnly);
				final String fingerprint = MigrationPlanArtifact.fingerprint(reviewed.artifact().createdAtEpochMillis(),
						current.artifact().plan());
				if (!reviewed.artifact().planFingerprint().equals(fingerprint)) {
					throw new IllegalArgumentException(
							"Current database, SQL or policies differ from the reviewed plan. Build a new plan.");
				}
			}
			expectedPlan = Files.createTempFile("sqlapp-reviewed-plan-", ".json");
			planning.save(reviewed, expectedPlan);
			command = new MigrationCommand() {
				@Override
				public Dialect getDialect(final java.sql.Connection value) {
					return PlanningJdbc.requireDialect(value);
				}
			};
			SchemaMigrationPlanningService.configure(command, request);
			command.setExpectedPlanFile(expectedPlan.toFile());
			command.setExpectedPlanFingerprint(reviewed.artifact().planFingerprint());
			try (var writable = PlanningJdbc.openForExecution(connection, password)) {
				command.setDataSource(writable);
				command.run();
			}
		} catch (final Exception exception) {
			failure = exception;
		} finally {
			Arrays.fill(password, '\0');
			if (expectedPlan != null) {
				try {
					Files.deleteIfExists(expectedPlan);
				} catch (final IOException cleanupFailure) {
					if (failure == null) {
						failure = cleanupFailure;
					} else {
						failure.addSuppressed(cleanupFailure);
					}
				}
			}
		}
		final var report = command == null ? null : command.getExecutionReport();
		String message = failure == null ? null
				: failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
		if (message != null) {
			if (!secret.isEmpty()) {
				message = message.replace(secret, "[redacted]");
			}
			if (connection != null) {
				message = message.replace(connection.jdbcUrl(), "[JDBC URL]");
			}
		}
		final var base = report == null ? new ReportReviewService.Review(
				"Execution did not complete. No execution audit report is available.\nInspect the database before retrying.",
				List.of(), List.of(), "") : new ReportReviewService().schemaExecution(report);
		final var review = message == null ? base
				: new ReportReviewService.Review(base.summary() + "\nOperation failure: " + message, base.columns(),
						base.rows(), base.details());
		return new Outcome(report, review, message);
	}

	void save(final Outcome outcome, final Path destination) throws IOException {
		if (outcome == null || outcome.report() == null) {
			throw new IllegalArgumentException("No execution audit report is available to save.");
		}
		if (destination == null || destination.getFileName() == null
				|| !destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
			throw new IllegalArgumentException("Choose a .json file for the execution audit report.");
		}
		final var io = new MigrationExecutionReportIO();
		io.write(destination, outcome.report());
		if (!outcome.report().equals(io.read(destination))) {
			throw new IOException("Saved execution audit differs from the displayed report.");
		}
	}
}
