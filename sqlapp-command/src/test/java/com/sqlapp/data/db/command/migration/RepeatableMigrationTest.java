/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepeatableMigrationTest {
	@TempDir
	Path directory;
	private Path sql;
	private final JDBCDataSource dataSource = new JDBCDataSource();

	@BeforeEach
	void initialize() throws Exception {
		sql = Files.createDirectory(directory.resolve("sql"));
		dataSource.setUrl("jdbc:hsqldb:mem:repeatable_" + UUID.randomUUID());
		dataSource.setUser("SA");
		dataSource.setPassword("");
	}

	private <T extends MigrationCommand> T configure(final T command) {
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setSqlDirectory(sql.toFile());
		command.setRepeatableMigrations(true);
		return command;
	}

	private long count(final String query) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery(query)) {
			rows.next();
			return rows.getLong(1);
		}
	}

	@Test
	void runsAfterVersionedAndOnlyRerunsWhenContentChanges() throws Exception {
		Files.writeString(sql.resolve("1_create.sql"), "CREATE TABLE repeat_log(value INT);");
		final Path repeatable = sql.resolve("R__refresh_reference_data.sql");
		Files.writeString(repeatable, "INSERT INTO repeat_log VALUES(1);");
		final var first = configure(new MigrationCommand());
		first.setExecutionReportFile(directory.resolve("execution.json").toFile());
		first.run();
		assertEquals(1, count("SELECT COUNT(*) FROM repeat_log"));
		assertEquals(1, count("SELECT COUNT(*) FROM \"changelog_repeatable\""));
		assertEquals(java.util.List.of("refresh_reference_data"), first.getExecutionReport().selectedRepeatables());
		assertEquals(java.util.List.of("refresh_reference_data"), first.getExecutionReport().committedRepeatables());

		configure(new MigrationCommand()).run();
		assertEquals(1, count("SELECT COUNT(*) FROM repeat_log"));
		assertEquals(1, count("SELECT COUNT(*) FROM \"changelog_repeatable\""));

		Files.writeString(repeatable, "INSERT INTO repeat_log VALUES(2);");
		configure(new MigrationCommand()).run();
		assertEquals(2, count("SELECT COUNT(*) FROM repeat_log"));
		assertEquals(2, count("SELECT COUNT(*) FROM \"changelog_repeatable\""));
	}

	@Test
	void approvedPlanDetectsRepeatableContentChangeBeforeRepeatableHistoryIsCreated() throws Exception {
		final Path repeatable = sql.resolve("R__view.sql");
		Files.writeString(repeatable, "CREATE TABLE approved(value INT);");
		final Path planFile = directory.resolve("plan.json");
		final var plan = configure(new MigrationPlanCommand());
		plan.setOutputFile(planFile.toFile());
		plan.run();
		Files.writeString(repeatable, "CREATE TABLE changed(value INT);");
		final var migration = configure(new MigrationCommand());
		migration.setExpectedPlanFile(planFile.toFile());
		assertThrows(RuntimeException.class, migration::run);
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='changelog_repeatable'"));
	}

	@Test
	void planReportsRepeatableWithoutChangingDatabase() throws Exception {
		Files.writeString(sql.resolve("R__refresh.sql"), "CREATE TABLE planned(value INT);");
		final var command = configure(new MigrationPlanCommand());
		command.run();
		assertEquals(1, command.getPlan().pendingRepeatables().size());
		final var entry = command.getPlan().pendingRepeatables().get(0);
		assertEquals("refresh", entry.name());
		assertNull(entry.previousChecksum());
		assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='CHANGELOG_REPEATABLE'"));
	}

	@Test
	void capturesVersionedAndRepeatableStateWithoutChangingDatabase() throws Exception {
		Files.writeString(sql.resolve("1_create.sql"), "CREATE TABLE snapshot_test(value INT);");
		Files.writeString(sql.resolve("R__view.sql"), "INSERT INTO snapshot_test VALUES(1);");
		configure(new MigrationCommand()).run();
		final long historyRows = count("SELECT COUNT(*) FROM \"changelog\"");
		final long repeatableRows = count("SELECT COUNT(*) FROM \"changelog_repeatable\"");
		final var snapshot = configure(new MigrationEnvironmentSnapshotCommand());
		snapshot.setEnvironmentId("test");
		snapshot.setOutputFile(directory.resolve("test-environment.json").toFile());
		snapshot.run();
		assertEquals(List.of(1L), snapshot.getSnapshot().versioned().stream()
				.map(MigrationEnvironmentSnapshot.VersionedEntry::version).toList());
		assertEquals(List.of("view"), snapshot.getSnapshot().repeatables().stream()
				.map(MigrationEnvironmentSnapshot.RepeatableEntry::name).toList());
		assertEquals(historyRows, count("SELECT COUNT(*) FROM \"changelog\""));
		assertEquals(repeatableRows, count("SELECT COUNT(*) FROM \"changelog_repeatable\""));
	}
}
