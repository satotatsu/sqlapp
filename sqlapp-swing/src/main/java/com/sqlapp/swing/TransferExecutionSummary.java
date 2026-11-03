/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.Phase;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation.Progress;

/** Explains a retained outcome; never used for in-progress workers. */
final class TransferExecutionSummary {
	private TransferExecutionSummary() {
	}

	static void appendProgressGuide(StringBuilder text) {
		text.append("Open Execution progress to inspect the stopped task, recorded issues and last committed chunk counts; select a task to open its SQL or verification.\n");
	}

	static void appendVerificationGuide(StringBuilder text) {
		text.append("Open Verification for recorded table counts and mismatches; select a task and Show chunks for paged details.\n");
	}

	static void appendFailure(StringBuilder text,
			com.sqlapp.data.db.command.migration.transfer.TransferFailureDiagnostic diagnostic) {
		if (diagnostic == null) return;
		text.append("\nReason: ").append(diagnostic.reason())
				.append("\nWhat to check next: ").append(diagnostic.nextStep())
				.append('\n').append(diagnostic.technicalDetails())
				.append("\nThese diagnostics do not identify a failed row or authorize retry.\n\n");
	}

	static void append(StringBuilder text, Phase phase) {
		text.append(switch (phase) {
		case COMPLETE -> "Transfer completed. Review verification and compatibility warnings below.";
		case REVALIDATION -> "Transfer stopped while rechecking the saved job and database state.";
		case SCHEMA_PREPARATION -> "Transfer stopped while preparing target tables. Created objects may remain.";
		case COPY -> "Transfer stopped while copying data. Committed rows may remain on the target.";
		case VERIFICATION -> "Transfer stopped while verifying copied data. Copying does not prove a data match.";
		case FINALIZATION -> "Transfer stopped while finalizing the run. Inspect retained results before retrying.";
		}).append('\n');
	}

	static void appendSchemaProgress(StringBuilder text, Progress progress) {
		var rows = TransferSchemaStatementTabs.rows(progress.planned(), progress);
		if (rows.isEmpty())
			return;
		var pending = rows.stream().filter(row -> row.get(2).equals("Not executed")).toList();
		text.append("\nSchema SQL not executed: ").append(pending.size()).append('\n');
		rows.stream().filter(row -> row.get(2).equals("Uncertain attempt")).findFirst().ifPresent(row -> text
				.append("Uncertain SQL: order ").append(row.get(0)).append(" / task ").append(row.get(1))
				.append("\nOpen Schema statements → Show uncertain attempt to inspect the SQL. It may already have taken effect.\n"));
		if (!pending.isEmpty()) {
			var first = pending.getFirst();
			text.append("First unexecuted SQL: order ").append(first.get(0)).append(" / task ").append(first.get(1))
					.append('\n');
		}
		if (!pending.isEmpty() || rows.stream().anyMatch(row -> row.get(2).equals("Uncertain attempt")))
			text.append("Recorded progress is not a restart point. Inspect target state before another review.\n");
	}
}
