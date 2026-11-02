/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.*;

class TransferNameMappingSchemaPanelTest {
	@Test
	void showsBuilderDefinitionAndUnavailableEvidenceAndInvalidatesItWhenTargetChanges() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			var result = new Result(Path.of("schema.xml"), "source", "mapping", List.of(new Table("A", "B",
					List.of(new Column("PRICE", "金額", "DOUBLE", false, true, "DOUBLE PRECISION DEFAULT 0", null),
							new Column("OPAQUE", "OPAQUE", "OTHER", false, true, null, "Target preview unavailable")))),
					List.of(), "Postgres16");
			panel.showResult(result);
			assertEquals("DOUBLE PRECISION DEFAULT 0", panel.model.getValueAt(0, 7));
			assertEquals("Unavailable", panel.model.getValueAt(1, 7));
			assertEquals("Target preview unavailable", panel.model.getValueAt(1, 8));
			assertEquals(5, panel.details.table.convertColumnIndexToView(7));
			assertTrue(panel.evidence.getText().contains("unavailable column previews: 1"));
			panel.details.filter.setText("DOUBLE PRECISION");
			assertEquals(1, panel.details.table.getRowCount());
			var settings = new TransferMappingTargetPreviewPanel(panel::changed);
			settings.product.setSelectedItem("PostgreSQL");
			assertEquals(0, panel.model.getRowCount());
			assertFalse(panel.evidence.getText().contains("Postgres16"));
		});
	}

	private Result result() {
		return new Result(Path.of("schema.xml"), "sha256:source", "sha256:mapping",
				List.of(new Table("PUBLIC.顧客", "PEOPLE", List.of(new Column("識別子", "ID", "INT", true, false)))),
				List.of("[SCHEMA_MAPPING_EXPRESSIONS] Inspect expressions."));
	}

	@Test
	void displaysReadOnlyEvidenceWithoutClaimingCurrentTargetState() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			panel.showResult(result());
			assertEquals(1, panel.model.getRowCount());
			assertFalse(panel.model.isCellEditable(0, 0));
			assertEquals("PEOPLE", panel.model.getValueAt(0, 2));
			assertEquals("Yes", panel.model.getValueAt(0, 5));
			assertEquals("No", panel.model.getValueAt(0, 6));
			assertTrue(panel.evidence.getText().contains("sha256:source"));
			assertTrue(panel.evidence.getText().contains("sha256:mapping"));
			assertTrue(panel.evidence.getText().contains("SCHEMA_MAPPING_EXPRESSIONS"));
			assertTrue(panel.evidence.getText().contains("Database Review is still required"));
			panel.details.filter.setText("識別子");
			assertEquals(1, panel.details.table.getRowCount());
			panel.details.filter.setText("missing");
			assertEquals(0, panel.details.table.getRowCount());
		});
	}

	@Test
	void editsClearCapturedEvidenceAndFailuresDoNotRetainSuccessfulRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = new TransferNameMappingPanel();
			editor.tables.addRow(new String[] { "A", null });
			editor.schemaCheck.showResult(result());
			editor.tables.setValueAt("NEW", 0, 1);
			assertEquals(0, editor.schemaCheck.model.getRowCount());
			assertFalse(editor.schemaCheck.evidence.getText().contains("sha256:source"));
			editor.schemaCheck.showResult(result());
			editor.schemaCheck.changed();
			assertEquals(0, editor.schemaCheck.model.getRowCount());
			editor.schemaCheck.showResult(result());
			editor.schemaCheck.clear("Schema check failed: column missing");
			assertEquals(0, editor.schemaCheck.model.getRowCount());
			assertEquals("Schema check failed: column missing", editor.schemaCheck.evidence.getText());
		});
	}
}
