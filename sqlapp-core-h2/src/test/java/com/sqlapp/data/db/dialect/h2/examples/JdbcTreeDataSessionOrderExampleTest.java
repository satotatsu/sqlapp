/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.dialect.h2.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionFailure;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult.TransactionOutcome;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/**
 * Executable example of a procedural order-processing batch. Each test creates
 * its own in-memory database and verifies the committed database state.
 */
class JdbcTreeDataSessionOrderExampleTest {

	/** The application logic: SQL selects work; ordinary loops process each hierarchy. */
	private JdbcTreeDataExecutionResult processOrders(Connection connection) throws SQLException {
		var schema = SchemaUtils.getSchema(connection, "PUBLIC", "ORDER_HEADER", "ORDER_LINE").orElseThrow();
		Table orders = schema.getTables().get("ORDER_HEADER");
		Table lines = schema.getTables().get("ORDER_LINE");
		return new JdbcTreeDataSession(connection, orders, lines).execute(session -> {
			// Loaded rows remain unchanged unless the business logic marks an operation.
			session.setTableOperationMode(TableOperationMode.NONE);
			// Small batches make execution before a later business failure observable.
			session.setRootBatchSize(1);
			// Keep the default commit interval: commit the whole execution on success.
			session.select(lines);
			session.select(orders, """
					SELECT * FROM ORDER_HEADER
					WHERE STATUS IN /*statuses*/('READY')
					  AND ID >= /*minimumId*/0
					ORDER BY ID
					""", Map.of("statuses", List.of("READY", "CANCELLED"), "minimumId", 1));

			while (session.next(orders)) {
				Row order = session.getRow(orders);
				boolean cancelled = "CANCELLED".equals(order.get("STATUS"));
				BigDecimal total = BigDecimal.ZERO;
				while (session.next(lines)) {
					Row line = session.getRow(lines);
					if (cancelled) {
						// Explicitly mark children too: the FK has no ON DELETE CASCADE.
						line.delete();
					} else {
						Number quantity = line.get("QUANTITY");
						if (quantity.intValue() <= 0) {
							throw new SQLException("Order " + order.get("ID") + ", line " + line.get("ID")
									+ ": QUANTITY must be positive.");
						}
						BigDecimal price = line.get("UNIT_PRICE");
						BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity.longValue()));
						line.put("AMOUNT", amount);
						line.update();
						total = total.add(amount);
					}
				}
				if (cancelled) {
					order.delete(); // The session executes child deletes before parent deletes.
				} else {
					order.put("TOTAL", total);
					order.put("STATUS", "DONE");
					order.update();
				}
			}
		});
	}

	@Test
	void updatesSelectedOrdersAndDeletesCancelledHierarchy() throws Exception {
		try (Connection connection = exampleDatabase()) {
			var result = processOrders(connection);
			printResult(result);
			assertTrue(result.successful());
			assertEquals(3, result.completedRootBatches());
			assertEquals(3, result.committedRootBatches());
			assertEquals(1, result.commits());
			assertCount(result, "ORDER_HEADER", SqlType.UPDATE, 2, 2);
			assertCount(result, "ORDER_HEADER", SqlType.DELETE, 1, 1);
			assertCount(result, "ORDER_LINE", SqlType.UPDATE, 3, 3);
			assertCount(result, "ORDER_LINE", SqlType.DELETE, 1, 1);
			// A rollback after execute cannot undo the successful final commit.
			connection.rollback();
			assertEquals(List.of("1:DONE:250.00", "3:DONE:30.00", "4:HOLD:0.00"), headers(connection));
			assertEquals(List.of("101:200.00", "102:50.00", "301:30.00", "401:0.00"), lines(connection));
		}
	}

	@Test
	void invalidLaterOrderRollsBackEarlierExecutedUpdatesAndDeletes() throws Exception {
		try (Connection connection = exampleDatabase()) {
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("UPDATE ORDER_LINE SET QUANTITY=0 WHERE ID=301");
			}
			connection.commit(); // Input preparation is outside the processing transaction.
			SQLException failure = assertThrows(SQLException.class, () -> processOrders(connection));
			var result = JdbcTreeDataExecutionFailure.result(failure).orElseThrow();
			System.out.println(failure.getMessage());
			printResult(result);
			assertSame(TransactionOutcome.ROLLED_BACK, result.remainingTransaction());
			assertEquals(2, result.completedRootBatches());
			assertEquals(0, result.commits());
			assertCount(result, "ORDER_HEADER", SqlType.UPDATE, 1, 0);
			assertCount(result, "ORDER_HEADER", SqlType.DELETE, 1, 0);
			assertCount(result, "ORDER_LINE", SqlType.UPDATE, 2, 0);
			assertCount(result, "ORDER_LINE", SqlType.DELETE, 1, 0);
			// Order 2 and its child are restored; order 1's updates are undone.
			assertEquals(List.of("1:READY:0.00", "2:CANCELLED:0.00", "3:READY:0.00", "4:HOLD:0.00"),
					headers(connection));
			assertEquals(List.of("101:0.00", "102:0.00", "201:0.00", "301:0.00", "401:0.00"), lines(connection));
		}
	}

	private void printResult(JdbcTreeDataExecutionResult result) {
		System.out.println("successful=" + result.successful() + ", completedBatches=" + result.completedRootBatches()
				+ ", committedBatches=" + result.committedRootBatches() + ", commits=" + result.commits()
				+ ", remainingTransaction=" + result.remainingTransaction());
		result.operations().forEach(operation -> System.out.println(operation.table().table() + " "
				+ operation.operation() + " executed=" + operation.executed() + " committed=" + operation.committed()));
	}

	private void assertCount(JdbcTreeDataExecutionResult result, String table, SqlType type, long executed,
			long committed) {
		var operation = result.operations().stream()
				.filter(value -> table.equals(value.table().table()) && type == value.operation()).findFirst().orElseThrow();
		assertEquals(executed, operation.executed().knownAffectedRows());
		assertEquals(committed, operation.committed().knownAffectedRows());
	}

	private Connection exampleDatabase() throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:order-example-" + UUID.randomUUID());
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE ORDER_HEADER (
					    ID INT PRIMARY KEY, STATUS VARCHAR(16) NOT NULL,
					    TOTAL DECIMAL(12,2) NOT NULL DEFAULT 0
					)
					""");
			statement.execute("""
					CREATE TABLE ORDER_LINE (
					    ID INT PRIMARY KEY, ORDER_ID INT NOT NULL REFERENCES ORDER_HEADER(ID),
					    QUANTITY INT NOT NULL, UNIT_PRICE DECIMAL(12,2) NOT NULL,
					    AMOUNT DECIMAL(12,2) NOT NULL DEFAULT 0
					)
					""");
			statement.executeUpdate("""
					INSERT INTO ORDER_HEADER(ID, STATUS)
					VALUES(1,'READY'),(2,'CANCELLED'),(3,'READY'),(4,'HOLD')
					""");
			statement.executeUpdate("""
					INSERT INTO ORDER_LINE(ID, ORDER_ID, QUANTITY, UNIT_PRICE)
					VALUES(101,1,2,100),(102,1,1,50),(201,2,1,20),(301,3,3,10),(401,4,1,40)
					""");
		}
		connection.commit();
		return connection;
	}

	private List<String> headers(Connection connection) throws SQLException {
		return query(connection, "SELECT ID, STATUS, TOTAL FROM ORDER_HEADER ORDER BY ID", true);
	}

	private List<String> lines(Connection connection) throws SQLException {
		return query(connection, "SELECT ID, AMOUNT FROM ORDER_LINE ORDER BY ID", false);
	}

	private List<String> query(Connection connection, String sql, boolean header) throws SQLException {
		var rows = new java.util.ArrayList<String>();
		try (Statement statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
			while (result.next()) {
				rows.add(header ? result.getInt(1) + ":" + result.getString(2) + ":" + result.getBigDecimal(3)
						: result.getInt(1) + ":" + result.getBigDecimal(2));
			}
		}
		return rows;
	}
}
