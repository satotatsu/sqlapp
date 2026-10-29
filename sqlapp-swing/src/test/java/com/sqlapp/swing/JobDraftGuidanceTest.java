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
	void unavailableFilterPreservesHiddenSelectionsAndOriginalRestrictions() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = managed(List.of(new TableChoice("PUBLIC.ITEMS", List.of("ID"), null),
					new TableChoice("PUBLIC.LOGS", List.of(), "Primary key required"),
					new TableChoice("PUBLIC.COMPLEX", List.of("ID"), "Unsupported complex value")));
			panel.select.doClick(0);
			panel.unavailableOnly.doClick(0);
			assertEquals(2, panel.tables.getRowCount());
			assertTrue(panel.count.getText().contains("1 selected (1 hidden)"));
			assertTrue(panel.count.getText().contains("data-copy eligible: 1; unavailable: 2"));
			assertTrue(panel.selectionHelp.getText().contains("Existing selections remain included"));
			panel.filter.setText("complex");
			assertEquals(1, panel.tables.getRowCount());
			assertEquals("Unsupported complex value", panel.tables.getValueAt(0, 3));
			assertFalse(panel.tables.isCellEditable(0, 0));
			panel.select.doClick(0);
			assertEquals(Boolean.TRUE, panel.model.getValueAt(0, 0));
			assertEquals(Boolean.FALSE, panel.model.getValueAt(2, 0));
			assertTrue(panel.create.isEnabled());
			assertFalse(panel.useSchemaOnly.isVisible());
		});
	}

	@Test
	void schemaOnlyShortcutRequiresExplicitClickAndRestoresDataCopyRestrictions() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = managed(List.of(new TableChoice("PUBLIC.LOGS", List.of(), "Primary key required")));
			var clicks = new java.util.concurrent.atomic.AtomicInteger();
			panel.offerSchemaOnly(() -> {
				clicks.incrementAndGet();
				panel.schemaOnly(true);
			});
			assertEquals(0, clicks.get());
			assertTrue(panel.useSchemaOnly.isVisible());
			panel.unavailableOnly.doClick(0);
			panel.useSchemaOnly.doClick(0);
			assertEquals(1, clicks.get());
			assertFalse(panel.unavailableOnly.isSelected());
			assertFalse(panel.unavailableOnly.isEnabled());
			assertFalse(panel.useSchemaOnly.isVisible());
			assertTrue(panel.model.isCellEditable(0, 0));
			panel.select.doClick(0);
			assertTrue(panel.create.isEnabled());
			panel.schemaOnly(false);
			assertFalse(panel.model.isCellEditable(0, 0));
			assertEquals(Boolean.FALSE, panel.model.getValueAt(0, 0));
			assertFalse(panel.create.isEnabled());
			assertTrue(panel.useSchemaOnly.isVisible());
		});
	}

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
