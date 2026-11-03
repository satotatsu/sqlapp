/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.jdbc.bulk.*;

class TransferVerificationTabsTest {
	static BulkMigrationVerificationResult comparison(int size, int badIndex, boolean unordered) {
		var chunks = new ArrayList<BulkMigrationVerificationChunk>();
		for (int index = 0; index < size; index++) {
			String hash = (unordered ? "multiset-sha256-v1:" : "sha256:") + index;
			chunks.add(new BulkMigrationVerificationChunk(index, 1, 1, hash, index == badIndex ? hash + "-different" : hash));
		}
		return new BulkMigrationVerificationResult(1, size, size, chunks);
	}

	static TransferFileImportService.Outcome outcome(Map<String, BulkMigrationVerificationResult> verification) {
		return new TransferFileImportService.Outcome("hash", "hash", TransferJobExecutionService.Phase.VERIFICATION,
				null, true, verification, Map.of(), "not-yet-verified", null);
	}

	@Test
	void fileResultsSupportMismatchNavigationSortedSelectionAndReadOnlyFullHashes() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			tabs.addTab("Summary", new JPanel());
			var view = new TransferVerificationTabs();
			view.show(tabs, outcome(Map.of("matched", comparison(2, -1, false), "bad", comparison(12, 10, true))));
			assertEquals(2, tabs.getTabCount());
			assertEquals(0, tabs.getSelectedIndex());
			assertEquals(2, view.tables.table.getRowCount());
			assertFalse(view.tables.table.isCellEditable(0, 0));
			assertFalse(view.showChunks.isEnabled());
			view.tables.table.getRowSorter().toggleSortOrder(3);
			view.tables.table.getRowSorter().toggleSortOrder(3);
			view.tables.table.moveColumn(0, 6);
			view.showMismatches.doClick(0);
			assertEquals(1, view.tables.table.getRowCount());
			assertTrue(view.showChunks.isEnabled());
			view.showChunks.doClick(0);
			assertEquals(view.chunks, tabs.getSelectedComponent());
			assertTrue(view.page.getText().contains("bad — 1–12 of 12"));
			assertTrue(view.page.getText().contains("Unordered multiset hashes"));
			view.chunks.table.getRowSorter().toggleSortOrder(0);
			assertEquals("10", view.chunks.table.getValueAt(10, 0));
			view.mismatchedChunks.doClick(0);
			assertEquals(1, view.chunks.table.getRowCount());
			assertEquals("10", view.chunks.table.getValueAt(0, 0));
			assertFalse(view.chunks.table.isCellEditable(0, 4));
			view.chunks.table.setRowSelectionInterval(0, 0);
			assertTrue(view.chunks.selected.getText().contains("multiset-sha256-v1:10-different"));
			assertFalse(view.next.isEnabled());
		});
	}

	@Test
	void pagesAreBoundedAndSearchCopyAndMismatchFilterArePageScoped() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferVerificationTabs();
			view.show(tabs, outcome(Map.of("large", comparison(405, 403, false))));
			view.tables.table.setRowSelectionInterval(0, 0);
			view.showChunks.doClick(0);
			assertEquals(200, view.chunks.table.getRowCount());
			assertFalse(view.previous.isEnabled());
			view.chunks.filter.setText("not in this page");
			view.next.doClick(0);
			assertEquals("", view.chunks.filter.getText());
			assertEquals(200, view.chunks.table.getRowCount());
			assertEquals("200", view.chunks.table.getValueAt(0, 0));
			view.next.doClick(0);
			assertEquals(5, view.chunks.table.getRowCount());
			assertTrue(view.page.getText().contains("401–405 of 405"));
			assertFalse(view.next.isEnabled());
			view.mismatchedChunks.doClick(0);
			assertEquals(1, view.chunks.table.getRowCount());
			assertEquals("403", view.chunks.table.getValueAt(0, 0));
			assertFalse(view.previous.isEnabled());
			assertTrue(view.page.getText().contains("1–1 of 1"));
			view.mismatchedChunks.doClick(0);
			view.next.doClick(0);
			view.previous.doClick(0);
			assertEquals("0", view.chunks.table.getValueAt(0, 0));
		});
	}

	@Test
	void emptyComparisonsAndAbsentResultsNeverInventVerificationAndOldActionsCannotRestoreTabs() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			tabs.addTab("Summary", new JPanel());
			var view = new TransferVerificationTabs();
			view.show(tabs, outcome(Map.of("empty", comparison(0, -1, false))));
			assertFalse(view.showMismatches.isEnabled());
			view.tables.table.setRowSelectionInterval(0, 0);
			view.showChunks.doClick(0);
			assertEquals(0, view.chunks.table.getRowCount());
			assertTrue(view.page.getText().contains("0–0 of 0"));
			var stale = view.showChunks;
			var staleNext = view.next;
			var staleMismatch = view.mismatchedChunks;
			view.clear(tabs);
			assertEquals(1, tabs.getTabCount());
			stale.doClick(0);
			staleNext.doClick(0);
			staleMismatch.doClick(0);
			assertEquals(1, tabs.getTabCount());
			view.show(tabs, outcome(Map.of()));
			view.show(tabs, new Object());
			assertEquals(1, tabs.getTabCount());
		});
	}

	@Test
	void directResultsUseTheSameViewWithoutOpeningArtifactsOrConnections() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var configuration = new TransferJobConfiguration("from", "to", List.of("ITEMS"), TransferJobConfiguration.SchemaMode.REQUIRE_EXISTING);
			var job = new TransferJobConfigurationIO.Snapshot(Path.of("missing.yaml"), "", "hash", configuration);
			var prepared = new TransferJobPreparationService.Prepared(Path.of("missing"), job, Path.of("missing.xml"), null,
					"hash", List.of("direct"), List.of(), new TransferSchemaPreparation.Plan("hash", List.of(), List.of(),
							List.of(new TransferSchemaPreparation.TablePreparation("direct", "catalog", "Target", "日本語",
									TransferSchemaPreparation.TableAction.USE_EXISTING))));
			var verification = new BulkMigrationJobVerificationResult(List.of(
					new BulkMigrationJobTaskVerificationResult("direct", List.of("ID"), comparison(3, 2, false))));
			var outcome = new TransferJobExecutionService.Outcome(prepared, TransferJobExecutionService.Phase.VERIFICATION,
					null, verification, List.of(), Map.of(), null);
			var view = new TransferVerificationTabs();
			var tabs = new JTabbedPane();
			view.show(tabs, new DirectTransferDesktopService.Executed(outcome, Path.of("missing-result.json")));
			assertEquals("direct", view.tables.table.getValueAt(0, 0));
			assertEquals("\"catalog\".\"Target\".\"日本語\"", view.tables.table.getValueAt(0, 1));
			assertEquals("Mismatch", view.tables.table.getValueAt(0, 2));
			assertEquals("3", view.tables.table.getValueAt(0, 3));
		});
	}

	@Test
	void fileTargetNamesRequireTheMatchingRecordedReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var plan = new TransferSchemaPreparation.Plan("hash", List.of(), List.of(), List.of(
					new TransferSchemaPreparation.TablePreparation("file", null, "Mixed.Case", "表\"名",
							TransferSchemaPreparation.TableAction.USE_EXISTING)));
			var review = new TransferFileImportService.Review(Path.of("missing.json"), "hash", null, "hash", plan);
			var prior = new FileTransferDesktopService.Reviewed(review, null);
			var view = new TransferVerificationTabs();
			var tabs = new JTabbedPane();
			view.show(tabs, outcome(Map.of("file", comparison(1, -1, false))), prior);
			assertEquals("\"Mixed.Case\".\"表\"\"名\"", view.tables.table.getValueAt(0, 1));
			var unrelated = new FileTransferDesktopService.Reviewed(
					new TransferFileImportService.Review(Path.of("other.json"), "other", null, "hash", plan), null);
			view.show(tabs, outcome(Map.of("file", comparison(1, -1, false))), unrelated);
			assertEquals("not recorded", view.tables.table.getValueAt(0, 1));
		});
	}
}
