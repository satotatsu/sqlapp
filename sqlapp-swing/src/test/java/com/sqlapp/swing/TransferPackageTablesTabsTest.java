/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferPackageTablesTabsTest {
	@TempDir
	Path directory;

	private TransferFileImportService.PackageDescription description(String fingerprint) throws Exception {
		var job = Files.writeString(directory.resolve("metadata.yaml"), """
				formatVersion: 1
				source: from
				target: to
				tables: [A]
				dataMode: FILE
				schemaMode: REQUIRE_EXISTING
				files: {directory: packages, format: CSV}
				""");
		return new TransferFileImportService.PackageDescription(
				new TransferFilePackageIO.Snapshot(
						directory.resolve("does-not-exist/manifest.json"), fingerprint, null),
				new TransferJobConfigurationIO().read(job).configuration(), null,
				List.of(new TransferFileImportService.PackageTable("a", null, "Source", "A", "A copy", "a.csv", 10,
						true, false,
						List.of(new TransferFileImportService.PackageColumn("ID", "ID", "INTEGER", true, true),
								new TransferFileImportService.PackageColumn("original", "renamed", "VARCHAR", false,
										false)))));
	}

	private FileTransferDesktopService.Reviewed reviewed(String fingerprint) {
		return new FileTransferDesktopService.Reviewed(new TransferFileImportService.Review(
				directory.resolve("does-not-exist/manifest.json"), fingerprint, null, "plan"), null);
	}

	@Test
	void matchingReviewAndOutcomeRetainOfflineMetadataWhileOtherResultsAndExplicitClearDiscardIt() throws Exception {
		var description = description("package-hash");
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferPackageTablesTabs();
			view.show(tabs, new FileTransferDesktopService.Inspected(description.snapshot(), false,
					description.configuration(), description));
			view.show(tabs, reviewed("package-hash"));
			assertNotNull(view.panel);
			assertEquals(1, tabs.getTabCount());
			view.show(tabs,
					new TransferFileImportService.Outcome("package-hash", "plan",
							TransferJobExecutionService.Phase.COMPLETE, null, false, java.util.Map.of(),
							java.util.Map.of(), null, null));
			assertNotNull(view.panel);
			view.show(tabs, reviewed("other-package"));
			assertNull(view.panel);
			assertEquals(0, tabs.getTabCount());
			view.show(tabs, reviewed("package-hash"));
			assertNull(view.panel);
			view.show(tabs, new FileTransferDesktopService.Inspected(description.snapshot(), false,
					description.configuration(), description));
			view.clear(tabs);
			view.show(tabs, reviewed("package-hash"));
			assertNull(view.panel);
		});
	}

	@Test
	void renameFilterComposesWithSearchAndOldColumnCallbacksCannotChangeNewTable() throws Exception {
		var description = description("package-hash");
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferPackageTablesTabs();
			view.show(tabs, new FileTransferDesktopService.Inspected(description.snapshot(), false,
					description.configuration(), description));
			view.panel.table.setRowSelectionInterval(0, 0);
			view.showColumns.doClick(0);
			assertEquals(2, view.columns.table.getRowCount());
			view.columns.table.moveColumn(6, 0);
			view.showRenamed.doClick(0);
			assertEquals(1, view.columns.table.getRowCount());
			assertEquals("original",
					view.columns.table.getModel().getValueAt(view.columns.table.convertRowIndexToModel(0), 1));
			view.columns.filter.setText("ID");
			assertEquals(0, view.columns.table.getRowCount());
			view.showAllColumns.doClick(0);
			assertEquals(2, view.columns.table.getRowCount());
			var staleFilter = view.showRenamed;
			view.showColumns.doClick(0);
			staleFilter.doClick(0);
			assertEquals(2, view.columns.table.getRowCount());
			view.showRenamed.doClick(0);
			assertEquals(1, view.columns.table.getRowCount());
		});
	}

	@Test
	void selectedPreviewUsesRecordedPackageAndTaskAndIgnoresStaleButtons() throws Exception {
		var description = description("package-hash");
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferPackageTablesTabs();
			var selection = new java.util.concurrent.atomic.AtomicReference<TransferPackageTablesTabs.PreviewSelection>();
			view.setPreviewNavigator(selection::set);
			view.show(tabs, new FileTransferDesktopService.Inspected(description.snapshot(), false,
					description.configuration(), description));
			assertFalse(view.preview.isEnabled());
			view.panel.table.moveColumn(0, 4);
			view.panel.table.setRowSelectionInterval(0, 0);
			view.preview.doClick(0);
			assertEquals(description.snapshot().file(), selection.get().manifest());
			assertEquals("package-hash", selection.get().fingerprint());
			assertEquals("a", selection.get().task());
			assertSame(description, selection.get().description());
			var stale = view.preview;
			selection.set(null);
			view.clear(tabs);
			stale.doClick(0);
			assertNull(selection.get());
		});
	}

	@Test
	void offlineNamesFollowSelectedModelRowAndStaleActionsCannotReopenColumns() throws Exception {
		var job = Files.writeString(directory.resolve("job.yaml"), """
				formatVersion: 1
				source: from
				target: to
				tables: [A, B]
				dataMode: FILE
				schemaMode: CREATE_MISSING_WITH_IDENTITY
				files: {directory: packages, format: CSV}
				""");
		var configuration = new TransferJobConfigurationIO().read(job).configuration();
		var column = new TransferFileImportService.PackageColumn("original", "renamed", "VARCHAR", false, false);
		var description = new TransferFileImportService.PackageDescription(
				new TransferFilePackageIO.Snapshot(directory.resolve("absent.json"), "hash", null), configuration, null,
				List.of(new TransferFileImportService.PackageTable("a", null, "Source", "A", "A copy", "a.csv", 10,
						true, true, List.of(column)),
						new TransferFileImportService.PackageTable("b", null, "Source", "B", "B copy", "b.csv", 2, true,
								true, List.of(column))));
		SwingUtilities.invokeAndWait(() -> {
			var tabs = new JTabbedPane();
			var view = new TransferPackageTablesTabs();
			view.show(tabs, new FileTransferDesktopService.Inspected(null, false, configuration, description));
			assertEquals(2, view.panel.table.getRowCount());
			assertFalse(view.panel.table.isCellEditable(0, 0));
			assertFalse(view.showColumns.isEnabled());
			view.panel.table.getRowSorter().toggleSortOrder(3);
			var selectedPreview = new java.util.concurrent.atomic.AtomicReference<TransferPackageTablesTabs.PreviewSelection>();
			view.setPreviewNavigator(selectedPreview::set);
			view.panel.table.moveColumn(0, 5);
			view.panel.table.setRowSelectionInterval(0, 0);
			assertEquals("b", view.panel.table.getModel().getValueAt(view.panel.table.convertRowIndexToModel(0), 0));
			view.preview.doClick(0);
			assertEquals("b", selectedPreview.get().task());
			view.showColumns.doClick(0);
			assertEquals("original", view.columns.table.getModel().getValueAt(0, 1));
			assertEquals("renamed", view.columns.table.getModel().getValueAt(0, 2));
			assertEquals("Package columns", tabs.getTitleAt(tabs.getSelectedIndex()));
			var stale = view.showColumns;
			view.clear(tabs);
			stale.doClick(0);
			assertEquals(0, tabs.getTabCount());
			assertNull(view.columns);
		});
	}
}
