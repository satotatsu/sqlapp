/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;

class JobDraftReferencedTablesTest {
	@TempDir
	Path directory;

	@Test
	void addsHiddenParentsOnlyOnExplicitActionWithoutSavingOrChangingFilter() throws Exception {
		var schema = fixture(false);
		var ref = new AtomicReference<JobDraftPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new JobDraftPanel(schema, directory, saved -> fail("Selection must not save"));
			ref.set(panel);
			selectChild(panel);
			assertFalse(included(panel, "PUBLIC.ROOTS"));
		});
		finish(ref.get());
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			assertTrue(included(panel, "PUBLIC.ROOTS"));
			assertTrue(included(panel, "PUBLIC.PARENTS"));
			assertTrue(included(panel, "PUBLIC.CHILDREN"));
			assertFalse(included(panel, "PUBLIC.OTHER"));
			assertEquals("CHILDREN", panel.filter.getText());
			assertTrue(panel.count.getText().contains("3 selected (2 hidden)"));
			assertTrue(panel.status.getText().contains("Added 2 referenced"));
			assertTrue(panel.statusPanel.detailsText().contains("PUBLIC.ROOTS"));
			assertTrue(panel.statusPanel.detailsText().contains("No job was saved"));
		});
		assertFalse(Files.exists(directory.resolve("migration.yaml")));
	}

	@Test
	void unavailableParentLeavesWholeSelectionIntactAndSchemaOnlyCanIncludeIt() throws Exception {
		var schema = fixture(true);
		var ref = new AtomicReference<JobDraftPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new JobDraftPanel(schema, directory, saved -> fail("Selection must not save"));
			ref.set(panel);
			selectChild(panel);
		});
		finish(ref.get());
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			assertFalse(included(panel, "PUBLIC.ROOTS"));
			assertFalse(included(panel, "PUBLIC.PARENTS"));
			assertTrue(included(panel, "PUBLIC.CHILDREN"));
			assertTrue(panel.statusPanel.detailsText().contains("unavailable for data copy"));
			assertTrue(panel.statusPanel.detailsText().contains("primary key"));
			panel.schemaOnly(true);
		});
		finish(ref.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(included(ref.get(), "PUBLIC.ROOTS"));
			assertTrue(included(ref.get(), "PUBLIC.PARENTS"));
		});
	}

	@Test
	void backgroundResultCannotReplaceASelectionChangedInFlight() throws Exception {
		var schema = fixture(false);
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var inspected = new CountDownLatch(1);
		var ref = new AtomicReference<JobDraftPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var builder = new BulkMigrationJobDraftBuilder() {
				@Override
				public java.util.List<String> includeReferencedTables(SchemaSnapshot snapshot,
						java.util.List<String> selected) throws java.io.IOException {
					assertFalse(SwingUtilities.isEventDispatchThread());
					entered.countDown();
					try {
						if (!release.await(5, TimeUnit.SECONDS))
							throw new java.io.IOException("Timed out");
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						throw new java.io.IOException(interrupted);
					}
					return super.includeReferencedTables(snapshot, selected);
				}
			};
			var panel = new JobDraftPanel(builder, yaml -> fail("Selection must not save"));
			ref.set(panel);
			panel.schemaFile.setText(schema.file().toString());
			panel.inspect.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					inspected.countDown();
			});
			panel.inspectSchema();
		});
		assertTrue(inspected.await(5, TimeUnit.SECONDS));
		var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			var panel = ref.get();
			selectChild(panel);
			panel.inspect.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			panel.includeReferenced.doClick(0);
		});
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> ref.get().model.setValueAt(false, row(ref.get(), "PUBLIC.CHILDREN"), 0));
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(included(ref.get(), "PUBLIC.CHILDREN"));
			assertFalse(included(ref.get(), "PUBLIC.PARENTS"));
			assertFalse(included(ref.get(), "PUBLIC.ROOTS"));
			assertTrue(ref.get().status.getText().contains("Inputs changed"));
		});
	}

	@Test
	void policySpecificAvailabilityIsNotReplacedByBaseSchemaEligibility() throws Exception {
		var base = fixture(false);
		var restricted = new BulkMigrationJobDraftBuilder.SchemaSnapshot(base.file(), base.fingerprint(),
				base.tables().stream()
						.map(table -> table.table().equals("PUBLIC.PARENTS")
								? new BulkMigrationJobDraftBuilder.TableChoice(table.table(), table.primaryKey(),
										"CSV policy restriction fixture")
								: table)
						.toList());
		var ref = new AtomicReference<JobDraftPanel>();
		SwingUtilities.invokeAndWait(() -> {
			var panel = new JobDraftPanel(restricted, directory, saved -> fail("Selection must not save"));
			ref.set(panel);
			selectChild(panel);
		});
		finish(ref.get());
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(included(ref.get(), "PUBLIC.CHILDREN"));
			assertFalse(included(ref.get(), "PUBLIC.ROOTS"));
			assertFalse(included(ref.get(), "PUBLIC.PARENTS"));
			assertTrue(ref.get().statusPanel.detailsText().contains("CSV policy restriction fixture"));
		});
	}

	private void finish(JobDraftPanel panel) throws Exception {
		var done = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			panel.includeReferenced.addPropertyChangeListener("enabled", event -> {
				if (Boolean.TRUE.equals(event.getNewValue()))
					done.countDown();
			});
			panel.includeReferenced.doClick(0);
			assertTrue(panel.isBusy());
			assertFalse(panel.create.isEnabled());
		});
		assertTrue(done.await(5, TimeUnit.SECONDS));
	}

	private static void selectChild(JobDraftPanel panel) {
		panel.filter.setText("CHILDREN");
		panel.select.doClick(0);
	}

	private static boolean included(JobDraftPanel panel, String name) {
		return Boolean.TRUE.equals(panel.model.getValueAt(row(panel, name), 0));
	}

	private static int row(JobDraftPanel panel, String name) {
		for (int row = 0; row < panel.model.getRowCount(); row++)
			if (name.equals(panel.model.getValueAt(row, 1)))
				return row;
		throw new AssertionError(name);
	}

	private BulkMigrationJobDraftBuilder.SchemaSnapshot fixture(boolean keylessParent) throws Exception {
		var schema = new Schema("PUBLIC");
		var root = table("ROOTS", false);
		var parent = table("PARENTS", keylessParent);
		var child = table("CHILDREN", false);
		parent.getConstraints().addForeignKeyConstraint("FK_ROOT", parent.getColumns().get("ID"),
				root.getColumns().get("ID"));
		child.getConstraints().addForeignKeyConstraint("FK_DIRECT_ROOT", child.getColumns().get("ID"),
				root.getColumns().get("ID"));
		child.getConstraints().addForeignKeyConstraint("FK_PARENT", child.getColumns().get("ID"),
				parent.getColumns().get("ID"));
		schema.getTables().add(child);
		schema.getTables().add(root);
		schema.getTables().add(parent);
		schema.getTables().add(table("OTHER", false));
		var file = directory.resolve("schema.xml");
		schema.writeXml(file.toFile());
		return new BulkMigrationJobDraftBuilder().inspect(file);
	}

	private static Table table(String name, boolean keyless) {
		var table = new Table(name);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		if (!keyless)
			table.setPrimaryKey("PK_" + name, table.getColumns().get("ID"));
		return table;
	}
}
