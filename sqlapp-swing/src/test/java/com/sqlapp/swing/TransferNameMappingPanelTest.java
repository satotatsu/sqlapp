/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SourceColumn;

class TransferNameMappingPanelTest {
	@TempDir
	Path directory;

	@Test
	void discoversColumnsWithoutOverwritingRenamesAndKeepsUnchangedColumnsOutOfYaml() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			var io = new TransferNameMappingIO();
			panel.load(io.validate(
					"formatVersion: 1\ntables:\n  PUBLIC.注文:\n    targetTable: 履歴\n    columns: {名前: 顧客名, 古い列: 保持}\n"));
			var discovered = java.util.Map.of("PUBLIC.注文",
					java.util.List.of(new SourceColumn("名前", "VARCHAR"), new SourceColumn("数量", "INTEGER")));
			panel.includeColumns(discovered);
			panel.includeColumns(discovered);
			assertEquals(1, panel.tabs.getSelectedIndex());
			assertEquals(3, panel.columns.getRowCount());
			assertEquals("顧客名", panel.columns.getValueAt(0, 2));
			assertEquals("VARCHAR", panel.columns.getValueAt(0, 3));
			assertFalse(panel.columns.isCellEditable(0, 3));
			var mapping = panel.configuration().tables().get("PUBLIC.注文");
			assertEquals("履歴", mapping.targetTable());
			assertEquals(java.util.Map.of("名前", "顧客名", "古い列", "保持"), mapping.columns());
			panel.columns.setValueAt("個数", 2, 2);
			assertEquals("個数", panel.configuration().tables().get("PUBLIC.注文").columns().get("数量"));
		});
	}

	@Test
	void invalidFormIsNotPartiallyChangedByDiscovery() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", null });
			panel.columns.addRow(new String[] { "A", "", "" });
			assertThrows(IllegalArgumentException.class, () -> panel
					.includeColumns(java.util.Map.of("A", java.util.List.of(new SourceColumn("ID", "INTEGER")))));
			assertEquals(1, panel.columns.getRowCount());
		});
	}

	@Test
	void preservesNamesAndColumnSwapsAcrossSaveAndReopen() throws Exception {
		var io = new TransferNameMappingIO();
		var original = io.validate("""
				formatVersion: 1
				tables:
				  PUBLIC.注文:
				    targetTable: 注文履歴
				    columns: {A: B, B: A, ' 名前 ': ' 顧客名 '}
				  PUBLIC.ITEMS: {}
				""");
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.load(original);
			assertEquals(original, panel.configuration());
			panel.tables.setValueAt("商品", 1, 1);
			panel.columns.addRow(new String[] { "PUBLIC.追加", "元", "先" });
			var changed = panel.configuration();
			assertEquals("商品", changed.tables().get("PUBLIC.ITEMS").targetTable());
			assertNull(changed.tables().get("PUBLIC.追加").targetTable());
			assertEquals("先", changed.tables().get("PUBLIC.追加").columns().get("元"));
			panel.load(io.validate(io.format(changed)));
			assertEquals(changed, panel.configuration());
		});
	}

	@Test
	void rejectsDuplicatesInvalidNamesAndUnfinishedRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "X" });
			panel.tables.addRow(new String[] { "A", "Y" });
			assertThrows(IllegalArgumentException.class, panel::configuration);
			panel.tables.removeRow(1);
			panel.columns.addRow(new String[] { "A", "C", "D" });
			panel.columns.addRow(new String[] { "A", "C", "E" });
			assertThrows(IllegalArgumentException.class, panel::configuration);
			panel.columns.removeRow(1);
			panel.tables.setValueAt("schema.table", 0, 1);
			assertThrows(IllegalArgumentException.class, panel::configuration);
			panel.tables.setValueAt("X", 0, 1);
			panel.columns.addRow(new String[3]);
			assertThrows(IllegalArgumentException.class, panel::configuration);
		});
	}

	@Test
	void commitsActiveCellBeforeBuildingConfiguration() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "B" });
			assertTrue(panel.tableGrid.editCellAt(0, 1));
			((JTextField) panel.tableGrid.getEditorComponent()).setText("編集後");
			assertEquals("編集後", panel.configuration().tables().get("A").targetTable());
		});
	}

	@Test
	void pinnedWritesRejectExternalChangesAndProtectJobAndPackages() throws Exception {
		var io = new TransferNameMappingIO();
		var file = directory.resolve("names.yaml");
		var saved = io.write(file, "formatVersion: 1\ntables: {}\n", null);
		Files.writeString(file, saved.text() + "# external edit\n");
		assertThrows(java.io.IOException.class, () -> io.write(file, saved.text(), saved.fingerprint()));
		var job = Files.writeString(directory.resolve("job.yaml"), "job");
		assertThrows(java.io.IOException.class, () -> TransferNameMappingDialog.checkDestination(job, job));
		Files.writeString(directory.resolve("manifest.json"), "{}");
		Files.writeString(directory.resolve("source-schema.xml"), "schema");
		assertThrows(java.io.IOException.class, () -> TransferNameMappingDialog.checkDestination(job, file));
	}
}
