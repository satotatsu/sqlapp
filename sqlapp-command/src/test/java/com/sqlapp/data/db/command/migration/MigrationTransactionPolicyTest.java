/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationTransactionPolicyTest {
	@TempDir
	Path directory;

	@Test
	void rejectsNonTransactionalFileBeforeSetupOrVersionSql() throws Exception {
		final Path up = Files.createDirectory(directory.resolve("up"));
		final Path setup = Files.createDirectory(directory.resolve("setup"));
		Files.writeString(up.resolve("1_change_NoTran.sql"), "INSERT INTO GUARD VALUES(2);");
		Files.writeString(setup.resolve("setup.sql"), "INSERT INTO GUARD VALUES(1);");
		final var dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:transaction_policy_" + UUID.randomUUID());
		dataSource.setUser("SA");
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE GUARD(ID INT)");
		}
		final var command = new MigrationCommand();
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setSqlDirectory(up.toFile());
		command.setSetupSqlDirectory(setup.toFile());
		command.setRejectNonTransactional(true);
		final RuntimeException failure = assertThrows(RuntimeException.class, command::run);
		assertTrue(failure.getMessage().contains("Non-transactional migrations"));
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM GUARD")) {
			assertTrue(rows.next());
			assertEquals(0, rows.getInt(1));
		}
	}
}
