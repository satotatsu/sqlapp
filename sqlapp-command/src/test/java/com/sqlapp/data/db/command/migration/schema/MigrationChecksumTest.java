/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.schema.MigrationValidationResult.State;

class MigrationChecksumTest {
	@TempDir
	Path sqlDirectory;

	private final JDBCDataSource dataSource = dataSource();

	private static JDBCDataSource dataSource() {
		final var result = new JDBCDataSource();
		result.setUrl("jdbc:hsqldb:mem:checksum_" + UUID.randomUUID());
		result.setUser("SA");
		result.setPassword("");
		return result;
	}

	private <T extends MigrationCommand> T configure(final T command) {
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setSqlDirectory(sqlDirectory.toFile());
		return command;
	}

	private MigrationCommand migrate(final boolean enabled) {
		final var command = configure(new MigrationCommand());
		command.setChecksumValidation(enabled);
		command.run();
		return command;
	}

	private MigrationValidateCommand validate() {
		final var command = configure(new MigrationValidateCommand());
		command.run();
		return command;
	}

	private void sql(final String name, final String text) throws Exception {
		Files.writeString(sqlDirectory.resolve(name), text);
	}

	private Object scalar(final String sql) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getObject(1);
		}
	}

	private String checksum(final long version) throws Exception {
		return (String) scalar("SELECT \"checksum\" FROM \"changelog\" WHERE \"change_number\"=" + version);
	}

	@Test
	void defaultsRemainCasualAndValidationDoesNotUpgradeHistory() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		assertFalse(new MigrationCommand().isChecksumValidation());
		migrate(false);
		sql("1_create.sql", "CREATE TABLE sample(id BIGINT);");
		assertNull(migrate(false).getValidationResult());
		assertEquals(State.UNVERIFIED, validate().getValidationResult().entries().get(0).state());
		assertEquals(0L, ((Number) scalar("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
				+ "WHERE TABLE_NAME='changelog' AND COLUMN_NAME='checksum'")).longValue());
	}

	@Test
	void recordsAndVerifiesAndStopsBeforeApplyingPendingSqlOnMismatch() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		migrate(true);
		assertTrue(checksum(1).matches("sha256:[a-f0-9]{64}"));
		assertEquals(State.VERIFIED, validate().getValidationResult().entries().get(0).state());
		sql("1_create.sql", "CREATE TABLE sample(id BIGINT);");
		sql("2_insert.sql", "INSERT INTO sample VALUES(1);");
		final var command = configure(new MigrationCommand());
		command.setChecksumValidation(true);
		assertThrows(RuntimeException.class, command::run);
		assertEquals(State.CHANGED, command.getValidationResult().entries().get(0).state());
		assertEquals(0L, ((Number) scalar("SELECT COUNT(*) FROM sample")).longValue());
	}

	@Test
	void existingRowsStayUnverifiedAfterOptIn() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		migrate(false);
		sql("2_insert.sql", "INSERT INTO sample VALUES(1);");
		migrate(true);
		assertNull(checksum(1));
		assertNotNull(checksum(2));
		final var entries = validate().getValidationResult().entries();
		assertEquals(State.UNVERIFIED, entries.get(0).state());
		assertEquals(State.VERIFIED, entries.get(1).state());
	}

	@Test
	void disablingPreservesChecksumsAndNewRowsRemainUnverified() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		migrate(true);
		final String original = checksum(1);
		sql("2_insert.sql", "INSERT INTO sample VALUES(1);");
		migrate(false);
		assertEquals(original, checksum(1));
		assertNull(checksum(2));
		assertEquals(State.UNVERIFIED, validate().getValidationResult().entries().get(1).state());
	}

	@Test
	void standaloneValidationDetectsMissingFileAndDoesNotRewriteHistory() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		migrate(true);
		final String original = checksum(1);
		Files.delete(sqlDirectory.resolve("1_create.sql"));
		final var command = configure(new MigrationValidateCommand());
		assertThrows(RuntimeException.class, command::run);
		assertEquals(State.MISSING, command.getValidationResult().entries().get(0).state());
		assertEquals(original, checksum(1));
	}

	@Test
	void validationOnFreshDatabaseDoesNotCreateHistoryOrRunHooks() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		final var command = configure(new MigrationValidateCommand());
		command.setChecksumValidation(true);
		command.setSetupSqlDirectory(sqlDirectory.toFile());
		command.setFinalizeSqlDirectory(sqlDirectory.toFile());
		command.run();
		assertTrue(command.getValidationResult().entries().isEmpty());
		assertEquals(0L, ((Number) scalar("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
				+ "WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_TYPE='BASE TABLE'")).longValue());
	}

	@Test
	void historyOnlyInsertDoesNotClaimSqlWasVerified() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		final var command = configure(new MigrationInsertCommand());
		command.setChecksumValidation(true);
		command.run();
		assertNull(checksum(1));
		assertEquals(State.UNVERIFIED, validate().getValidationResult().entries().get(0).state());
	}

	@Test
	void checksumUsesSameSourceSnapshotAsParsedSql() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		final var handler = new DbVersionFileHandler();
		handler.setUpSqlDirectory(sqlDirectory.toFile());
		final var file = handler.read().get(0);
		file.getUpSqls();
		final String checksum = file.getUpSqlChecksum();
		sql("1_create.sql", "CREATE TABLE sample(id BIGINT);");
		assertEquals(checksum, file.getUpSqlChecksum());
		assertNotEquals(checksum, handler.read().get(0).getUpSqlChecksum());
	}

	@Test
	void validationRequiresAnExistingSqlDirectory() {
		final var command = configure(new MigrationValidateCommand());
		command.setSqlDirectory(sqlDirectory.resolve("missing").toFile());
		assertThrows(IllegalArgumentException.class, command::run);
	}

	@Test
	void customHistoryWithoutSeriesNumberAndReadOnlyAccount() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		final var migration = configure(new MigrationCommand());
		migration.setSchemaChangeLogTableName("PUBLIC.custom_history");
		migration.setIdColumnName("version_id");
		migration.setWithSeriesNumber(false);
		migration.setChecksumValidation(true);
		migration.run();
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE USER reader PASSWORD 'test'");
			statement.execute("GRANT SELECT ON PUBLIC.\"custom_history\" TO reader");
		}
		final var reader = new JDBCDataSource();
		reader.setUrl(dataSource.getUrl());
		reader.setUser("READER");
		reader.setPassword("test");
		final var validation = configure(new MigrationValidateCommand());
		validation.setDataSource(reader);
		validation.setSchemaChangeLogTableName("PUBLIC.custom_history");
		validation.setIdColumnName("version_id");
		validation.setWithSeriesNumber(false);
		validation.run();
		assertEquals(State.VERIFIED, validation.getValidationResult().entries().get(0).state());
	}

	@Test
	void downRemovesTheChecksumWithItsHistoryAndUpCanReapply() throws Exception {
		sql("1_create.sql", "CREATE TABLE sample(id INT);");
		migrate(true);
		final var downDirectory = Files.createDirectory(sqlDirectory.resolve("down"));
		Files.writeString(downDirectory.resolve("1_create.sql"), "DROP TABLE sample;");
		final var down = configure(new MigrationDownCommand());
		down.setChecksumValidation(true);
		down.setDownSqlDirectory(downDirectory.toFile());
		down.setLastChangeToApply(0L);
		down.run();
		assertTrue(validate().getValidationResult().entries().isEmpty());
		migrate(true);
		assertEquals(State.VERIFIED, validate().getValidationResult().entries().get(0).state());
	}
}
