/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;

/**
 * Periodic commits and restart of free-form root SELECTs, including drivers that
 * close the root cursor at commit as JDBC allows.
 */
class JdbcTreeDataSessionRestartTest {
	private static final String CHECKPOINT_SQL = """
			SELECT ID, V FROM ROOT_TABLE
			WHERE 1=1
			  /*if checkpoint != null*/ AND ID > /*checkpoint.ID*/0 /*end*/
			ORDER BY ID
			""";

	@Test
	void periodicCommitsWithoutRestartFailBeforeChangingDataWhenTheCursorCannotBeHeld() throws Exception {
		try (var raw = database()) {
			var connection = closingCursorsAtCommit(raw, new AtomicInteger());
			var table = table(connection);
			var failure = assertThrows(IllegalStateException.class,
					() -> run(connection, table, JdbcTreeDataRestart.none(), "SELECT ID, V FROM ROOT_TABLE ORDER BY ID",
							new ArrayList<>(), -1));
			assertTrue(failure.getMessage().contains("closes the root cursor at commit"), failure.getMessage());
			assertEquals(0, sum(raw));
		}
	}

	@Test
	void selfExcludingRootSelectIsReopenedAfterEachCommit() throws Exception {
		try (var raw = database()) {
			var queries = new AtomicInteger();
			var connection = closingCursorsAtCommit(raw, queries);
			var processed = new ArrayList<Integer>();
			run(connection, table(connection), JdbcTreeDataRestart.selfExcluding(),
					"SELECT ID, V FROM ROOT_TABLE WHERE V = 0 ORDER BY ID", processed, -1);
			assertEquals(List.of(1, 2, 3, 4, 5), processed);
			assertEquals(5, sum(raw));
			assertTrue(queries.get() > 1, "root SELECT was re-executed: " + queries.get());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void checkpointRestartContinuesAfterTheLastCommittedRootAndThenCompletes(boolean holdable) throws Exception {
		try (var raw = database()) {
			var connection = holdable ? raw : closingCursorsAtCommit(raw, new AtomicInteger());
			var table = table(connection);
			var restart = JdbcTreeDataRestart.checkpoint("orders", List.of("ID"),
					new JdbcBulkMigrationCheckpointStore(connection, "TREE_CHECKPOINTS"));
			var first = new ArrayList<Integer>();
			// The business code fails while processing root 4: roots 1 and 2 were committed.
			assertThrows(IllegalStateException.class, () -> run(connection, table, restart, CHECKPOINT_SQL, first, 4));
			assertEquals(2, sum(raw));
			var second = new ArrayList<Integer>();
			run(connection, table, restart, CHECKPOINT_SQL, second, -1);
			// V is incremented, so a reprocessed root would show up as 2.
			assertEquals(List.of(3, 4, 5), second);
			assertEquals(5, sum(raw));
			var third = new ArrayList<Integer>();
			run(connection, table, restart, CHECKPOINT_SQL, third, -1);
			assertEquals(List.of(), third);
			assertEquals(5, sum(raw));
		}
	}

	@Test
	void stoppingEarlyKeepsTheCheckpointOpen() throws Exception {
		try (var raw = database()) {
			var table = table(raw);
			var restart = JdbcTreeDataRestart.checkpoint("early", List.of("ID"),
					new JdbcBulkMigrationCheckpointStore(raw, "TREE_CHECKPOINTS"));
			var first = new ArrayList<Integer>();
			try (var session = session(raw, table, restart)) {
				session.execute(active -> {
					active.select(table, CHECKPOINT_SQL);
					while (active.next(table)) {
						Row row = active.getRow(table);
						first.add((Integer) row.get("ID"));
						row.put("V", (Integer) row.get("V") + 1);
						row.update();
						if (first.size() == 2)
							break;
					}
				});
			}
			var second = new ArrayList<Integer>();
			run(raw, table, restart, CHECKPOINT_SQL, second, -1);
			assertEquals(List.of(3, 4, 5), second);
			assertEquals(5, sum(raw));
		}
	}

	@Test
	void checkpointRestartRejectsUnorderedRootsChangedSqlAndForeignStores() throws Exception {
		try (var raw = database(); var other = database()) {
			var table = table(raw);
			var store = new JdbcBulkMigrationCheckpointStore(raw, "TREE_CHECKPOINTS");
			var restart = JdbcTreeDataRestart.checkpoint("orders", List.of("ID"), store);
			var unordered = CHECKPOINT_SQL.replace("ORDER BY ID", "ORDER BY ID DESC");
			var failure = assertThrows(IllegalStateException.class,
					() -> run(raw, table, restart, unordered, new ArrayList<>(), -1));
			assertTrue(failure.getMessage().contains("strictly increasing"), failure.getMessage());
			assertEquals(0, sum(raw));
			// Save progress with one SQL, then change it.
			assertThrows(IllegalStateException.class,
					() -> run(raw, table, restart, CHECKPOINT_SQL, new ArrayList<>(), 4));
			var changed = CHECKPOINT_SQL.replace("ROOT_TABLE", "ROOT_TABLE R");
			failure = assertThrows(IllegalStateException.class,
					() -> run(raw, table, restart, changed, new ArrayList<>(), -1));
			assertTrue(failure.getMessage().contains("changed since checkpoint 'orders'"), failure.getMessage());
			var noVariable = "SELECT ID, V FROM ROOT_TABLE ORDER BY ID";
			failure = assertThrows(IllegalStateException.class, () -> run(raw, table, JdbcTreeDataRestart
					.checkpoint("plain", List.of("ID"), store), noVariable, new ArrayList<>(), -1));
			assertTrue(failure.getMessage().contains("'checkpoint' variable"), failure.getMessage());
			var foreign = JdbcTreeDataRestart.checkpoint("foreign", List.of("ID"),
					new JdbcBulkMigrationCheckpointStore(other, "TREE_CHECKPOINTS"));
			assertThrows(IllegalArgumentException.class,
					() -> run(raw, table, foreign, CHECKPOINT_SQL, new ArrayList<>(), -1));
			assertThrows(IllegalArgumentException.class, () -> run(raw, table,
					JdbcTreeDataRestart.checkpoint("bad", List.of("MISSING"), store), CHECKPOINT_SQL,
					new ArrayList<>(), -1));
		}
	}

	private static void run(Connection connection, Table table, JdbcTreeDataRestart restart, String sql,
			List<Integer> processed, int failAt) throws SQLException {
		try (var session = session(connection, table, restart)) {
			session.execute(active -> {
				active.select(table, sql);
				while (active.next(table)) {
					Row row = active.getRow(table);
					int id = (Integer) row.get("ID");
					if (id == failAt)
						throw new IllegalStateException("business failure at " + id);
					processed.add(id);
					row.put("V", (Integer) row.get("V") + 1);
					row.update();
				}
			});
		}
	}

	private static JdbcTreeDataSession session(Connection connection, Table table, JdbcTreeDataRestart restart) {
		var session = new JdbcTreeDataSession(connection, table);
		session.setTableOperationMode(JdbcTreeDataSession.TableOperationMode.UPDATE);
		session.setRootBatchSize(2);
		session.setCommitEveryRootBatches(1);
		session.setRestart(restart);
		return session;
	}

	private static Table table(Connection connection) throws SQLException {
		var table = new Table("ROOT_TABLE").setSchemaName("PUBLIC");
		table.setDialect(DialectResolver.getInstance().getDialect(connection));
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("V").setDataType(DataType.INT));
		table.setPrimaryKey((String) null, table.getColumns().get("ID"));
		return table;
	}

	private static Connection database() throws SQLException {
		var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ROOT_TABLE(ID INT PRIMARY KEY, V INT)");
			statement.execute("INSERT INTO ROOT_TABLE VALUES(1,0),(2,0),(3,0),(4,0),(5,0)");
		}
		connection.setAutoCommit(false);
		return connection;
	}

	private static int sum(Connection raw) throws SQLException {
		try (var statement = raw.createStatement(); var rs = statement.executeQuery("SELECT SUM(V) FROM ROOT_TABLE")) {
			rs.next();
			return rs.getInt(1);
		}
	}

	/**
	 * HSQLDB keeps in-memory result sets readable after commit. This wrapper
	 * reports no cursor holdability and closes CLOSE_CURSORS_AT_COMMIT result sets
	 * at commit, as other drivers do.
	 */
	private static Connection closingCursorsAtCommit(Connection connection, AtomicInteger queries) {
		int[] commits = { 0 };
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getMetaData")) {
						DatabaseMetaData metaData = connection.getMetaData();
						return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
								new Class<?>[] { DatabaseMetaData.class },
								(p, m, a) -> m.getName().equals("supportsResultSetHoldability")
										&& Integer.valueOf(ResultSet.HOLD_CURSORS_OVER_COMMIT).equals(a[0]) ? false
												: invoke(m, metaData, a));
					}
					if (method.getName().equals("commit"))
						commits[0]++;
					if (method.getName().equals("prepareStatement") && args.length == 4
							&& Integer.valueOf(ResultSet.CLOSE_CURSORS_AT_COMMIT).equals(args[3])) {
						var statement = (PreparedStatement) invoke(method, connection, args);
						return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
								new Class<?>[] { PreparedStatement.class }, (p, m, a) -> {
									Object result = invoke(m, statement, a);
									if (!m.getName().equals("executeQuery"))
										return result;
									queries.incrementAndGet();
									var resultSet = (ResultSet) result;
									int opened = commits[0];
									return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
											new Class<?>[] { ResultSet.class }, (p2, m2, a2) -> {
												if (m2.getName().equals("next") && commits[0] != opened)
													throw new SQLException("ResultSet closed by commit");
												return invoke(m2, resultSet, a2);
											});
								});
					}
					return invoke(method, connection, args);
				});
	}

	private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
