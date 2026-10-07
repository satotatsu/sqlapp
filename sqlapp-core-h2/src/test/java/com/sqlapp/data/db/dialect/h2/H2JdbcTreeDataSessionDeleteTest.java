/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.dialect.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataCopySession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

class H2JdbcTreeDataSessionDeleteTest {

	@Test
	void deletesThreeLevelsBeforeInsertingAnotherHierarchy() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			List<String> sql = new ArrayList<>();
			new JdbcTreeDataSession(connection, schema.getTables()).execute(session -> {
				session.setSqlHandler((table, type, statement) -> {
					sql.add(type + ":" + table.getName());
					return statement;
				});
				for (Table table : schema.getTables()) {
					row(session, table, 1).delete();
				}
				for (Table table : schema.getTables()) {
					row(session, table, 2);
				}
			});
			assertEquals(List.of("DELETE:G", "DELETE:C", "DELETE:P", "INSERT:P", "INSERT:C", "INSERT:G"), sql);
			for (Table table : schema.getTables()) {
				assertEquals(1, count(connection, table.getName()));
			}
			connection.rollback();
			assertEquals(1, count(connection, "P"));
		}
	}

	@Test
	void leavesUnmarkedChildrenAndRollsBackEarlierDeletes() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			assertThrows(SQLException.class, () -> new JdbcTreeDataSession(connection, schema.getTables())
					.execute(session -> {
						row(session, schema.getTables().get("P"), 1).delete();
						row(session, schema.getTables().get("C"), 1).unchanged();
						row(session, schema.getTables().get("G"), 1).delete();
					}));
			for (String table : List.of("P", "C", "G")) {
				assertEquals(1, count(connection, table));
			}
		}
	}

	@Test
	void businessFailureDiscardsPendingRowsAndRollsBackExecutedBatch() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			RuntimeException failure = new RuntimeException("business failure");
			assertSame(failure, assertThrows(RuntimeException.class,
					() -> new JdbcTreeDataSession(connection, schema.getTables()).execute(session -> {
						session.setRootBatchSize(1);
						row(session, schema.getTables().get("P"), 2);
						row(session, schema.getTables().get("P"), 3);
						throw failure;
					})));
			assertEquals(1, count(connection, "P"));
			assertFalse(connection.isClosed());
		}
	}

	@Test
	void deleteModeAlsoUsesChildFirstOrder() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			new JdbcTreeDataSession(connection, schema.getTables()).execute(session -> {
				session.setTableOperationMode(TableOperationMode.DELETE);
				for (Table table : schema.getTables()) {
					row(session, table, 1);
				}
			});
			assertEquals(0, count(connection, "P"));
		}
	}

	@Test
	void deletesSelectedRowsAndCommitsAfterTheReadBufferWasCleared() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			new JdbcTreeDataSession(connection, schema.getTables()).execute(session -> {
				for (Table table : List.of(schema.getTables().get("G"), schema.getTables().get("C"))) {
					session.select(table);
				}
				session.select(schema.getTables().get("P"));
				session.readAll((table, row) -> row.delete());
			});
			connection.rollback();
			assertEquals(0, count(connection, "P"));
		}
	}

	@Test
	void businessFailureDoesNotExecutePendingRows() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			List<String> sql = new ArrayList<>();
			assertThrows(SQLException.class, () -> new JdbcTreeDataSession(connection, schema.getTables())
					.execute(session -> {
						session.setSqlHandler((table, type, statement) -> {
							sql.add(statement);
							return statement;
						});
						row(session, schema.getTables().get("P"), 2);
						throw new SQLException("business failure");
					}));
			assertEquals(List.of(), sql);
			assertEquals(1, count(connection, "P"));
		}
	}

	@Test
	void commitFailureClosesStatementsAndRollsBack() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			SQLException failure = new SQLException("commit failure");
			List<PreparedStatement> statements = new ArrayList<>();
			Connection failing = intercept(connection, "commit", failure);
			assertSame(failure, assertThrows(SQLException.class,
					() -> new JdbcTreeDataSession(failing, schema.getTables()).execute(session -> {
						session.setPreparedStatementBeforeExecuteHandler(statements::add);
						row(session, schema.getTables().get("P"), 2);
					})));
			assertEquals(1, count(connection, "P"));
			assertFalse(statements.isEmpty());
			for (PreparedStatement statement : statements) {
				assertTrue(statement.isClosed());
			}
		}
	}

	@Test
	void rollbackFailureIsSuppressedOnBusinessFailure() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			SQLException rollbackFailure = new SQLException("rollback failure");
			RuntimeException failure = new RuntimeException("business failure");
			Connection failing = intercept(connection, "rollback", rollbackFailure);
			assertSame(failure, assertThrows(RuntimeException.class,
					() -> new JdbcTreeDataSession(failing, schema.getTables()).execute(session -> {
						throw failure;
					})));
			assertEquals(1, failure.getSuppressed().length);
			assertSame(rollbackFailure, failure.getSuppressed()[0]);
		}
	}

	@Test
	void periodicCommitSurvivesLaterBusinessFailure() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			assertThrows(RuntimeException.class, () -> new JdbcTreeDataSession(connection, schema.getTables())
					.execute(session -> {
						session.setRootBatchSize(1);
						session.setCommitEveryRootBatches(1);
						row(session, schema.getTables().get("P"), 2);
						row(session, schema.getTables().get("P"), 3);
						throw new RuntimeException("business failure");
					}));
			assertEquals(2, count(connection, "P"));
		}
	}

	@Test
	void rowProcessingRequiresExecuteAndCloseDoesNotCommit() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			JdbcTreeDataSession session = new JdbcTreeDataSession(connection, schema.getTables());
			assertThrows(IllegalStateException.class, () -> session.newRow(schema.getTables().get("P")));
			try (Statement statement = connection.createStatement()) {
				statement.execute("INSERT INTO P VALUES(2)");
			}
			session.close();
			connection.rollback();
			assertEquals(1, count(connection, "P"));
		}
	}

	@Test
	void rejectsNestedExecutionAndClosingInsideCallback() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			JdbcTreeDataSession session = new JdbcTreeDataSession(connection, schema.getTables());
			assertThrows(IllegalStateException.class, () -> session.execute(active -> {
				row(active, schema.getTables().get("P"), 2);
				active.execute(nested -> { });
			}));
			assertEquals(1, count(connection, "P"));
			assertThrows(IllegalStateException.class, () -> session.execute(active -> active.close()));
			session.execute(active -> row(active, schema.getTables().get("P"), 3));
			connection.rollback();
			assertEquals(2, count(connection, "P"));
		}
	}

	@Test
	void swallowedBatchFailureStillRollsBackExecution() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			assertThrows(IllegalStateException.class, () -> new JdbcTreeDataSession(connection, schema.getTables())
					.execute(session -> {
						session.setRootBatchSize(1);
						row(session, schema.getTables().get("P"), 1).delete();
						assertThrows(SQLException.class, () -> row(session, schema.getTables().get("P"), 2));
					}));
			assertEquals(1, count(connection, "P"));
		}
	}

	@Test
	void copyBusinessFailureRollsBackSourceDeletionAndTargetInsertion() throws Exception {
		try (Connection connection = database()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("DELETE FROM G");
				statement.execute("DELETE FROM C");
				statement.execute("INSERT INTO P VALUES(2)");
				statement.execute("CREATE TABLE T (ID INT PRIMARY KEY)");
			}
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC", "P", "T").orElseThrow();
			Table sourceTable = schema.getTables().get("P");
			Table targetTable = schema.getTables().get("T");
			JdbcTreeDataSession source = new JdbcTreeDataSession(connection, sourceTable);
			source.setTableOperationMode(TableOperationMode.NONE);
			source.select(sourceTable, "SELECT * FROM P ORDER BY ID");
			JdbcTreeDataSession target = new JdbcTreeDataSession(connection, targetTable);
			JdbcTreeDataCopySession copy = new JdbcTreeDataCopySession(source, target);
			copy.setRootBatchSize(1);
			assertThrows(SQLException.class, () -> copy.execute(active -> {
				while (active.next(sourceTable)) {
					active.newCopy(active.getRow(sourceTable), targetTable);
				}
				throw new SQLException("business failure");
			}));
			assertEquals(2, count(connection, "P"));
			assertEquals(0, count(connection, "T"));
		}
	}

	@Test
	void rejectsAutoCommitBeforeRunningWork() throws Exception {
		try (Connection connection = database()) {
			Schema schema = schema(connection);
			connection.setAutoCommit(true);
			assertThrows(IllegalStateException.class, () -> new JdbcTreeDataSession(connection, schema.getTables())
					.execute(session -> { throw new AssertionError("must not run"); }));
		}
	}

	private Row row(JdbcTreeDataSession session, Table table, int id) throws SQLException {
		Row row = session.newRow(table);
		row.put("ID", id);
		return row;
	}

	private Connection intercept(Connection connection, String operation, SQLException failure) {
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, arguments) -> {
					if (method.getName().equals(operation)) {
						throw failure;
					}
					try {
						return method.invoke(connection, arguments);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				});
	}

	private Schema schema(Connection connection) throws SQLException {
		Schema schema = SchemaUtils.getSchema(connection, "PUBLIC", "P", "C", "G").orElseThrow();
		// Use a deterministic parent-first order when creating the test rows.
		schema.getTables().sort((a, b) -> Integer.compare(List.of("P", "C", "G").indexOf(a.getName()),
				List.of("P", "C", "G").indexOf(b.getName())));
		return schema;
	}

	private Connection database() throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID());
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE P (ID INT PRIMARY KEY)");
			statement.execute("CREATE TABLE C (ID INT PRIMARY KEY, P_ID INT NOT NULL REFERENCES P(ID))");
			statement.execute("CREATE TABLE G (ID INT PRIMARY KEY, C_ID INT NOT NULL REFERENCES C(ID))");
			statement.execute("INSERT INTO P VALUES(1)");
			statement.execute("INSERT INTO C VALUES(1,1)");
			statement.execute("INSERT INTO G VALUES(1,1)");
		}
		connection.commit();
		return connection;
	}

	private int count(Connection connection, String table) throws SQLException {
		try (Statement statement = connection.createStatement();
				var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			result.next();
			return result.getInt(1);
		}
	}
}
