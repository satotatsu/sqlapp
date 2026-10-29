/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.List;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaChangeChecks;

/**
 * Recorded planning guidance; never infers whether a target name was applied.
 */
final class TransferNameAdjustmentNotice {
	private TransferNameAdjustmentNotice() {
	}

	static void append(StringBuilder text, List<String> warnings) {
		final long count = warnings.stream()
				.filter(warning -> warning.startsWith(TransferSchemaChangeChecks.NAME_ADJUSTMENT)).count();
		if (count == 0)
			return;
		text.append("\nObject-name adjustment warnings: ").append(count).append(".\n")
				.append("See Warnings (Target / Object names) and Schema SQL for the original and target names. ")
				.append("Check scripts and application code that refer to constraint/index names; source definitions retain their original names.\n");
	}
}
