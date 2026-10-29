/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferWarningTabsTest {
	private static final String HASH = "sha256:" + "a".repeat(64);

	private TransferFilePackageIO.Snapshot snapshot() {
		return new TransferFilePackageIO.Snapshot(Path.of("manifest.json"), HASH, new TransferFilePackageIO.Manifest(1,
				HASH, HASH, new TransferJobConfiguration.FileTransfer("packages", null, null, "__NULL__"),
				List.of(new TransferFilePackageIO.TableFile("customers", null, "PUBLIC", "CUSTOMERS", List.of("ID"),
						"table-1.csv", 0, 0, HASH)),
				List.of(new TransferFilePackageIO.Warning("customers", "ID", "Review precision\nFull detail"))));
	}

	@Test
	void exportIdentityAndMessageArePreservedWithoutParsing() {
		var row = TransferWarningTabs.rows(snapshot()).getFirst();
		assertEquals(List.of("Package export", "customers", "ID", "Review precision\nFull detail"), row.subList(0, 4));
		assertTrue(row.get(4).contains("before importing"));
	}

	@Test
	void reviewCombinesExportAndTargetWarningsWithoutDroppingDuplicates() {
		var saved = snapshot();
		var review = new FileTransferDesktopService.Reviewed(new TransferFileImportService.Review(saved.file(), HASH,
				new com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity("DB", "1", HASH), HASH,
				new TransferSchemaPreparation.Plan(HASH, List.of(), List.of("Target warning", "Target warning"))),
				saved.manifest());
		var rows = TransferWarningTabs.rows(review);
		assertEquals(3, rows.size());
		assertEquals("Package export", rows.get(0).get(0));
		assertEquals(List.of("Target compatibility", "", "", "Target warning"), rows.get(1).subList(0, 4));
		assertEquals(rows.get(1), rows.get(2));
	}

	@Test
	void tableSupportsLiteralSearchReadOnlyDetailsAndClearingStaleResults() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			tabs.addTab("Summary", new JPanel());
			var warnings = new TransferWarningTabs();
			warnings.show(tabs, snapshot());
			assertEquals("Warnings (1)", tabs.getTitleAt(1));
			var panel = (ReportDetailTablePanel) tabs.getComponentAt(1);
			assertFalse(panel.table.isCellEditable(0, 3));
			panel.filter.setText("precision");
			assertEquals(1, panel.table.getRowCount());
			panel.table.setRowSelectionInterval(0, 0);
			assertTrue(panel.selected.getText().contains("Full detail"));
			assertTrue(panel.selected.getText().contains("Next action:"));
			assertTrue(panel.selected.getText().contains("before importing"));
			panel.filter.setText("no matching warning");
			assertEquals(0, panel.table.getRowCount());
			warnings.clear(tabs);
			assertEquals(1, tabs.getTabCount());
			warnings.show(tabs, new TransferFileImportService.Outcome(HASH, HASH,
					TransferJobExecutionService.Phase.COMPLETE, null, false, Map.of(), Map.of(), null, null));
			assertEquals(1, tabs.getTabCount());
		});
	}

	@Test
	void stableCodesSelectGuidanceWithoutGuessingCategoriesFromEnglishMessages() {
		var warnings = List.of(TransferSchemaChangeChecks.DATA_IMPACT + " fixture",
				TransferSchemaChangeChecks.UNRESOLVED + " fixture", TransferGeneratedKeyChecks.CODE + " fixture",
				TransferSchemaChangeChecks.NAME_ADJUSTMENT + " fixture",
				"Repeated source constraint name without a category code",
				"Column removal and generated keys require manual work", "[FUTURE_WARNING] fixture");
		var outcome = new TransferFileImportService.Outcome(HASH, HASH, TransferJobExecutionService.Phase.COMPLETE,
				null, false, Map.of(), Map.of(), null, null,
				new TransferSchemaPreparation.Progress(List.of(), 0, null, warnings));
		var rows = TransferWarningTabs.rows(outcome);
		assertEquals(List.of("Target / Data impact", "Target / Manual work", "Target / Generated keys",
				"Target / Object names", "Target compatibility", "Target compatibility", "Target compatibility"),
				rows.stream().map(row -> row.getFirst()).toList());
		assertTrue(rows.get(0).get(4).contains("save and review again"));
		assertTrue(rows.get(1).get(4).contains("does not resolve"));
		assertTrue(rows.get(2).get(4).contains("not checked or synchronized"));
		assertTrue(rows.get(3).get(4).contains("source and target names"));
		assertTrue(rows.get(3).get(4).contains("does not confirm SQL execution"));
		assertEquals(warnings, rows.stream().map(row -> row.get(3)).toList());
	}
}
