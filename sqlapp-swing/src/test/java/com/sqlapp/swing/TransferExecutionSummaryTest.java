/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.Phase;

class TransferExecutionSummaryTest {
	@Test
	void onlyCompleteIsDescribedAsCompletedWithoutClaimingVerification() {
		for (var phase : Phase.values()) {
			var text = new StringBuilder();
			TransferExecutionSummary.append(text, phase);
			assertEquals(phase == Phase.COMPLETE, text.toString().startsWith("Transfer completed."), phase.name());
			assertEquals(phase != Phase.COMPLETE, text.toString().startsWith("Transfer stopped"), phase.name());
			assertFalse(text.toString().contains("verified successfully"), phase.name());
		}
	}

	@Test
	void copyAndDdlFailuresDescribePossibleRemainingChanges() {
		var copy = new StringBuilder();
		TransferExecutionSummary.append(copy, Phase.COPY);
		assertTrue(copy.toString().contains("Committed rows may remain"));
		var ddl = new StringBuilder();
		TransferExecutionSummary.append(ddl, Phase.SCHEMA_PREPARATION);
		assertTrue(ddl.toString().contains("Created objects may remain"));
	}
}
