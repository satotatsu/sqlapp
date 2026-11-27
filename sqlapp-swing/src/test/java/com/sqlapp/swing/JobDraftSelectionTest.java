/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;

class JobDraftSelectionTest {
	@Test
	void schemaOnlySelectionAllowsKeylessTablesAndClearsThemWhenReturningToCopyMode() throws Exception {
		var panel = open(new AtomicReference<>(), new AtomicReference<>());
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(panel.model.isCellEditable(3, 0));
			panel.schemaOnly(true);
			assertTrue(panel.model.isCellEditable(3, 0));
			panel.filter.setText("NO_KEY");
			panel.select.doClick(0);
			assertTrue((Boolean) panel.model.getValueAt(3, 0));
			panel.schemaOnly(false);
			assertFalse((Boolean) panel.model.getValueAt(3, 0));
			assertFalse(panel.model.isCellEditable(3, 0));
		});
	}

	private static BulkMigrationJobDraftBuilder builder(AtomicReference<List<String>> selected) {
		return new BulkMigrationJobDraftBuilder() {
			@Override
			public SchemaSnapshot inspect(Path file) {
				assertFalse(SwingUtilities.isEventDispatchThread());
				return new SchemaSnapshot(file, "sha256:" + "a".repeat(64),
						List.of(new TableChoice("PUBLIC.CUSTOMERS", List.of("CUSTOMER_ID"), null),
								new TableChoice("PUBLIC.ORDERS", List.of("ORDER_ID"), null),
								new TableChoice("PUBLIC.AUDIT", List.of("AUDIT_ID"), null),
								new TableChoice("PUBLIC.NO_KEY", List.of(), "Primary key required")));
			}

			@Override
			public String draft(SchemaSnapshot schema, List<String> tables, String job, int chunk, boolean verify) {
				assertFalse(SwingUtilities.isEventDispatchThread());
				selected.set(List.copyOf(tables));
				return "draft";
			}
		};
	}

	@Test
	void visibleSelectionAndClearPreserveHiddenSelectionsAndDraftReceivesAllSelectedTables() throws Exception {
		final var selected = new AtomicReference<List<String>>();
		final var draft = new AtomicReference<String>();
		final var panel = open(selected, draft);
		SwingUtilities.invokeAndWait(() -> {
			panel.filter.setText("customers");
			panel.select.doClick(0);
			panel.filter.setText("orders");
			assertTrue(panel.count.getText().contains("1 selected (1 hidden)"));
			panel.select.doClick(0);
			panel.clear.doClick(0);
			assertTrue(panel.count.getText().contains("1 selected (1 hidden)"));
			panel.select.doClick(0);
			panel.filter.setText("NO_KEY");
			panel.select.doClick(0);
			assertFalse((Boolean) panel.model.getValueAt(3, 0));
			assertTrue(panel.count.getText().contains("2 selected (2 hidden)"));
			assertTrue(panel.create.isEnabled());
		});
		finish(panel, panel::createDraft);
		assertEquals(List.of("PUBLIC.CUSTOMERS", "PUBLIC.ORDERS"), selected.get());
		assertEquals("draft", draft.get());
	}

	@Test
	void sortedBulkActionsCaptureModelRowsAndClearAllCanRemoveHiddenSelections() throws Exception {
		final var panel = open(new AtomicReference<>(), new AtomicReference<>());
		SwingUtilities.invokeAndWait(() -> {
			panel.tables.getRowSorter().toggleSortOrder(0);
			panel.select.doClick(0);
			assertTrue(panel.count.getText().contains("3 selected (0 hidden)"));
			assertFalse((Boolean) panel.model.getValueAt(3, 0));
			panel.filter.setText("[a].*");
			assertEquals(0, panel.tables.getRowCount());
			assertFalse(panel.select.isEnabled());
			assertFalse(panel.clear.isEnabled());
			assertTrue(panel.count.getText().contains("3 selected (3 hidden)"));
			panel.clearAll.doClick(0);
			assertTrue(panel.count.getText().contains("0 selected (0 hidden)"));
			assertFalse(panel.create.isEnabled());
			panel.clearFilter.doClick(0);
			assertEquals(4, panel.tables.getRowCount());
			panel.filter.setText("order_id");
			assertEquals(1, panel.tables.getRowCount());
			panel.filter.setText("primary key required");
			assertEquals(1, panel.tables.getRowCount());
		});
	}

	@Test
	void selectedOnlyCombinesWithSearchAndDraftStillIncludesHiddenSelections() throws Exception {
		final var selected = new AtomicReference<List<String>>();
		final var draft = new AtomicReference<String>();
		final var panel = open(selected, draft);
		SwingUtilities.invokeAndWait(() -> {
			panel.filter.setText("customers");
			panel.select.doClick(0);
			panel.filter.setText("orders");
			panel.select.doClick(0);
			panel.clearFilter.doClick(0);
			panel.selectedOnly.doClick(0);
			assertEquals(2, panel.tables.getRowCount());
			panel.tables.getRowSorter().toggleSortOrder(1);
			panel.filter.setText("orders");
			assertEquals(1, panel.tables.getRowCount());
			assertTrue(panel.count.getText().contains("2 selected (1 hidden)"));
			panel.clear.doClick(0);
			assertEquals(0, panel.tables.getRowCount());
			assertFalse(panel.clear.isEnabled());
			assertTrue(panel.create.isEnabled());
			assertEquals(Boolean.TRUE, panel.model.getValueAt(0, 0));
			assertEquals(Boolean.FALSE, panel.model.getValueAt(1, 0));
			panel.filter.setText("[no matches].*");
		});
		finish(panel, panel::createDraft);
		assertEquals(List.of("PUBLIC.CUSTOMERS"), selected.get());
		assertEquals("draft", draft.get());
	}

	@Test
	void selectedOnlyUpdatesWhenUncheckedAndEmptySelectionCanReturnToAllTables() throws Exception {
		final var panel = open(new AtomicReference<>(), new AtomicReference<>());
		SwingUtilities.invokeAndWait(() -> {
			panel.selectedOnly.doClick(0);
			assertEquals(0, panel.tables.getRowCount());
			assertTrue(panel.selectionHelp.getText().contains("Turn off Selected tables only"));
			assertFalse(panel.select.isEnabled());
			panel.selectedOnly.doClick(0);
			panel.select.doClick(0);
			panel.selectedOnly.doClick(0);
			assertEquals(3, panel.tables.getRowCount());
			final int modelRow = panel.tables.convertRowIndexToModel(0);
			panel.tables.setValueAt(false, 0, 0);
			assertEquals(Boolean.FALSE, panel.model.getValueAt(modelRow, 0));
			assertEquals(2, panel.tables.getRowCount());
			panel.clearAll.doClick(0);
			assertEquals(0, panel.tables.getRowCount());
			assertFalse(panel.create.isEnabled());
			panel.selectedOnly.doClick(0);
			assertEquals(4, panel.tables.getRowCount());
		});
	}

	@Test
	void rereadingSameSchemaSucceedsAndResetsThePreviousFilterAndSelection() throws Exception {
		final var panel = open(new AtomicReference<>(), new AtomicReference<>());
		SwingUtilities.invokeAndWait(() -> {
			panel.select.doClick(0);
			panel.filter.setText("customers");
			panel.selectedOnly.doClick(0);
			panel.unavailableOnly.doClick(0);
		});
		finish(panel, () -> {
			panel.inspectSchema();
			assertFalse(panel.filter.isEnabled());
			assertFalse(panel.selectedOnly.isEnabled());
			assertFalse(panel.unavailableOnly.isEnabled());
			assertFalse(panel.clearAll.isEnabled());
		});
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(4, panel.tables.getRowCount());
			assertTrue(panel.filter.getText().isEmpty());
			assertFalse(panel.selectedOnly.isSelected());
			assertFalse(panel.unavailableOnly.isSelected());
			assertEquals("4 / 4 tables; 0 selected (0 hidden); data-copy eligible: 3; unavailable: 1",
					panel.count.getText());
			assertTrue(panel.fingerprint.getText().startsWith("sha256:"));
			assertFalse(panel.create.isEnabled());
		});
	}

	private static JobDraftPanel open(AtomicReference<List<String>> selected, AtomicReference<String> draft)
			throws Exception {
		final var ref = new AtomicReference<JobDraftPanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new JobDraftPanel(builder(selected), draft::set);
			ref.set(panel);
			panel.schemaFile.setText("schema.xml");
		});
		finish(ref.get(), ref.get()::inspectSchema);
		return ref.get();
	}

	private static void finish(JobDraftPanel panel, Runnable action) throws Exception {
		final var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			panel.inspect.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			action.run();
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
	}
}
