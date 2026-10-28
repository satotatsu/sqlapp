/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.jdbc.bulk.*;

class FileTransferSummaryTest {
	private static final String HASH = "sha256:" + "a".repeat(64);

	@Test
	void failureRetainsDdlUncertaintyCommittedRowsAndMissingAuditWithoutClaimingVerification() {
		final var ddl = new TransferSchemaPreparation.Progress(
				List.of(new TransferSchemaPreparation.Statement("items", "CREATE TABLE ITEMS(ID INTEGER)")), 0, 1,
				List.of("Vendor default requires testing"));
		final var outcome = new TransferFileImportService.Outcome(HASH, HASH,
				TransferJobExecutionService.Phase.SCHEMA_PREPARATION, null, true, Map.of(),
				Map.of("items", new ChunkedBulkMigrationProgress("items", 0, 3, 0, 3)), "items", null, ddl);
		final var text = FileTransferSummary.text(outcome);
		assertTrue(text.contains("Verification: not completed"));
		assertTrue(text.startsWith("Transfer stopped while preparing target tables."));
		assertTrue(text.contains("Committed rows / items: 3"));
		assertTrue(text.contains("DDL statements completed: 0 / 1"));
		assertTrue(text.contains("Uncertain attempted DDL statement: 1"));
		assertTrue(text.contains("Vendor default requires testing"));
		assertTrue(text.contains("Stopped task: items"));
		assertTrue(text.contains("Audit report: unavailable"));
		assertTrue(text.contains("Import did not complete"));
		assertTrue(text.contains("Retrying does not reset existing data"));
		assertTrue(text.indexOf("Next steps:") < text.indexOf("Committed rows / items"));
	}

	@Test
	void completedCopyWithVerificationDisabledDoesNotClaimMatchedData() {
		final var outcome = new TransferFileImportService.Outcome(HASH, HASH,
				TransferJobExecutionService.Phase.COMPLETE, new BulkMigrationJobResult(List.of()), false, Map.of(),
				Map.of(), null, Path.of("import-result.json"));
		final var text = FileTransferSummary.text(outcome);
		assertTrue(text.contains("Verification: disabled by job configuration"));
		assertTrue(text.startsWith("Transfer completed."));
		assertTrue(text.contains("Completed tables: 0"));
		assertTrue(text.contains("Processed rows in completed tables: 0"));
		assertFalse(text.contains("Verification: matched"));
		assertTrue(text.contains("Row verification was disabled"));
		assertTrue(text.contains("Test application reads and writes"));
	}

	@Test
	void partialVerificationOnlyDescribesRecordedTables() {
		final var outcome = new TransferFileImportService.Outcome(HASH, HASH,
				TransferJobExecutionService.Phase.VERIFICATION, null, true,
				Map.of("items", new BulkMigrationVerificationResult(10, 0, 0, List.of())), Map.of(), "orders", null);
		final var text = FileTransferSummary.text(outcome);
		assertTrue(text.contains("matched for 1 recorded tables"));
		assertTrue(text.contains("Import did not complete"));
	}

	@Test
	void verificationMismatchRemainsVisibleEvenWithRecordedCopyResults() {
		final var comparison = new BulkMigrationVerificationResult(10, 1, 1,
				List.of(new BulkMigrationVerificationChunk(0, 1, 1, "expected", "different")));
		final var outcome = new TransferFileImportService.Outcome(HASH, HASH,
				TransferJobExecutionService.Phase.VERIFICATION, new BulkMigrationJobResult(List.of()), true,
				Map.of("items", comparison), Map.of(), "items", Path.of("result.json"));
		assertTrue(FileTransferSummary.text(outcome).contains("Verification: mismatch"));
	}
}
