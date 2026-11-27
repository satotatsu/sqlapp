/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.jdbc.bulk.*;

class TransferExecutionProgressTabsTest {
	private static final List<TransferSchemaPreparation.Statement> SQL = List.of(
			new TransferSchemaPreparation.Statement("parent", "CREATE TABLE P(ID INTEGER)"),
			new TransferSchemaPreparation.Statement("child", "CREATE TABLE C(ID INTEGER)"),
			new TransferSchemaPreparation.Statement("pending", "CREATE TABLE NEXT(ID INTEGER)"));

	private FileTransferDesktopService.Reviewed prior(String fingerprint) {
		var tables = List.of("parent", "child", "pending").stream()
				.map(id -> new TransferSchemaPreparation.TablePreparation(id, null, "Target", id,
						TransferSchemaPreparation.TableAction.CREATE))
				.toList();
		return new FileTransferDesktopService.Reviewed(new TransferFileImportService.Review(Path.of("missing.json"),
				fingerprint, null, "hash", new TransferSchemaPreparation.Plan("hash", SQL, List.of(), tables)), null);
	}

	private TransferFileImportService.Outcome partial(TransferJobExecutionService.Phase phase, int completed,
			Integer uncertain) {
		return new TransferFileImportService.Outcome("hash", "hash", phase, null, true, Map.of(),
				Map.of("parent", new ChunkedBulkMigrationProgress("parent", 1, 5, 5, 10), "child",
						new ChunkedBulkMigrationProgress("child", 0, 3, 0, 3)),
				"child", null, new TransferSchemaPreparation.Progress(SQL, completed, uncertain));
	}

	@Test
	void partialCopyDistinguishesCommittedEvidenceFromCompletionAndMissingRecords() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var view = new TransferExecutionProgressTabs();
			var tabs = new JTabbedPane();
			var navigated = new AtomicReference<String>();
			view.setNavigators(navigated::set, id -> fail("No recorded verification"));
			view.show(tabs, partial(TransferJobExecutionService.Phase.COPY, 3, null), prior("hash"));
			assertEquals(3, view.panel.table.getRowCount());
			assertEquals("10", view.panel.table.getModel().getValueAt(0, 4));
			assertEquals("No copy completion recorded", view.panel.table.getModel().getValueAt(0, 3));
			assertEquals("not recorded", view.panel.table.getModel().getValueAt(2, 4));
			assertEquals("Not recorded", view.panel.table.getModel().getValueAt(2, 5));
			assertFalse(view.panel.table.isCellEditable(0, 0));
			view.panel.table.getRowSorter().toggleSortOrder(4);
			view.panel.table.moveColumn(0, 5);
			view.showStopped.doClick(0);
			assertEquals(1, view.panel.table.getRowCount());
			assertTrue(view.showSql.isEnabled());
			assertFalse(view.showVerification.isEnabled());
			view.showSql.doClick(0);
			assertEquals("child", navigated.get());
			assertTrue(view.panel.selected.getText().contains("Stopped task during COPY"));
			assertTrue(view.panel.selected.getText().contains("\"Target\".\"child\""));
		});
	}

	@Test
	void uncertainSchemaStatementsNavigateToRecordedSqlAndDoNotMarkLaterTasksAsFailed() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var statements = new TransferSchemaStatementTabs();
			var view = new TransferExecutionProgressTabs();
			var outcome = new TransferFileImportService.Outcome("hash", "hash",
					TransferJobExecutionService.Phase.SCHEMA_PREPARATION, null, true, Map.of(), Map.of(), "child", null,
					new TransferSchemaPreparation.Progress(SQL, 1, 2));
			statements.show(tabs, outcome);
			view.setNavigators(id -> statements.showTask(tabs, id), null);
			view.show(tabs, outcome, prior("hash"));
			view.showIssues.doClick(0);
			assertEquals(1, view.panel.table.getRowCount());
			view.panel.table.setRowSelectionInterval(0, 0);
			assertTrue(view.panel.selected.getText().contains("Uncertain SQL attempt"));
			assertTrue(view.panel.selected.getText().contains("0/1 completed; uncertain: 1"));
			view.showSql.doClick(0);
			assertNotEquals(view.panel, tabs.getSelectedComponent());
			var table = (ReportDetailTablePanel) tabs.getSelectedComponent();
			assertEquals(1, table.table.getRowCount());
			assertEquals("Uncertain attempt", table.table.getValueAt(0, 2));
			assertTrue(table.selected.getText().contains("CREATE TABLE C(ID INTEGER)"));
		});
	}

	private DirectTransferDesktopService.Executed direct(boolean schemaOnly) {
		var configuration = new TransferJobConfiguration(1, "from", "to", List.of("ITEMS"),
				TransferJobConfiguration.SchemaMode.CREATE_MISSING, null, null, null, null, null,
				schemaOnly ? TransferJobConfiguration.DataMode.SCHEMA_ONLY
						: TransferJobConfiguration.DataMode.JDBC_DIRECT,
				null);
		var job = new TransferJobConfigurationIO.Snapshot(Path.of("missing.yaml"), "", "hash", configuration);
		var prepared = new TransferJobPreparationService.Prepared(Path.of("missing"), job, Path.of("missing.xml"), null,
				"hash", List.of("parent", "child", "pending"));
		var verification = new BulkMigrationJobVerificationResult(List.of(
				new BulkMigrationJobTaskVerificationResult("parent", List.of("ID"), new BulkMigrationVerificationResult(
						1, 1, 1, List.of(new BulkMigrationVerificationChunk(0, 1, 1, "a", "b"))))));
		return new DirectTransferDesktopService.Executed(
				new TransferJobExecutionService.Outcome(prepared, TransferJobExecutionService.Phase.VERIFICATION, null,
						schemaOnly ? null : verification, schemaOnly ? List.of() : List.of("parent"), Map.of(), null),
				Path.of("missing-result.json"));
	}

	@Test
	void directCompletionAndMismatchRemainIndependentAndNavigationClearsOtherTaskFilters() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferExecutionProgressTabs();
			var verification = new TransferVerificationTabs();
			var value = direct(false);
			verification.show(tabs, value);
			view.setNavigators(null, id -> verification.showTask(tabs, id));
			view.show(tabs, value, null);
			assertEquals("Copy completion recorded; result unavailable", view.panel.table.getValueAt(0, 3));
			assertEquals("Mismatch", view.panel.table.getValueAt(0, 5));
			assertEquals("not recorded", view.panel.table.getValueAt(0, 4));
			assertFalse(view.showStopped.isEnabled());
			view.showIssues.doClick(0);
			view.panel.table.setRowSelectionInterval(0, 0);
			verification.tables.filter.setText("hidden");
			view.showVerification.doClick(0);
			assertEquals(verification.tables, tabs.getSelectedComponent());
			assertEquals(1, verification.tables.table.getRowCount());
			assertTrue(verification.showChunks.isEnabled());
		});
	}

	@Test
	void schemaOnlyAndMismatchedReviewsDoNotInventCopyOrTargetEvidence() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferExecutionProgressTabs();
			view.show(tabs, direct(true), null);
			assertEquals("Not requested (schema only)", view.panel.table.getValueAt(0, 3));
			assertEquals("Not applicable", view.panel.table.getValueAt(0, 5));
			view.show(tabs, partial(TransferJobExecutionService.Phase.COPY, 3, null), prior("other"));
			assertEquals("not recorded", view.panel.table.getValueAt(0, 1));
		});
	}

	@Test
	void completedCopyResultAndZeroRowsAreExplicitButDoNotInventChunkProgress() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var copy = new BulkMigrationJobResult(
					List.of(new BulkMigrationJobTaskResult("empty", new ChunkedBulkMigrationResult(0, 0, 0, false))));
			var outcome = new TransferFileImportService.Outcome("hash", "hash",
					TransferJobExecutionService.Phase.COMPLETE, copy, false, Map.of(), Map.of(), null, null);
			var view = new TransferExecutionProgressTabs();
			var tabs = new JTabbedPane();
			view.show(tabs, outcome, null);
			assertEquals("Copy completed; processed rows: 0", view.panel.table.getValueAt(0, 3));
			assertEquals("not recorded", view.panel.table.getValueAt(0, 4));
			assertEquals("Not requested", view.panel.table.getValueAt(0, 5));
		});
	}

	@Test
	void removedResultsAndStaleButtonsCannotNavigateOrRestoreEvidence() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var view = new TransferExecutionProgressTabs();
			var tabs = new JTabbedPane();
			tabs.addTab("Summary", new JPanel());
			view.setNavigators(id -> fail("Stale navigation"), null);
			view.show(tabs, partial(TransferJobExecutionService.Phase.COPY, 3, null), prior("hash"));
			view.showStopped.doClick(0);
			var stopped = view.showStopped;
			var sql = view.showSql;
			var issues = view.showIssues;
			view.clear(tabs);
			stopped.doClick(0);
			sql.doClick(0);
			issues.doClick(0);
			assertEquals(1, tabs.getTabCount());
			view.show(tabs, new Object(), null);
			assertEquals(1, tabs.getTabCount());
		});
	}
}
