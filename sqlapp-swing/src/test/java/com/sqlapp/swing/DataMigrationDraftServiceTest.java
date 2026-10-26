/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;

class DataMigrationDraftServiceTest {
	@TempDir
	Path directory;

	@Test
	void sourceDiscoveryAndTableSelectionCreatePinnedJobAndLivePlanWithoutYamlEditing() throws Exception {
		final String source = "jdbc:hsqldb:mem:wizard_source_" + UUID.randomUUID();
		final String target = "jdbc:hsqldb:mem:wizard_target_" + UUID.randomUUID();
		try (var from = DriverManager.getConnection(source, "SA", "");
				var to = DriverManager.getConnection(target, "SA", "")) {
			try (var statement = from.createStatement()) {
				statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
				statement.execute("INSERT INTO ITEMS VALUES(1, 'one'),(2, 'two')");
			}
			try (var statement = to.createStatement()) {
				statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
			}
			final var prepared = new DataMigrationDraftService().discover(new PlanningJdbc.Spec(source, "SA", ""),
					new char[0], "", directory);
			assertEquals(1, prepared.schema().tables().size());
			assertTrue(prepared.schema().tables().getFirst().selectable(), Files.readString(prepared.schema().file()));
			assertTrue(Files.isRegularFile(prepared.schema().file()));
			final var saved = new AtomicReference<BulkMigrationJobConfigurationEditorIO.Snapshot>();
			final var finished = new CountDownLatch(1);
			SwingUtilities.invokeAndWait(() -> {
				final var panel = new JobDraftPanel(prepared.schema(), prepared.directory(), value -> {
					saved.set(value);
					finished.countDown();
				});
				assertFalse(panel.schemaFile.isEnabled());
				assertFalse(panel.schemaFile.getParent().isVisible());
				assertFalse(panel.jobId.getParent().isVisible());
				assertTrue(panel.verify.isSelected());
				panel.select.doClick(0);
				panel.create.doClick(0);
				assertFalse(panel.cancel.isEnabled());
			});
			assertTrue(finished.await(5, TimeUnit.SECONDS));
			final var configuration = new BulkMigrationJobConfigurationEditorIO().validate(saved.get().text());
			assertEquals(prepared.schema().fingerprint(), configuration.getSchemaFingerprint());
			final byte[] original = Files.readAllBytes(saved.get().file());
			assertThrows(Exception.class,
					() -> DataMigrationDraftService.save(prepared.directory(), saved.get().text()));
			assertArrayEquals(original, Files.readAllBytes(saved.get().file()));
			assertFalse(saved.get().text().contains(source));
			assertFalse(saved.get().text().contains(target));
			final var plan = new DataMigrationPlanningService().plan(
					new DataMigrationPlanningService.Request(saved.get().file(), saved.get().fingerprint()),
					new PlanningJdbc.Spec(source, "SA", ""), new char[0], new PlanningJdbc.Spec(target, "SA", ""),
					new char[0]);
			assertEquals(1, plan.report().tasks().size());
			assertTrue(plan.report().compatible());
			try (var statement = from.createStatement();
					var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				rows.next();
				assertEquals(2, rows.getInt(1));
			}
			try (var statement = to.createStatement();
					var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				rows.next();
				assertEquals(0, rows.getInt(1));
			}
		}
	}

	@Test
	void discoveryRejectsMissingSchemaAndBadWorkspaceAndClearsPasswords() throws Exception {
		final String url = "jdbc:hsqldb:mem:wizard_errors_" + UUID.randomUUID();
		try (var ignored = DriverManager.getConnection(url, "SA", "")) {
			final var service = new DataMigrationDraftService();
			final var failure = assertThrows(IllegalArgumentException.class, () -> service
					.discover(new PlanningJdbc.Spec(url, "SA", ""), new char[0], "NOT_PRESENT", directory));
			assertTrue(failure.getMessage().contains("Source schema"));
			final char[] secret = "never-connect".toCharArray();
			assertThrows(IllegalArgumentException.class, () -> service.discover(new PlanningJdbc.Spec(url, "SA", ""),
					secret, "", directory.resolve("absent")));
			assertArrayEquals(new char[secret.length], secret);
			try (var children = Files.list(directory)) {
				assertEquals(0, children.count());
			}
		}
	}
}
