/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.List;
import com.sqlapp.data.db.command.migration.transfer.TransferGeneratedKeyChecks;

final class TransferGeneratedKeyNotice {
	private TransferGeneratedKeyNotice() {
	}

	static void append(StringBuilder text, List<String> warnings) {
		final int count = TransferGeneratedKeyChecks.messages(warnings).size();
		if (count == 0)
			return;
		text.append("\nBefore application writes: review generated-key counters for ").append(count)
				.append(" column(s).\n")
				.append("Copied-row verification does not verify future generated IDs. See the per-column ")
				.append(TransferGeneratedKeyChecks.CODE).append(" warnings below.\n");
	}
}
