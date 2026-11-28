package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;

class JdbcBatchReEvaluationSafetyTest {

	private static final String UPDATE = "UPDATE t SET v=/*v*/0 WHERE id IN /*ids*/(0)";

	private Connection database() throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY, v INTEGER)");
			statement.execute("INSERT INTO t VALUES(1,0),(2,0),(3,0),(4,0)");
		}
		connection.setAutoCommit(false);
		return connection;
	}

	private SqlNode parse(String sql) {
		return SqlParser.getInstance().parse(null, SqlType.UPDATE, sql);
	}

	private Object call(Object target, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private class Counts {
		int prepares;
		int updates;
		int binds;
		int commits;
		int pending;
		final List<Integer> batches = new ArrayList<>();

		Connection wrap(Connection connection) {
			return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
					(proxy, method, args) -> {
						if (method.getName().equals("commit")) commits++;
						Object result = call(connection, method, args);
						if (!method.getName().equals("prepareStatement")) return result;
						prepares++;
						return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { PreparedStatement.class },
								(p, m, a) -> {
									if (m.getName().equals("setInt")) binds++;
									if (m.getName().equals("addBatch")) pending++;
									if (m.getName().equals("executeBatch")) batches.add(pending);
									if (m.getName().equals("clearBatch")) pending = 0;
									if (m.getName().equals("executeUpdate")) updates++;
									return call(result, m, a);
								});
					});
		}
	}

	@Test
	void fixedScalarSqlKeepsBatchSizesStatementReuseAndCommitBoundaries() throws Exception {
		try (Connection connection = database()) {
			Counts counts = new Counts();
			List<Integer> results = new ArrayList<>();
			try (var handler = new JdbcBatchIterateHander(List.of(parse("UPDATE t SET v=/*v*/0 WHERE id=1")), 3, 2)) {
				handler.setBatchUpdateResultHandler(result -> results.add(result.getValues().size()));
				assertEquals(8, handler.execute(counts.wrap(connection),
						IntStream.rangeClosed(1, 8).mapToObj(v -> Map.of("v", v)).toList()));
			}
			assertEquals(List.of(3, 3, 2), counts.batches);
			assertEquals(counts.batches, results);
			assertEquals(2, counts.prepares); // Existing cache distinguishes full and partial batch sizes.
			assertEquals(0, counts.updates);
			assertEquals(2, counts.commits);
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT v FROM t WHERE id=1")) {
				assertTrue(rows.next());
				assertEquals(8, rows.getInt(1));
			}
		}
	}

	@Test
	void sameLengthArraysKeepBatchingAndRefreshValues() throws Exception {
		try (Connection connection = database()) {
			Counts counts = new Counts();
			try (var handler = new JdbcBatchIterateHander(parse(UPDATE), 2)) {
				assertEquals(4, handler.execute(counts.wrap(connection), List.of(
						Map.of("v", 10, "ids", List.of(1, 2)), Map.of("v", 20, "ids", List.of(3, 4)),
						Map.of("v", 30, "ids", List.of(1, 2)), Map.of("v", 40, "ids", List.of(3, 4)))));
			}
			assertEquals(1, counts.prepares);
			assertEquals(List.of(2, 2), counts.batches);
			assertEquals(0, counts.updates);
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT v FROM t ORDER BY id")) {
				for (int value : List.of(30, 30, 40, 40)) {
					assertTrue(rows.next());
					assertEquals(value, rows.getInt(1));
				}
			}
		}
	}

	@Test
	void changedArrayFailsBeforeBindingOrFlushingAndUsesExistingRollbackHandler() throws Exception {
		for (int batchSize : List.of(1, 2)) {
			try (Connection connection = database()) {
				Counts counts = new Counts();
				int[] rollbacks = { 0 };
				try (var handler = new JdbcBatchIterateHander(parse(UPDATE), batchSize)) {
					handler.setRollbackHandler(conn -> { rollbacks[0]++; conn.rollback(); });
					SQLException failure = assertThrows(SQLException.class, () -> handler.execute(counts.wrap(connection),
							List.of(Map.of("v", 10, "ids", List.of(1, 2)), Map.of("v", 20, "ids", List.of(2, 3, 4)))));
					assertTrue(failure.getMessage().contains("setEvaluateSqlPerRow(true)"));
				}
				assertEquals(3, counts.binds);
				assertTrue(counts.batches.isEmpty());
				assertEquals(batchSize == 1 ? 1 : 0, counts.updates);
				assertEquals(0, counts.commits);
				assertEquals(1, rollbacks[0]);
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT SUM(v) FROM t")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
			}
		}
	}

	@Test
	void bothStatementCacheKeysRejectChangedShapeWithoutBinding() throws Exception {
		for (boolean signatureKey : List.of(false, true)) {
			try (Connection connection = database(); var holder = new StatementHolder(parse(UPDATE))) {
				Counts counts = new Counts();
				Connection tracked = counts.wrap(connection);
				Table table = new Table("t");
				SqlSignature signature = new SqlSignature(table, table.getRows());
				Map<String, Object> first = Map.of("v", 10, "ids", List.of(1, 2));
				Map<String, Object> changed = Map.of("v", 20, "ids", List.of(1, 2, 3));
				if (signatureKey) {
					holder.createStatement(tracked, signature, 1, first, false);
					assertThrows(SQLException.class, () -> holder.getStatement(signature, 1, changed));
				} else {
					holder.createStatement(tracked, 1, 1, first, false);
					assertThrows(SQLException.class, () -> holder.getStatement(1, 1, changed));
				}
				assertEquals(3, counts.binds);
				assertEquals(1, counts.prepares);
				assertEquals(0, counts.updates);
			}
		}
	}
}
