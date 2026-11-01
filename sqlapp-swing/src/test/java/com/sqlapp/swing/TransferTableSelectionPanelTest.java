/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.DataMode;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class TransferTableSelectionPanelTest {
	@TempDir
	Path directory;

	private Path schema() throws Exception {
		var schema = new Schema("PUBLIC");
		var parent = new Table("PARENTS");
		var child = new Table("CHILDREN");
		parent.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		parent.setPrimaryKey("PK_P", parent.getColumns().get("ID"));
		child.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT));
		child.getConstraints().addForeignKeyConstraint("FK_P", child.getColumns().get("PARENT_ID"),
				parent.getColumns().get("ID"));
		schema.getTables().add(parent);
		schema.getTables().add(child);
		var file = directory.resolve("source-schema.xml");
		schema.writeXml(file.toFile());
		return file;
	}

	private void idle(BooleanSupplier busy) throws Exception {
		var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			var timer = new javax.swing.Timer(10, event -> {
				if (!busy.getAsBoolean()) {
					((javax.swing.Timer) event.getSource()).stop();
					done.countDown();
				}
			});
			timer.start();
		});
		assertTrue(done.await(8, TimeUnit.SECONDS));
	}

	private int row(JobDraftPanel panel, String table) {
		for (int row = 0; row < panel.model.getRowCount(); row++)
			if (table.equals(panel.model.getValueAt(row, 1)))
				return row;
		throw new AssertionError(table);
	}

	private TransferTableSelectionPanel load(DataMode mode, List<String> selected, AtomicReference<List<String>> chosen)
			throws Exception {
		var panel = new AtomicReference<TransferTableSelectionPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var value = new TransferTableSelectionPanel(directory.resolve("job.yaml"), selected, mode, chosen::set,
					() -> {
					});
			panel.set(value);
			value.read.doClick(0);
		});
		idle(panel.get()::isBusy);
		return panel.get();
	}

	@Test
	void reopensKeylessDirectSelectionIncludesParentsAndUsesHiddenSelectionsWithoutWriting() throws Exception {
		var file = schema();
		byte[] original = Files.readAllBytes(file);
		var chosen = new AtomicReference<List<String>>();
		var panel = load(DataMode.JDBC_DIRECT, List.of("CHILDREN"), chosen);
		SwingUtilities.invokeAndWait(() -> {
			assertNotNull(panel.selector);
			assertEquals(List.of("PUBLIC.CHILDREN"), panel.selector.selectedTables());
			panel.selector.includeReferenced.doClick(0);
		});
		idle(panel::isBusy);
		SwingUtilities.invokeAndWait(() -> {
			panel.selector.filter.setText("missing");
			panel.selector.create.doClick(0);
		});
		idle(panel::isBusy);
		assertEquals(List.of("PUBLIC.PARENTS", "PUBLIC.CHILDREN"), chosen.get());
		assertFalse(Files.exists(directory.resolve("job.yaml")));
		assertArrayEquals(original, Files.readAllBytes(file));
	}

	@Test
	void fileRouteRetainsKeyRequirementsAndUnavailableSelectionsCanBeRemoved() throws Exception {
		schema();
		var chosen = new AtomicReference<List<String>>();
		var panel = load(DataMode.FILE, List.of("PARENTS"), chosen);
		SwingUtilities.invokeAndWait(() -> {
			int child = row(panel.selector, "PUBLIC.CHILDREN");
			assertFalse(panel.selector.model.isCellEditable(child, 0));
			panel.selector.model.setValueAt(true, child, 0);
			assertTrue(panel.selector.model.isCellEditable(child, 0));
			panel.selector.create.doClick(0);
			assertNull(chosen.get());
			assertTrue(panel.selector.status.getText().contains("unavailable"));
			panel.selector.model.setValueAt(false, child, 0);
			panel.selector.create.doClick(0);
		});
		idle(panel::isBusy);
		assertEquals(List.of("PUBLIC.PARENTS"), chosen.get());
	}

	@Test
	void schemaOnlyAllowsKeylessDefinitionsAndChangedPathsCannotPublishAnOldSelection() throws Exception {
		schema();
		var chosen = new AtomicReference<List<String>>();
		var panel = load(DataMode.SCHEMA_ONLY, List.of("CHILDREN"), chosen);
		var stale = new AtomicReference<JobDraftPanel>();
		SwingUtilities.invokeAndWait(() -> {
			stale.set(panel.selector);
			panel.schemaFile.setText(directory.resolve("another.xml").toString());
			assertNull(panel.selector);
			stale.get().create.doClick(0);
		});
		idle(stale.get()::isBusy);
		assertNull(chosen.get());
		SwingUtilities.invokeAndWait(() -> {
			panel.schemaFile.setText(directory.resolve("source-schema.xml").toString());
			panel.read.doClick(0);
		});
		idle(panel::isBusy);
		SwingUtilities.invokeAndWait(() -> panel.selector.create.doClick(0));
		idle(panel::isBusy);
		assertEquals(List.of("PUBLIC.CHILDREN"), chosen.get());
	}

	@Test
	void changedSchemaBytesCannotPublishSelectionAndMissingSavedTablesAreExplained() throws Exception {
		var file = schema();
		var chosen = new AtomicReference<List<String>>();
		var panel = load(DataMode.JDBC_DIRECT, List.of("PARENTS"), chosen);
		Files.writeString(file, Files.readString(file) + "\n<!-- changed -->");
		SwingUtilities.invokeAndWait(() -> panel.selector.create.doClick(0));
		idle(panel::isBusy);
		assertNull(chosen.get());
		var missing = load(DataMode.JDBC_DIRECT, List.of("MISSING"), chosen);
		SwingUtilities.invokeAndWait(() -> {
			assertNull(missing.selector);
			assertTrue(missing.status.getText().contains("MISSING"));
		});
	}
}
