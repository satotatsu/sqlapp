/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.MigrationExecutionFailure.Phase;
import com.sqlapp.data.db.command.migration.MigrationExecutionFailure.RecoveryOutcome;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlConverter;

class MigrationExecutionFailureTest {
	@TempDir
	Path directory;
	private Path up;
	private final JDBCDataSource dataSource = new JDBCDataSource();

	@BeforeEach
	void initialize() throws Exception {
		up = Files.createDirectory(directory.resolve("up"));
		dataSource.setUrl("jdbc:hsqldb:mem:failure_" + UUID.randomUUID());
		dataSource.setUser("SA");
		dataSource.setPassword("");
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE sample(id INT PRIMARY KEY)");
			final var handler = new DbVersionHandler();
			handler.createTable(connection, DialectResolver.getInstance().getDialect(connection),
					handler.createVersionTableDefinition("changelog"));
		}
	}

	private <T extends MigrationCommand> T configure(final T command) {
		command.setSqlDirectory(up.toFile());
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		return command;
	}

	private Object scalar(final String query) throws Exception {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var result = statement.executeQuery(query)) {
			assertTrue(result.next());
			return result.getObject(1);
		}
	}

	private MigrationCommand checkedFailureCommand() {
		return configure(new MigrationCommand() {
			@Override
			protected void executeSql(final Connection connection, final Dialect dialect, final SqlConverter converter,
					final ParametersContext context, final SplitResult sql) throws SQLException {
				if (sql.getText().contains("999")) {
					throw new SQLException("injected statement failure", "42000", 123);
				}
				super.executeSql(connection, dialect, converter, context, sql);
			}
		});
	}

	@Test
	void checkedSqlFailureReportsPositionAndKeepsAnErrorMarkerAfterRollback() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1); INSERT INTO sample VALUES(999);");
		final var command = checkedFailureCommand();
		assertThrows(RuntimeException.class, command::run);
		final var failure = command.getExecutionFailure();
		assertEquals(Phase.MIGRATION, failure.phase());
		assertEquals(1L, failure.version());
		assertEquals(2, failure.attemptedStatement());
		assertEquals(1, failure.completedStatements());
		assertEquals("42000", failure.sqlState());
		assertEquals(123, failure.vendorErrorCode());
		assertFalse(failure.nonTransactional());
		assertEquals(RecoveryOutcome.RETURNED, failure.rollback());
		assertEquals(RecoveryOutcome.RETURNED, failure.historyRecovery());
		assertEquals(0L, ((Number) scalar("SELECT COUNT(*) FROM sample")).longValue());
		assertEquals("Errored", scalar("SELECT \"status\" FROM \"changelog\""));
		assertThrows(RuntimeException.class, configure(new MigrationCommand())::run);
	}

	@Test
	void nonTransactionalPartialChangesRemainVisibleAndRepairOnlyRemovesHistory() throws Exception {
		Files.writeString(up.resolve("1_change_NoTran.sql"),
				"INSERT INTO sample VALUES(1); INSERT INTO sample VALUES(999);");
		final var command = checkedFailureCommand();
		assertThrows(RuntimeException.class, command::run);
		assertTrue(command.getExecutionFailure().nonTransactional());
		assertEquals(1L, ((Number) scalar("SELECT COUNT(*) FROM sample")).longValue());
		assertEquals("Errored", scalar("SELECT \"status\" FROM \"changelog\""));
		configure(new MigrationRepairCommand()).run();
		assertEquals(0L, ((Number) scalar("SELECT COUNT(*) FROM \"changelog\"")).longValue());
		assertEquals(1L, ((Number) scalar("SELECT COUNT(*) FROM sample")).longValue());
	}

	@Test
	void earlierCommittedVersionsAreReportedSeparately() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1);");
		Files.writeString(up.resolve("2_change.sql"), "INSERT INTO sample VALUES(999);");
		final var command = checkedFailureCommand();
		assertThrows(RuntimeException.class, command::run);
		assertEquals(List.of(1L), command.getExecutionFailure().committedVersions());
		assertEquals(0, command.getExecutionFailure().completedStatements());
		assertEquals(1, command.getExecutionFailure().attemptedStatement());
		assertEquals(1L, ((Number) scalar("SELECT COUNT(*) FROM sample")).longValue());
	}

	@Test
	void finalizeFailureIsNotAttributedToTheLastVersion() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1);");
		final var finalizeDirectory = Files.createDirectory(directory.resolve("finalize"));
		Files.writeString(finalizeDirectory.resolve("finish.sql"), "INSERT INTO sample VALUES(999);");
		final var command = checkedFailureCommand();
		command.setFinalizeSqlDirectory(finalizeDirectory.toFile());
		assertThrows(RuntimeException.class, command::run);
		assertEquals(Phase.FINALIZE, command.getExecutionFailure().phase());
		assertNull(command.getExecutionFailure().version());
		assertEquals(finalizeDirectory.toFile().getAbsolutePath(), command.getExecutionFailure().source());
		assertEquals(List.of(1L), command.getExecutionFailure().committedVersions());
		assertEquals(RecoveryOutcome.NOT_ATTEMPTED, command.getExecutionFailure().historyRecovery());
	}

	@Test
	void rollbackFailureDoesNotReplaceTheOriginalCauseOrAttemptHistoryCommit() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(999);");
		final var command = checkedFailureCommand();
		command.setRollbackHandler(connection -> { throw new SQLException("rollback unavailable"); });
		final var thrown = assertThrows(RuntimeException.class, command::run);
		Throwable original = thrown;
		while (original.getCause() != null) {
			original = original.getCause();
		}
		assertEquals("injected statement failure", original.getMessage());
		assertTrue(original.getSuppressed().length > 0);
		assertEquals(RecoveryOutcome.FAILED, command.getExecutionFailure().rollback());
		assertEquals(RecoveryOutcome.NOT_ATTEMPTED, command.getExecutionFailure().historyRecovery());
	}

	@Test
	void historyRecoveryFailureIsAttachedToOriginalFailure() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1); INSERT INTO sample VALUES(1);");
		final var command = configure(new MigrationCommand() {
			@Override
			protected void errorVersion(final Connection connection, final Dialect dialect, final Table table,
					final Row row, final Long id, final DbVersionHandler handler) throws SQLException {
				throw new SQLException("history unavailable");
			}
		});
		final var thrown = assertThrows(RuntimeException.class, command::run);
		assertEquals(RecoveryOutcome.FAILED, command.getExecutionFailure().historyRecovery());
		assertTrue(thrown.getSuppressed().length > 0 || thrown.getCause().getSuppressed().length > 0);
	}

	@Test
	void downFailureDoesNotDeletePreviouslyAppliedHistory() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1);");
		configure(new MigrationCommand()).run();
		final var down = Files.createDirectory(directory.resolve("down"));
		Files.writeString(down.resolve("1_change.sql"), "INSERT INTO sample VALUES(1);");
		final var command = configure(new MigrationDownCommand());
		command.setDownSqlDirectory(down.toFile());
		command.setLastChangeToApply(0L);
		assertThrows(RuntimeException.class, command::run);
		assertEquals("Completed", scalar("SELECT \"status\" FROM \"changelog\""));
	}

	@Test
	void reusingCommandClearsPreviousFailure() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(999);");
		final var command = checkedFailureCommand();
		assertThrows(RuntimeException.class, command::run);
		assertNotNull(command.getExecutionFailure());
		configure(new MigrationRepairCommand()).run();
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1);");
		command.run();
		assertNull(command.getExecutionFailure());
	}

	@Test
	void failedCommitAcknowledgementDoesNotClaimRollbackOrOverwriteCompletedHistory() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1);");
		final var command = configure(new MigrationCommand());
		final var firstCommit = new AtomicBoolean(true);
		command.setCommitHandler(connection -> {
			connection.commit();
			if (firstCommit.getAndSet(false)) {
				throw new SQLException("commit acknowledgement lost", "08006");
			}
		});
		assertThrows(RuntimeException.class, command::run);
		assertEquals(Phase.VERSION_COMMIT, command.getExecutionFailure().phase());
		assertTrue(command.getExecutionFailure().committedVersions().isEmpty());
		assertEquals("08006", command.getExecutionFailure().sqlState());
		assertEquals("Completed", scalar("SELECT \"status\" FROM \"changelog\""));
		assertEquals(1L, ((Number) scalar("SELECT COUNT(*) FROM sample")).longValue());
	}

	@Test
	void setupFailureHasNoVersionAndDoesNotCreateAnErrorHistoryEntry() throws Exception {
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO sample VALUES(1);");
		final var setup = Files.createDirectory(directory.resolve("setup"));
		Files.writeString(setup.resolve("start.sql"), "INSERT INTO sample VALUES(999);");
		final var command = checkedFailureCommand();
		command.setSetupSqlDirectory(setup.toFile());
		assertThrows(RuntimeException.class, command::run);
		assertEquals(Phase.SETUP, command.getExecutionFailure().phase());
		assertNull(command.getExecutionFailure().version());
		assertEquals(1, command.getExecutionFailure().attemptedStatement());
		assertEquals(0L, ((Number) scalar("SELECT COUNT(*) FROM \"changelog\"")).longValue());
	}
}
