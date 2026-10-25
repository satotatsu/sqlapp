/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaDraftHandoffTest {
	@TempDir
	Path directory;
	private final SchemaComparisonService service = new SchemaComparisonService();

	private SchemaComparisonService.Result snapshot() throws Exception {
		final Path before = Files.writeString(directory.resolve("before.xml"), "before");
		final Path after = Files.writeString(directory.resolve("after.xml"), "after");
		return new SchemaComparisonService.Result(before, after, "review", "CREATE TABLE CUSTOMERS(ID INT);\n", 1);
	}

	@Test
	void usesSharedVersionReaderAndRejectsChangedMissingUnrecognizedOrDuplicateFiles() throws Exception {
		final var snapshot = snapshot();
		final Path up = Files.createDirectory(directory.resolve("up"));
		final Path file = up.resolve("1_create_customers.sql");
		service.save(snapshot, file, true);
		assertEquals(up, service.migrationDirectory(snapshot, file));
		assertTrue(SchemaComparisonService.defaultSqlDraftName().matches("[0-9]{17}_schema_migration_draft\\.sql"));
		Files.writeString(file, "changed");
		assertThrows(IllegalArgumentException.class, () -> service.migrationDirectory(snapshot, file));
		service.save(snapshot, file, true);
		Files.copy(file, up.resolve("1_duplicate.sql"));
		assertThrows(RuntimeException.class, () -> service.migrationDirectory(snapshot, file));
		Files.delete(up.resolve("1_duplicate.sql"));
		final Path plain = up.resolve("draft.sql");
		service.save(snapshot, plain, true);
		final var invalid = assertThrows(IllegalArgumentException.class,
				() -> service.migrationDirectory(snapshot, plain));
		assertTrue(invalid.getMessage().contains("<numeric version>_<description>.sql"));
		final Path overflowing = up.resolve("99999999999999999999_overflow.sql");
		service.save(snapshot, overflowing, true);
		assertTrue(assertThrows(IllegalArgumentException.class, () -> service.migrationDirectory(snapshot, file))
				.getMessage().contains("exceeds"));
		Files.delete(overflowing);
		Files.delete(file);
		assertThrows(IllegalArgumentException.class, () -> service.migrationDirectory(snapshot, file));
	}

	@Test
	void savedDraftAppearsInReadOnlyConnectedPlanWithoutCreatingItsTable() throws Exception {
		final var snapshot = snapshot();
		final Path up = Files.createDirectory(directory.resolve("up"));
		final Path file = up.resolve("1_create_customers.sql");
		service.save(snapshot, file, true);
		final var request = new SchemaMigrationPlanningService.Request(service.migrationDirectory(snapshot, file), null,
				null, null, null, "changelog", true, true, true, false, false, false);
		final String url = "jdbc:hsqldb:mem:handoff_" + java.util.UUID.randomUUID();
		final var planned = new SchemaMigrationPlanningService().plan(request, url, "SA", new char[0], "");
		assertEquals(1, planned.artifact().plan().pending().size());
		assertEquals(1L, planned.artifact().plan().pending().getFirst().version());
		try (var connection = java.sql.DriverManager.getConnection(url, "SA", "");
				var tables = connection.getMetaData().getTables(null, "PUBLIC", "CUSTOMERS", null)) {
			assertFalse(tables.next());
		}
	}

	@Test
	void handoffRequiresSavedSnapshotAndSupportsBusyRetryWithoutConnecting() throws Exception {
		final var snapshot = snapshot();
		final Path up = Files.createDirectory(directory.resolve("up"));
		final Path saved = up.resolve("1_create_customers.sql");
		final var panel = new AtomicReference<SchemaComparisonPanel>();
		final var planner = new AtomicReference<SchemaMigrationPlanPanel>();
		final var accept = new AtomicBoolean(false);
		final var callbacks = new AtomicInteger();
		final var compared = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var plan = new SchemaMigrationPlanPanel((request, url, user, password, driver) -> {
				fail("Handoff must not connect or build a plan");
				return null;
			}, (result, path) -> {
			});
			planner.set(plan);
			plan.downDirectory.setText("existing-down");
			plan.targetVersion.setText("15");
			final var view = new SchemaComparisonPanel((before, after) -> snapshot, service::save);
			panel.set(view);
			view.setDirectoryReceiver(path -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				callbacks.incrementAndGet();
				return accept.get() && plan.selectUpDirectory(path);
			});
			assertFalse(view.useDirectory.isEnabled());
			view.original.setText(snapshot.original().toString());
			view.target.setText(snapshot.target().toString());
			onEnabled(view.compare, compared);
			view.compare.doClick();
		});
		assertTrue(compared.await(5, TimeUnit.SECONDS));
		final var savedSignal = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(panel.get().useDirectory.isEnabled());
			onEnabled(panel.get().compare, savedSignal);
			panel.get().saveTo(saved, true);
		});
		assertTrue(savedSignal.await(5, TimeUnit.SECONDS));
		final var rejected = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().useDirectory.isEnabled());
			onEnabled(panel.get().compare, rejected);
			panel.get().useDirectory.doClick();
			assertTrue(panel.get().isBusy());
		});
		assertTrue(rejected.await(5, TimeUnit.SECONDS));
		final var accepted = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.get().status.getText().contains("busy"));
			assertTrue(panel.get().useDirectory.isEnabled());
			accept.set(true);
			onEnabled(panel.get().compare, accepted);
			panel.get().useDirectory.doClick();
		});
		assertTrue(accepted.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(up.toString(), planner.get().upDirectory.getText());
			assertEquals("existing-down", planner.get().downDirectory.getText());
			assertEquals("15", planner.get().targetVersion.getText());
			assertFalse(planner.get().apply.isEnabled());
			planner.get().upDirectory.setEnabled(false);
			assertFalse(planner.get().selectUpDirectory(directory));
			assertEquals(up.toString(), planner.get().upDirectory.getText());
			panel.get().target.setText("changed.xml");
			assertFalse(panel.get().useDirectory.isEnabled());
		});
		assertEquals(2, callbacks.get());
	}

	@Test
	void opensSavedComparisonInSqlEditorAndClearsEditActionWhenInputsChange() throws Exception {
		final var snapshot = snapshot();
		final Path file = directory.resolve("1_customers.sql");
		final var comparison = new AtomicReference<SchemaComparisonPanel>();
		final var editor = new AtomicReference<SqlEditorPanel>();
		final var compared = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			final var sql = new SqlEditorPanel(path -> true);
			editor.set(sql);
			final var view = new SchemaComparisonPanel((before, after) -> snapshot, service::save);
			comparison.set(view);
			view.setSqlEditor(sql::requestOpen);
			view.original.setText(snapshot.original().toString());
			view.target.setText(snapshot.target().toString());
			onEnabled(view.compare, compared);
			view.compare.doClick();
		});
		assertTrue(compared.await(5, TimeUnit.SECONDS));
		final var saved = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			onEnabled(comparison.get().compare, saved);
			comparison.get().saveTo(file, true);
		});
		assertTrue(saved.await(5, TimeUnit.SECONDS));
		final var opened = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(comparison.get().editSql.isEnabled());
			onEnabled(editor.get().open, opened);
			comparison.get().editSql.doClick();
		});
		assertTrue(opened.await(5, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			assertEquals(snapshot.sqlDraft(), editor.get().text.getText());
			assertFalse(editor.get().isDirty());
			comparison.get().target.setText("changed.xml");
			assertFalse(comparison.get().editSql.isEnabled());
		});
	}

	private static void onEnabled(final javax.swing.JButton button, final CountDownLatch latch) {
		button.addPropertyChangeListener("enabled", event -> {
			if (Boolean.TRUE.equals(event.getNewValue()))
				latch.countDown();
		});
	}
}
