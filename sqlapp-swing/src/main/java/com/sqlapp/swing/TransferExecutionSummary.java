/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.Phase;

/** Explains a retained outcome; never used for in-progress workers. */
final class TransferExecutionSummary {
	private TransferExecutionSummary() {
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
}
