/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReportIO;

/** All SQL execution targets isolated in-memory HSQLDB databases. */
class SchemaMigrationExecutionServiceTest {
	@TempDir
	Path directory;
	private Path up;
	private final String url = "jdbc:hsqldb:mem:gui_execution_" + UUID.randomUUID();
	private final SchemaMigrationPlanningService planning = new SchemaMigrationPlanningService();
	private final SchemaMigrationExecutionService service = new SchemaMigrationExecutionService();

	@BeforeEach
	void initialize() throws Exception {
		up = Files.createDirectory(directory.resolve("up"));
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE reviewed(id INT);");
	}

	@Test
	void appliesReviewedSqlAndRetainsAuditFingerprintWithoutAllowingStaleReexecution() throws Exception {
		final var request = request(null, false, false);
		final var reviewed = review(request);
		final var outcome = service.execute(request, spec(url), reviewed, new char[0]);
		assertTrue(outcome.successful(), outcome.review().summary());
		assertEquals(List.of(1L), outcome.report().committedVersions());
		assertEquals(reviewed.artifact().planFingerprint(), outcome.report().planFingerprint());
		assertEquals(1, count(url, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='REVIEWED'"));
		final Path output = directory.resolve("execution.json");
		service.save(outcome, output);
		assertEquals(outcome.report(), new MigrationExecutionReportIO().read(output));
		assertFalse(Files.readString(output).contains(url));
		final var duplicate = service.execute(request, spec(url), reviewed, new char[0]);
		assertFalse(duplicate.successful());
		assertNull(duplicate.report());
		assertTrue(duplicate.failureMessage().contains("differ"));
		assertEquals(1, count(url, "SELECT COUNT(*) FROM \"changelog\""));
	}

	@Test
	void changedSqlWithSameStatementCountIsRejectedBeforeHistoryCreation() throws Exception {
		final var request = request(null, false, false);
		final var reviewed = review(request);
		Files.writeString(up.resolve("1_create.sql"), "CREATE TABLE changed(id INT);");
		final var outcome = service.execute(request, spec(url), reviewed, new char[0]);
		assertFalse(outcome.successful());
		assertNull(outcome.report());
		assertTrue(outcome.failureMessage().contains("differ"));
		assertEquals(0, count(url, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
	}

	@Test
	void wrongDatabaseIsRejectedBeforeCreatingHistoryOrApplyingSql() throws Exception {
		final var request = request(null, false, false);
		final var reviewed = review(request);
		final String other = "jdbc:hsqldb:mem:wrong_target_" + UUID.randomUUID();
		final var outcome = service.execute(request, spec(other), reviewed, new char[0]);
		assertFalse(outcome.successful());
		assertNull(outcome.report());
		assertEquals(0, count(other, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
		assertEquals(0, count(url, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
	}

	@Test
	void returnsPartialFailureEvidenceAndSavesItWithoutClaimingRollbackUndidSql() throws Exception {
		Files.writeString(up.resolve("2_fail.sql"), "INSERT INTO missing_table VALUES(1);");
		final var request = request(null, false, false);
		final var outcome = service.execute(request, spec(url), review(request), new char[0]);
		assertFalse(outcome.successful());
		assertNotNull(outcome.report());
		assertFalse(outcome.report().successful());
		assertEquals(List.of(1L), outcome.report().committedVersions());
		assertEquals(2L, outcome.report().failure().version());
		assertTrue(outcome.review().summary().contains("does not prove"));
		assertEquals("COMMITTED", outcome.review().rows().get(0).get(1));
		assertEquals("NOT RECORDED AS COMMITTED", outcome.review().rows().get(1).get(1));
		final Path output = directory.resolve("failure.json");
		service.save(outcome, output);
		assertEquals(outcome.report(), new MigrationExecutionReportIO().read(output));
	}

	@Test
	void rejectsBlockersSetupFinalizeAndEmptyPlansAndClearsPassword() throws Exception {
		final var strict = request(null, true, false);
		final var blocked = review(strict);
		final char[] password = "private_password".toCharArray();
		final var rejected = service.execute(strict, spec(url), blocked, password);
		assertFalse(rejected.successful());
		assertTrue(rejected.failureMessage().contains("blockers"));
		assertArrayEquals(new char[password.length], password);
		final Path setup = Files.createDirectory(directory.resolve("setup"));
		Files.writeString(setup.resolve("setup.sql"), "CREATE TABLE setup_marker(id INT);");
		final var setupRequest = request(setup, false, false);
		assertTrue(service.execute(setupRequest, spec(url), review(setupRequest), new char[0]).failureMessage()
				.contains("setup/finalize"));
		final var finalizeRequest = new SchemaMigrationPlanningService.Request(up, null, null, setup, null, null, true,
				true, true, false, false, false);
		assertTrue(service.execute(finalizeRequest, spec(url), review(finalizeRequest), new char[0]).failureMessage()
				.contains("setup/finalize"));
		Files.delete(up.resolve("1_create.sql"));
		final var empty = request(null, false, false);
		assertTrue(
				service.execute(empty, spec(url), review(empty), new char[0]).failureMessage().contains("No pending"));
		assertEquals(0, count(url, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
	}

	@Test
	void executesRepeatableSqlThroughTheSharedCommand() throws Exception {
		Files.writeString(up.resolve("R__seed.sql"), "INSERT INTO reviewed VALUES(10);");
		final var request = request(null, false, true);
		final var outcome = service.execute(request, spec(url), review(request), new char[0]);
		assertTrue(outcome.successful(), outcome.review().summary());
		assertEquals(List.of("seed"), outcome.report().committedRepeatables());
		assertEquals(10, count(url, "SELECT id FROM reviewed"));
	}

	@Test
	void changedRepeatableSqlIsRejectedAndSavingWithoutReportFailsClearly() throws Exception {
		final Path repeatable = up.resolve("R__seed.sql");
		Files.writeString(repeatable, "INSERT INTO reviewed VALUES(10);");
		final var request = request(null, false, true);
		final var reviewed = review(request);
		Files.writeString(repeatable, "INSERT INTO reviewed VALUES(20);");
		final var outcome = service.execute(request, spec(url), reviewed, new char[0]);
		assertFalse(outcome.successful());
		assertNull(outcome.report());
		assertThrows(IllegalArgumentException.class, () -> service.save(outcome, directory.resolve("missing.json")));
		assertEquals(0, count(url, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
	}

	private SchemaMigrationPlanningService.Request request(final Path setup, final boolean rollback,
			final boolean repeatables) {
		return new SchemaMigrationPlanningService.Request(up, null, setup, null, null, null, true, true, true, rollback,
				repeatables, false);
	}

	private SchemaMigrationPlanningService.Result review(final SchemaMigrationPlanningService.Request request) {
		return planning.plan(request, url, "SA", new char[0], "");
	}

	private static PlanningJdbc.Spec spec(final String url) {
		return new PlanningJdbc.Spec(url, "SA", "");
	}

	private static long count(final String url, final String sql) throws Exception {
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}
}
