/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferJobExecutionService.Phase;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation.Progress;
import com.sqlapp.data.db.command.migration.transfer.TransferSchemaPreparation.Statement;

class TransferExecutionSummaryTest {
	@Test
	void schemaSummaryDistinguishesUncertainAndUnexecutedWithoutSuggestingResume() {
		var statements = java.util.List.of(new Statement("parent", "CREATE P"), new Statement("child", "CREATE C"),
				new Statement("index", "CREATE I"));
		var text = new StringBuilder();
		TransferExecutionSummary.appendSchemaProgress(text, new Progress(statements, 1, 2));
		assertTrue(text.toString().contains("Schema SQL not executed: 1"));
		assertTrue(text.toString().contains("Uncertain SQL: order 2 / task child"));
		assertTrue(text.toString().contains("First unexecuted SQL: order 3 / task index"));
		assertTrue(text.toString().contains("may already have taken effect"));
		assertTrue(text.toString().contains("not a restart point"));
		text.setLength(0);
		TransferExecutionSummary.appendSchemaProgress(text, new Progress(statements, 3, null));
		assertTrue(text.toString().contains("Schema SQL not executed: 0"));
		assertFalse(text.toString().contains("Uncertain SQL:"));
		assertFalse(text.toString().contains("First unexecuted SQL:"));
		text.setLength(0);
		TransferExecutionSummary.appendSchemaProgress(text, new Progress(java.util.List.of(), 0, null));
		assertEquals("", text.toString());
	}

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
