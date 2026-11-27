/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferNameAdjustmentNoticeTest {
	@Test
	void onlyCodedWarningsAreCountedAndNoExecutionClaimIsAdded() {
		var text = new StringBuilder("Existing summary");
		TransferNameAdjustmentNotice.append(text, List.of("Repeated source index name", "[FUTURE_CODE] fixture"));
		assertEquals("Existing summary", text.toString());
		String warning = TransferSchemaChangeChecks.NAME_ADJUSTMENT + " source PrimaryKey -> target sqlapp_fixture";
		TransferNameAdjustmentNotice.append(text, List.of(warning, warning));
		assertTrue(text.toString().contains("adjustment warnings: 2"));
		assertTrue(text.toString().contains("original and target names"));
		assertTrue(text.toString().contains("source definitions retain their original names"));
		assertFalse(text.toString().contains("names were applied"));
	}

	@Test
	void failedImportSummaryRetainsNamingGuidanceAlongsideActualProgress() {
		String hash = "sha256:" + "a".repeat(64);
		var outcome = new TransferFileImportService.Outcome(hash, hash,
				TransferJobExecutionService.Phase.SCHEMA_PREPARATION, null, false, Map.of(), Map.of(), null, null,
				new TransferSchemaPreparation.Progress(
						List.of(new TransferSchemaPreparation.Statement("items", "CREATE TABLE ITEMS(ID INT)")), 0, 1,
						List.of(TransferSchemaChangeChecks.NAME_ADJUSTMENT + " fixture")));
		var text = FileTransferSummary.text(outcome);
		assertTrue(text.contains("adjustment warnings: 1"));
		assertTrue(text.contains("Uncertain attempted DDL statement: 1"));
		assertTrue(text.contains("Import did not complete"));
	}
}
