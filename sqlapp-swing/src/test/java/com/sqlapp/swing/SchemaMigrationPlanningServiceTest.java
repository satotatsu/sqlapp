/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.ConsoleOutputLevel;
import com.sqlapp.data.db.command.migration.schema.MigrationCommand;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanIO;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Uses only a uniquely named in-memory HSQLDB database; no external
 * connections.
 */
class SchemaMigrationPlanningServiceTest {
	@TempDir
	Path directory;
	private Path up;
	private final String url = "jdbc:hsqldb:mem:swing_plan_" + UUID.randomUUID();
	private final SchemaMigrationPlanningService service = new SchemaMigrationPlanningService();

	@BeforeEach
	void initialize() throws Exception {
		up = Files.createDirectory(directory.resolve("up"));
	}

	@Test
	void buildsPlanWithoutExecutingUpSetupFinalizeSqlOrCreatingHistory() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE should_not_exist(id INT);");
		final Path setup = Files.createDirectory(directory.resolve("setup"));
		final Path finish = Files.createDirectory(directory.resolve("finalize"));
		Files.writeString(setup.resolve("setup.sql"), "DELETE FROM marker;");
		Files.writeString(finish.resolve("finalize.sql"), "DROP TABLE marker;");
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE marker(id INT)");
			statement.execute("INSERT INTO marker VALUES(7)");
		}
		final var request = new SchemaMigrationPlanningService.Request(up, null, setup, finish, null, null, true, true,
				true, false, false, false);
		final char[] password = new char[0];
		final var result = service.plan(request, url, "SA", password, "");
		final var plan = result.artifact().plan();
		assertFalse(plan.historyExists());
		assertEquals(1, plan.pending().size());
		assertEquals(1, plan.setupStatements());
		assertEquals(1, plan.finalizeStatements());
		assertTrue(plan.pending().getFirst().checksumWillBeRecorded());
		assertFalse(plan.hasBlockers());
		assertNotNull(plan.databaseIdentity());
		assertTrue(result.review().summary().contains(result.artifact().planFingerprint()));
		assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
		assertEquals(7, count("SELECT id FROM marker"));
	}

	@Test
	void readsExistingCustomHistoryAndSelectsOnlyPendingVersions() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE committed_marker(id INT);");
		try (var source = new HikariDataSource()) {
			source.setJdbcUrl(url);
			source.setUsername("SA");
			source.setPassword("");
			source.setMaximumPoolSize(1);
			final var apply = new MigrationCommand();
			apply.setDataSource(source);
			apply.setCloseDataSource(false);
			apply.setSqlDirectory(up.toFile());
			apply.setSchemaChangeLogTableName("custom_history");
			apply.setChecksumValidation(true);
			apply.setConsoleOutputLevel(ConsoleOutputLevel.ERROR);
			apply.run();
		}
		Files.writeString(up.resolve("2_add.sql"), "ALTER TABLE committed_marker ADD name VARCHAR(100);");
		final var request = new SchemaMigrationPlanningService.Request(up, null, null, null, null, "custom_history",
				true, true, true, false, false, false);
		final var plan = service.plan(request, url, "SA", new char[0], "").artifact().plan();
		assertTrue(plan.historyExists());
		assertEquals(1L, plan.currentVersion());
		assertEquals(2L, plan.pending().getFirst().version());
		assertEquals(1, plan.pending().size());
		assertEquals(1, count("SELECT COUNT(*) FROM \"custom_history\""));
		assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='COMMITTED_MARKER'"));
	}

	@Test
	void appliesUpperBoundRollbackRepeatableAndTransactionPolicies() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE planned(id INT);");
		Files.writeString(up.resolve("2_change_NO_TRAN.sql"), "ALTER TABLE planned ADD name VARCHAR(30);");
		Files.writeString(up.resolve("R__view.sql"), "CREATE VIEW planned_view AS SELECT * FROM planned;");
		final Path down = Files.createDirectory(directory.resolve("down"));
		Files.writeString(down.resolve("1_create.sql"), "DROP TABLE planned;");
		final var bounded = new SchemaMigrationPlanningService.Request(up, down, null, null, 1L, null, true, true, true,
				true, true, false);
		final var plan = service.plan(bounded, url, "SA", new char[0], "").artifact().plan();
		assertEquals(1L, plan.targetVersion());
		assertEquals(1, plan.pending().size());
		assertTrue(plan.pending().getFirst().rollbackAvailable());
		assertEquals(1, plan.pendingRepeatables().size());
		assertFalse(plan.hasBlockers());
		final var full = new SchemaMigrationPlanningService.Request(up, down, null, null, null, null, true, true, true,
				true, false, false);
		final var blocked = service.plan(full, url, "SA", new char[0], "");
		assertTrue(blocked.artifact().plan().hasBlockers());
		assertFalse(blocked.artifact().plan().pending().get(1).transactional());
		assertFalse(blocked.artifact().plan().pending().get(1).rollbackAvailable());
		assertTrue(blocked.review().summary().contains("KNOWN BLOCKERS PRESENT"));
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
	}

	@Test
	void savesExactlyTheDisplayedPlanAndRetainsFingerprint() throws Exception {
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE planned(id INT);");
		final var result = service.plan(defaultRequest(), url, "SA", new char[0], "");
		final Path destination = directory.resolve("reports/plan.json");
		service.save(result, destination);
		final var saved = new MigrationPlanIO().read(destination);
		assertEquals(result.artifact(), saved);
		assertEquals(result.artifact().planFingerprint(), saved.planFingerprint());
		assertEquals(result.artifact().createdAtEpochMillis(), saved.createdAtEpochMillis());
		assertFalse(Files.readString(destination).contains(url));
		assertThrows(IllegalArgumentException.class, () -> service.save(result, up.resolve("1_create.sql")));
		assertTrue(Files.readString(up.resolve("1_create.sql")).startsWith("CREATE TABLE"));
	}

	@Test
	void validatesDirectoriesAndUpperBoundBeforeConnecting() {
		assertThrows(IllegalArgumentException.class, () -> new SchemaMigrationPlanningService.Request(null, null, null,
				null, null, null, true, true, true, false, false, false));
		assertThrows(IllegalArgumentException.class, () -> new SchemaMigrationPlanningService.Request(up,
				directory.resolve("missing"), null, null, null, null, true, true, true, false, false, false));
		assertThrows(IllegalArgumentException.class, () -> new SchemaMigrationPlanningService.Request(up, null, null,
				null, -1L, null, true, true, true, false, false, false));
	}

	@Test
	void clearsPasswordAndOmitsItFromFailureMessages() {
		final char[] password = "secret_value".toCharArray();
		final var failure = assertThrows(IllegalArgumentException.class,
				() -> service.plan(defaultRequest(), "not-jdbc:secret_value", "SA", password, ""));
		assertArrayEquals(new char[password.length], password);
		assertFalse(failure.getMessage().contains("secret_value"));
		final char[] missingDriverPassword = "another_secret".toCharArray();
		assertThrows(IllegalArgumentException.class,
				() -> service.plan(defaultRequest(), url, "SA", missingDriverPassword, "missing.JdbcDriver"));
		assertArrayEquals(new char[missingDriverPassword.length], missingDriverPassword);
	}

	private SchemaMigrationPlanningService.Request defaultRequest() {
		return new SchemaMigrationPlanningService.Request(up, null, null, null, null, null, true, true, true, false,
				false, false);
	}

	private long count(final String sql) throws Exception {
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}
}
