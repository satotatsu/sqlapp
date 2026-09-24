/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbConcurrencyException;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class MigrationConcurrencyTest {
	@TempDir
	Path directory;
	private Path up;
	private Path down;
	private final JDBCDataSource dataSource = new JDBCDataSource();

	@BeforeEach
	void initialize() throws Exception {
		dataSource.setUrl("jdbc:hsqldb:mem:concurrency_" + UUID.randomUUID());
		dataSource.setUser("SA");
		dataSource.setPassword("");
		up = Files.createDirectory(directory.resolve("up"));
		down = Files.createDirectory(directory.resolve("down"));
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE audit_log(direction VARCHAR(10))");
			new DbVersionHandler().createTable(connection, DialectResolver.getInstance().getDialect(connection),
					new DbVersionHandler().createVersionTableDefinition("changelog"));
		}
		Files.writeString(up.resolve("1_change.sql"), "INSERT INTO audit_log VALUES('up');");
		Files.writeString(down.resolve("1_change.sql"), "INSERT INTO audit_log VALUES('down');");
	}

	private <T extends MigrationCommand> T configure(final T command) {
		command.setDataSource(dataSource);
		command.setCloseDataSource(false);
		command.setSqlDirectory(up.toFile());
		command.setDownSqlDirectory(down.toFile());
		return command;
	}

	private long count(final String query) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery(query)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}

	@Test
	void staleUpFailsExplicitlyWhenAnotherConnectionAppliesTheVersionBeforeLockAcquisition() throws Exception {
		final var competitor = configure(new MigrationCommand());
		final var command = configure(new MigrationCommand() {
			@Override
			protected void executeChangeVersion(final Connection connection, final Dialect dialect, final Table table,
					final List<Row> rows, final List<SqlFile> files, final DbVersionHandler handler) throws SQLException {
				super.executeChangeVersion(beforeLock(connection, competitor::run), dialect, table, rows, files, handler);
			}
		});
		final var failure = assertThrows(DbConcurrencyException.class, command::run);
		assertTrue(failure.getMessage().contains("version 1"));
		assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE direction='up'"));
		assertEquals(1, count("SELECT COUNT(*) FROM \"changelog\" WHERE \"status\"='Completed'"));
	}

	@Test
	void staleDownCannotExecuteTwiceAfterAnotherConnectionRemovesTheHistory() throws Exception {
		configure(new MigrationCommand()).run();
		final var competitor = configure(new MigrationDownCommand());
		competitor.setLastChangeToApply(0L);
		final var command = configure(new MigrationDownCommand() {
			@Override
			protected void executeChangeVersion(final Connection connection, final Dialect dialect, final Table table,
					final List<Row> rows, final List<SqlFile> files, final DbVersionHandler handler) throws SQLException {
				super.executeChangeVersion(beforeLock(connection, competitor::run), dialect, table, rows, files, handler);
			}
		});
		command.setLastChangeToApply(0L);
		assertThrows(DbConcurrencyException.class, command::run);
		assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE direction='down'"));
		assertEquals(0, count("SELECT COUNT(*) FROM \"changelog\""));
	}

	@Test
	void rejectedHistoryClaimDoesNotReturnSuccessOrRunSql() throws Exception {
		final var command = configure(new MigrationCommand() {
			@Override
			protected boolean startVersion(final Connection connection, final Dialect dialect, final Table table,
					final Row row, final Long seriesNumber, final DbVersionHandler handler) {
				return false;
			}
		});
		assertThrows(DbConcurrencyException.class, command::run);
		assertEquals(0, count("SELECT COUNT(*) FROM audit_log"));
		assertEquals(0, count("SELECT COUNT(*) FROM \"changelog\""));
	}

	@Test
	void historyLookupPreservesTransactionAndIsolation() throws Exception {
		try (var connection = dataSource.getConnection()) {
			connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO audit_log VALUES('pending')");
			}
			final var handler = new DbVersionHandler();
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			assertFalse(handler.exists(dialect, connection, handler.createVersionTableDefinition("changelog"), 1L));
			assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
			connection.rollback();
		}
		assertEquals(0, count("SELECT COUNT(*) FROM audit_log"));
	}

	@Test
	void configuredLockTimeoutIsAppliedAndReportedAsConcurrencyFailure() throws Exception {
		final var timeout = new AtomicInteger();
		final var command = configure(new MigrationCommand());
		command.setLockTimeoutSeconds(7);
		try (var connection = dataSource.getConnection()) {
			final var dialect = DialectResolver.getInstance().getDialect(connection);
			final var table = new DbVersionHandler().createVersionTableDefinition("changelog");
			final var operations = dialect.createSqlFactoryRegistry().createSql(table, SqlType.LOCK);
			final Connection timingOut = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						final Object value = invoke(connection, method, args);
						if (!method.getName().equals("createStatement")) {
							return value;
						}
						return Proxy.newProxyInstance(Statement.class.getClassLoader(),
								new Class<?>[] { Statement.class }, (statementProxy, statementMethod, statementArgs) -> {
									if (statementMethod.getName().equals("setQueryTimeout")) {
										timeout.set((Integer) statementArgs[0]);
									}
									if (statementMethod.getName().equals("execute")) {
										throw new SQLTimeoutException("busy");
									}
									return invoke(value, statementMethod, statementArgs);
								});
					});
			final var failure = assertThrows(DbConcurrencyException.class,
					() -> command.executeMigrationLock(timingOut, operations));
			assertEquals(7, timeout.get());
			assertTrue(failure.getMessage().contains("7 seconds"));
			assertTrue(failure.getCause() instanceof SQLTimeoutException);
		}
	}

	/** Complete the competing transaction immediately before the real LOCK statement. */
	private Connection beforeLock(final Connection connection, final Runnable competitor) {
		final var invoked = new AtomicBoolean();
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					final Object value = invoke(connection, method, args);
					if (method.getName().equals("createStatement")) {
						return Proxy.newProxyInstance(Statement.class.getClassLoader(),
								new Class<?>[] { Statement.class }, (statementProxy, statementMethod, statementArgs) -> {
									if (statementMethod.getName().equals("execute") && statementArgs[0] instanceof String sql
											&& sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("LOCK TABLE")
											&& invoked.compareAndSet(false, true)) {
										competitor.run();
									}
									return invoke(value, statementMethod, statementArgs);
								});
					}
					return value;
				});
	}

	private Object invoke(final Object target, final Method method, final Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (final InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
