/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import com.sqlapp.data.db.command.migration.transfer.*;

/**
 * Connection lifetime only; common services own review, execution and report
 * semantics.
 */
final class DirectTransferDesktopService implements DirectTransferPanel.Backend {
	record Reviewed(TransferJobExecutionService.Review review, Path report,
			@com.fasterxml.jackson.annotation.JsonIgnore String schemaSqlDraft) {
		Reviewed(TransferJobExecutionService.Review review, Path report) {
			this(review, report, null);
		}
	}

	record Executed(TransferJobExecutionService.Outcome outcome, Path report,
			@com.fasterxml.jackson.annotation.JsonIgnore String schemaSqlDraft) {
		Executed(TransferJobExecutionService.Outcome outcome, Path report) {
			this(outcome, report, null);
		}
	}

	static final class ExecutionFailure extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		final Executed result;

		ExecutionFailure(Executed result, String message) {
			super(message);
			this.result = result;
		}
	}

	static String draftFor(TransferJobExecutionService.Outcome outcome, Reviewed reviewed) {
		return reviewed != null
				&& outcome.prepared().job().fingerprint().equals(reviewed.review().prepared().job().fingerprint())
				&& outcome.prepared().schemaPreparation().equals(reviewed.review().prepared().schemaPreparation())
						? reviewed.schemaSqlDraft()
						: null;
	}

	@Override
	public Object perform(DirectTransferPanel.Action action, DirectTransferPanel.Request request, char[] sourcePassword,
			char[] targetPassword) throws Exception {
		Executed completed = null;
		String phase = "checking the saved job and working folder";
		try {
			if (action == DirectTransferPanel.Action.DISCOVER) {
				phase = "reading source tables";
				return new DataMigrationDraftService().discover(request.source(), sourcePassword, request.schema(),
						request.workspace());
			}
			if (action == DirectTransferPanel.Action.TEST_SOURCE) {
				phase = "testing source connection";
				return new JdbcConnectionTestService().test(request.source(), sourcePassword);
			}
			if (action == DirectTransferPanel.Action.TEST_TARGET) {
				phase = "testing target connection";
				return new JdbcConnectionTestService().test(request.target(), targetPassword);
			}
			final var validation = new TransferDatabaseCommand();
			validation.setConfigurationFile(request.job().toFile());
			validation.setWorkspaceDirectory(request.workspace().toFile());
			validation.validateInputs();
			final var definition = new TransferJobConfigurationIO().read(request.job());
			if (definition.configuration().source().equals(definition.configuration().target()))
				throw new TransferActionFailure(phase, "Source and target logical connection names are identical.",
						"Use different source/target names in the shared job for separate connections.",
						"Configuration validation");
			phase = "configuring runtime JDBC drivers";
			try (var source = PlanningJdbc.open(request.source(), sourcePassword);
					var target = action == DirectTransferPanel.Action.EXECUTE
							? PlanningJdbc.openForDataExecution(request.target(), targetPassword)
							: PlanningJdbc.open(request.target(), targetPassword)) {
				final var bindings = Map.of(definition.configuration().source(), source,
						definition.configuration().target(), target);
				final var service = new TransferJobExecutionService();
				final var reports = new TransferJobReportIO();
				if (action == DirectTransferPanel.Action.REVIEW) {
					phase = "reviewing source metadata and target tables";
					final var captured = service.reviewWithSqlDraft(request.job(), bindings, request.workspace());
					final var review = captured.review();
					phase = "saving the review report";
					return new Reviewed(review, reports.writeReview(review), captured.schemaSqlDraft());
				}
				final var review = request.reviewed().review();
				try {
					final var outcome = service.execute(review, bindings);
					completed = new Executed(outcome, null, draftFor(outcome, request.reviewed()));
					completed = new Executed(outcome, reports.writeOutcome(review, outcome),
							completed.schemaSqlDraft());
					return completed;
				} catch (TransferJobExecutionService.ExecutionException failure) {
					Path report = null;
					try {
						report = reports.writeOutcome(review, failure.outcome());
					} catch (java.io.IOException unavailable) {
						/* Exposed by a null report path. */ }
					throw new ExecutionFailure(
							new Executed(failure.outcome(), report, draftFor(failure.outcome(), request.reviewed())),
							"Direct transfer failed. Inspect committed work, verification and audit availability before retrying.");
				}
			}
		} catch (ExecutionFailure failure) {
			throw new ExecutionFailure(failure.result, failure.getMessage());
		} catch (TransferActionFailure failure) {
			throw new TransferActionFailure(failure.phase, failure.reason, failure.nextStep, failure.technical);
		} catch (TransferJobPreparationService.PreparationException failure) {
			throw TransferActionFailure.preparation(failure);
		} catch (TransferSchemaPreparation.ValidationException failure) {
			throw new TransferPreparationFailure(failure);
		} catch (Exception failure) {
			if (completed != null)
				throw new ExecutionFailure(completed,
						"Copy result retained, but report publication or connection cleanup failed. Inspect the result before retrying.");
			final String reason;
			final String next;
			if (failure instanceof java.nio.file.NoSuchFileException) {
				reason = "The saved job or a required file was not found.";
				next = "Choose the existing shared job YAML and check its referenced files.";
			} else if (phase.equals("checking the saved job and working folder")) {
				reason = failure instanceof IllegalArgumentException && failure.getMessage() != null
						? failure.getMessage()
						: "The saved job or working folder could not be read.";
				next = "Check the selected YAML, its transfer route and the working folder's permissions. Use Job settings for supported options.";
			} else if (action == DirectTransferPanel.Action.DISCOVER || action == DirectTransferPanel.Action.TEST_SOURCE
					|| action == DirectTransferPanel.Action.TEST_TARGET) {
				reason = failure.getMessage() == null ? "Source discovery or connection test failed."
						: failure.getMessage();
				next = "Check the failing endpoint's connection and JDBC driver. For discovery, also check the source schema and working folder.";
			} else {
				reason = "The action could not complete at the reported stage.";
				next = "Use Test connection for each endpoint, then check runtime JDBC drivers, the selected job and report-folder permissions.";
			}
			throw new TransferActionFailure(phase, reason, next,
					"Exception type: " + failure.getClass().getSimpleName());
		} finally {
			Arrays.fill(sourcePassword, '\0');
			Arrays.fill(targetPassword, '\0');
		}
	}
}
