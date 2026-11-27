/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.List;
import com.sqlapp.data.db.command.migration.transfer.TransferGeneratedKeyChecks;
import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService;

/**
 * Follow-up guidance based on recorded execution state, without database
 * access.
 */
final class TransferNextSteps {
	private TransferNextSteps() {
	}

	static void append(StringBuilder text, TransferJobExecutionService.Phase phase, boolean verificationRequested,
			List<String> warnings) {
		text.append("\nNext steps:\n");
		if (phase != TransferJobExecutionService.Phase.COMPLETE) {
			text.append(
					"  Inspect Details and the audit report for the stopped task, committed rows and attempted DDL.\n")
					.append("  Check and repair the target before reviewing again. Retrying does not reset existing data.\n");
			return;
		}
		if (!verificationRequested)
			text.append("  Row verification was disabled. Compare source and target data before application use.\n");
		if (!TransferGeneratedKeyChecks.messages(warnings).isEmpty())
			text.append(
					"  Check the listed generated-key counters before automatic INSERTs. Counters were not synchronized.\n");
		if (!warnings.isEmpty())
			text.append("  Review compatibility warnings and adjust target definitions where needed.\n");
		text.append("  Test application reads and writes on the target before switching connections.\n")
				.append("  Keep the saved job and audit report for repeatable runs and troubleshooting.\n");
	}
}
