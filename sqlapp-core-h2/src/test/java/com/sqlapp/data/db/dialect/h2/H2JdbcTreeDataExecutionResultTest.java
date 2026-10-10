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
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionFailure;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult.Phase;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult.TransactionOutcome;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataCopySession;

class H2JdbcTreeDataExecutionResultTest {

	@Test
	void emptyExecutionDoesNotClaimACommit() throws Exception {
		try (Connection connection = database()) {
			var result = new JdbcTreeDataSession(connection, table(connection)).execute(active -> {
			});
			assertEquals(0, result.commits());
			assertEquals(TransactionOutcome.NOT_REQUIRED, result.remainingTransaction());
			assertTrue(result.operations().isEmpty());
		}
	}

	@Test
	void reportsExecutedAndCommittedRowsAndDoesNotChangePreviousSnapshots() throws Exception {
		try (Connection connection = database()) {
			Table table = table(connection);
			JdbcTreeDataSession session = new JdbcTreeDataSession(connection, table);
			var result = session.execute(active -> {
				active.setRootBatchSize(1);
				active.newRow(table).put("ID", 1);
				active.newRow(table).put("ID", 2);
			});
			assertTrue(result.successful());
			assertEquals(2, result.completedRootBatches());
			assertEquals(2, result.committedRootBatches());
			assertEquals(1, result.commits());
			var insert = result.operations().getFirst();
			assertEquals("A", insert.table().table());
			assertEquals("PUBLIC", insert.table().schema());
			assertEquals(SqlType.INSERT, insert.operation());
			assertEquals(2, insert.executed().knownAffectedRows());
			assertEquals(insert.executed(), insert.committed());
			assertThrows(UnsupportedOperationException.class, () -> result.operations().clear());
			var next = session.execute(active -> active.newRow(table).put("ID", 3));
			assertEquals(1, next.operations().getFirst().executed().knownAffectedRows());
			assertEquals(2, insert.executed().knownAffectedRows());
		}
	}

	@Test
	void failureRetainsEarlierCommitsAndOriginalException() throws Exception {
		try (Connection connection = database()) {
			Table table = table(connection);
			RuntimeException failure = new RuntimeException("business");
			assertSame(failure, assertThrows(RuntimeException.class,
					() -> new JdbcTreeDataSession(connection, table).execute(active -> {
						active.setRootBatchSize(1);
						active.setCommitEveryRootBatches(2);
						for (int i = 1; i <= 4; i++)
							active.newRow(table).put("ID", i);
						throw failure;
					})));
			var result = JdbcTreeDataExecutionFailure.result(failure).orElseThrow();
			assertFalse(result.successful());
			assertEquals(3, result.completedRootBatches());
			assertEquals(2, result.committedRootBatches());
			assertEquals(1, result.commits());
			assertEquals(3, result.operations().getFirst().executed().knownAffectedRows());
			assertEquals(2, result.operations().getFirst().committed().knownAffectedRows());
			assertEquals(TransactionOutcome.ROLLED_BACK, result.remainingTransaction());
			assertEquals(Phase.BUSINESS, result.failure().phase());
		}
	}

	@Test
	void partialBatchFailureReportsDriverEvidenceWithoutClaimingCommit() throws Exception {
		try (Connection connection = database()) {
			Table table = table(connection);
			SQLException failure = assertThrows(SQLException.class,
					() -> new JdbcTreeDataSession(connection, table).execute(active -> {
						active.newRow(table).put("ID", 1);
						active.newRow(table).put("ID", 1);
					}));
			var result = JdbcTreeDataExecutionFailure.result(failure).orElseThrow();
			var insert = result.operations().getFirst();
			assertEquals(1, insert.executed().knownAffectedRows());
			assertEquals(1, insert.executed().failedCounts());
			assertEquals(0, insert.committed().knownAffectedRows());
			assertEquals(SqlType.INSERT, result.failure().operation());
			assertEquals("A", result.failure().table().table());
			assertEquals(Phase.SQL, result.failure().phase());
		}
	}

	@Test
	void afterCommitFailureRetainsDurableCounts() throws Exception {
		try (Connection connection = database()) {
			Table table = table(connection);
			SQLException failure = assertThrows(SQLException.class,
					() -> new JdbcTreeDataSession(connection, table).execute(active -> {
						active.setAfterCommitEveryRootBatchesHandler((count, row) -> {
							throw new SQLException("after commit");
						});
						active.newRow(table).put("ID", 1);
					}));
			var result = JdbcTreeDataExecutionFailure.result(failure).orElseThrow();
			assertEquals(1, result.commits());
			assertEquals(1, result.committedRootBatches());
			assertEquals(1, result.operations().getFirst().committed().knownAffectedRows());
		}
	}

	@Test
	void mergeReportsActualUpdateAndInsertIncludingZeroAffectedRows() throws Exception {
		try (Connection connection = database()) {
			Table table = table(connection);
			var result = new JdbcTreeDataSession(connection, table).execute(active -> {
				var row = active.newRow(table);
				row.put("ID", 1);
				row.put("TXT", 2);
				row.merge();
			});
			assertEquals(2, result.operations().size());
			assertEquals(SqlType.UPDATE, result.operations().getFirst().operation());
			assertEquals(0, result.operations().getFirst().executed().knownAffectedRows());
			assertEquals(SqlType.INSERT, result.operations().getLast().operation());
			assertEquals(1, result.operations().getLast().committed().knownAffectedRows());
		}
	}

	@Test
	void sharedConnectionCopyReportsBothSidesOfTheSameCommit() throws Exception {
		try (Connection connection = database()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE B (ID INT PRIMARY KEY, TXT INT)");
				statement.execute("INSERT INTO A VALUES(1,2)");
			}
			connection.commit();
			Table from = table(connection);
			Table to = SchemaUtils.getSchema(connection, "PUBLIC", "B").orElseThrow().getTables().get("B");
			JdbcTreeDataSession source = new JdbcTreeDataSession(connection, from);
			source.setTableOperationMode(JdbcTreeDataSession.TableOperationMode.NONE);
			source.select(from);
			var result = new JdbcTreeDataCopySession(source, new JdbcTreeDataSession(connection, to)).execute(copy -> {
				while (copy.next(from))
					copy.newCopy(copy.getRow(from), to);
			});
			assertEquals(1, result.source().commits());
			assertEquals(1, result.target().commits());
			assertEquals(SqlType.DELETE, result.source().operations().getFirst().operation());
			assertEquals(1, result.source().operations().getFirst().committed().knownAffectedRows());
			assertEquals(1, result.target().operations().getFirst().committed().knownAffectedRows());
		}
	}

	@Test
	void unknownUpdateCountsRemainUnknownAfterCommit() throws Exception {
		try (Connection connection = database()) {
			Table table = table(connection);
			Connection proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
					new Class<?>[] { Connection.class }, (object, method, arguments) -> {
						Object value = invoke(connection, method, arguments);
						if (!(value instanceof PreparedStatement statement))
							return value;
						return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
								new Class<?>[] { PreparedStatement.class }, (p, m, a) -> {
									Object returned = invoke(statement, m, a);
									return m.getName().equals("executeBatch") ? new int[] { Statement.SUCCESS_NO_INFO }
											: returned;
								});
					});
			var result = new JdbcTreeDataSession(proxy, table).execute(active -> active.newRow(table).put("ID", 1));
			var insert = result.operations().getFirst();
			assertEquals(0, insert.executed().knownAffectedRows());
			assertEquals(1, insert.executed().unknownCounts());
			assertEquals(insert.executed(), insert.committed());
		}
	}

	private Object invoke(Object target, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
		try {
			return method.invoke(target, arguments);
		} catch (InvocationTargetException failure) {
			throw failure.getCause();
		}
	}

	private Connection database() throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE A (ID INT PRIMARY KEY, TXT INT)");
		}
		connection.commit();
		return connection;
	}

	private Table table(Connection connection) throws SQLException {
		return SchemaUtils.getSchema(connection, "PUBLIC", "A").orElseThrow().getTables().get("A");
	}
}
