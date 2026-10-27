/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;

/**
 * Embedded JDBC coverage of the creation-to-review handoff; never executes a
 * copy.
 */
class DataMigrationCreationIntegrationTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void generatedJobIsInspectedAndMissingTargetCannotEnableExecution(boolean targetExists) throws Exception {
		final String sourceUrl = "jdbc:hsqldb:mem:creation_source_" + UUID.randomUUID();
		final String targetUrl = "jdbc:hsqldb:mem:creation_target_" + UUID.randomUUID();
		try (var source = DriverManager.getConnection(sourceUrl, "SA", "");
				var target = DriverManager.getConnection(targetUrl, "SA", "")) {
			try (var statement = source.createStatement()) {
				statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
				statement.execute("INSERT INTO ITEMS VALUES(1, 'one'),(2, 'two')");
			}
			if (targetExists) {
				try (var statement = target.createStatement()) {
					statement.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
				}
			}
			final var finished = new CountDownLatch(2); // Discovery and automatic inspection.
			final var saved = new AtomicReference<BulkMigrationJobConfigurationEditorIO.Snapshot>();
			final var panel = new AtomicReference<DataMigrationPlanPanel>();
			final var planning = new DataMigrationPlanningService();
			SwingUtilities.invokeAndWait(() -> {
				final var view = new DataMigrationPlanPanel(planning::plan, planning::save,
						(result, from, fromPassword, to, toPassword, control) -> {
							throw new AssertionError("Creating and inspecting a job must never execute it");
						}, (result, path) -> fail("Creation must not save an execution report"),
						new DataMigrationDraftService()::discover, prepared -> {
							assertTrue(SwingUtilities.isEventDispatchThread());
							try {
								final var schema = prepared.schema();
								assertEquals(1, schema.tables().size());
								final String yaml = new BulkMigrationJobDraftBuilder().draft(schema,
										List.of(schema.tables().getFirst().table()), "", 10_000, true);
								final var snapshot = DataMigrationDraftService.save(prepared.directory(), yaml);
								saved.set(snapshot);
								return snapshot;
							} catch (Exception failure) {
								throw new IllegalStateException(failure);
							}
						});
				panel.set(view);
				view.source.jdbcUrl.setText(sourceUrl);
				view.source.username.setText("SA");
				view.target.jdbcUrl.setText(targetUrl);
				view.target.username.setText("SA");
				view.build.addPropertyChangeListener("enabled", event -> {
					if (Boolean.TRUE.equals(event.getNewValue()))
						finished.countDown();
				});
				view.createMigration(directory);
				assertTrue(view.isBusy());
				assertFalse(view.apply.isEnabled());
			});
			assertTrue(finished.await(15, TimeUnit.SECONDS), "Discovery and inspection did not finish");
			assertNotNull(saved.get(), "The table selection must produce a saved job");
			final var configuration = new BulkMigrationJobConfigurationEditorIO().validate(saved.get().text());
			assertNotNull(configuration.getSchemaFingerprint());
			assertFalse(saved.get().text().contains(sourceUrl));
			assertFalse(saved.get().text().contains(targetUrl));
			assertTrue(Files.isRegularFile(saved.get().file()));
			SwingUtilities.invokeAndWait(() -> {
				final var view = panel.get();
				assertFalse(view.isBusy());
				assertEquals(saved.get().file().toString(), view.configurationFile.getText());
				assertEquals(saved.get().fingerprint(), view.expectedFingerprint.getText());
				assertEquals(targetExists, view.save.isEnabled(), view.view.summary.getText());
				assertEquals(targetExists, view.apply.isEnabled(), view.view.summary.getText());
				if (targetExists) {
					assertEquals(1, view.view.tasks.getRowCount());
					assertTrue(view.status.getText().contains("No rows migrated"));
				} else {
					assertEquals(0, view.view.tasks.getRowCount());
					assertTrue(view.view.summary.getText().contains("ITEMS"), view.view.summary.getText());
				}
				assertEquals(0, view.source.password.getPassword().length);
				assertEquals(0, view.target.password.getPassword().length);
			});
			assertEquals(2, rowCount(source));
			if (targetExists)
				assertEquals(0, rowCount(target));
			else
				try (var tables = target.getMetaData().getTables(null, "PUBLIC", "ITEMS", new String[] { "TABLE" })) {
					assertFalse(tables.next(), "Inspection must not create the missing target table");
				}
		}
	}

	private static int rowCount(Connection connection) throws Exception {
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			rows.next();
			return rows.getInt(1);
		}
	}
}
