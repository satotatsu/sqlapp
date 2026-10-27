/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SchemaSnapshot;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.TableChoice;

class JobDraftGuidanceTest {

	@Test
	void emptySchemaAndIneligibleTablesExplainHowToProceedWithoutEnablingSave() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var unread = new JobDraftPanel(yaml -> fail("No draft expected"));
			assertTrue(unread.selectionHelp.getText().contains("Read Schema"));
			final var empty = managed(List.of());
			assertTrue(empty.selectionHelp.getText().contains("No tables in this Schema"));
			assertFalse(empty.create.isEnabled());
			final var unavailable = managed(List.of(new TableChoice("PUBLIC.LOGS", List.of(), "Primary key required")));
			assertTrue(unavailable.selectionHelp.getText().contains("Availability"));
			assertTrue(unavailable.selectionHelp.getText().contains("primary keys"));
			unavailable.select.doClick(0);
			assertFalse(unavailable.create.isEnabled());
		});
	}

	@Test
	void filterAndSelectionGuidanceRetainsHiddenSelectionsAndExplainsReviewBoundary() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = managed(List.of(new TableChoice("PUBLIC.ITEMS", List.of("ID"), null),
					new TableChoice("PUBLIC.ORDERS", List.of("ID"), null)));
			assertTrue(panel.selectionHelp.getText().contains("Tick Include"));
			assertFalse(panel.create.isEnabled());
			panel.filter.setText("absent");
			assertTrue(panel.selectionHelp.getText().contains("No tables match"));
			assertFalse(panel.create.isEnabled());
			panel.filter.setText("ITEMS");
			panel.select.doClick(0);
			assertTrue(panel.selectionHelp.getText().contains("does not copy rows"));
			assertTrue(panel.create.isEnabled());
			panel.filter.setText("ORDERS");
			assertTrue(panel.selectionHelp.getText().contains("Hidden selections"));
			assertTrue(panel.create.isEnabled());
			panel.filter.setText("absent");
			assertTrue(panel.selectionHelp.getText().contains("will still be included"));
			assertTrue(panel.create.isEnabled());
			panel.clearFilter.doClick(0);
			assertTrue(panel.selectionHelp.getText().contains("does not copy rows"));
			assertEquals(Boolean.TRUE, panel.model.getValueAt(0, 0));
			assertEquals(Boolean.FALSE, panel.model.getValueAt(1, 0));
			panel.clearAll.doClick(0);
			assertTrue(panel.selectionHelp.getText().contains("Tick Include"));
			assertFalse(panel.create.isEnabled());
		});
	}

	private static JobDraftPanel managed(List<TableChoice> tables) {
		return new JobDraftPanel(new SchemaSnapshot(Path.of("schema.xml"), "sha256:" + "a".repeat(64), tables),
				Path.of("."), saved -> fail("Guidance must not save or plan automatically"));
	}
}
