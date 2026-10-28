/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferGeneratedKeyChecks;
import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.Phase;

class TransferNextStepsTest {
	@Test
	void completedWithoutVerificationRetainsRequiredChecks() {
		var text = new StringBuilder();
		TransferNextSteps.append(text, Phase.COMPLETE, false, List.of(TransferGeneratedKeyChecks.CODE + " Target ID"));
		assertTrue(text.toString().contains("Row verification was disabled"));
		assertTrue(text.toString().contains("Counters were not synchronized"));
		assertTrue(text.toString().contains("adjust target definitions"));
		assertTrue(text.toString().contains("Test application reads and writes"));
	}

	@Test
	void completedWithoutWarningsDoesNotInventWarningsOrUnverifiedRows() {
		var text = new StringBuilder();
		TransferNextSteps.append(text, Phase.COMPLETE, true, List.of());
		assertFalse(text.toString().contains("verification was disabled"));
		assertFalse(text.toString().contains("Counters"));
		assertFalse(text.toString().contains("compatibility warnings"));
		assertTrue(text.toString().contains("saved job and audit report"));
	}

	@Test
	void everyIncompletePhaseRequiresInspectionBeforeRetry() {
		for (var phase : Phase.values()) {
			if (phase == Phase.COMPLETE)
				continue;
			var text = new StringBuilder();
			TransferNextSteps.append(text, phase, true, List.of());
			assertTrue(text.toString().contains("committed rows and attempted DDL"), phase.name());
			assertTrue(text.toString().contains("Retrying does not reset existing data"), phase.name());
			assertFalse(text.toString().contains("switching connections"), phase.name());
		}
	}
}
