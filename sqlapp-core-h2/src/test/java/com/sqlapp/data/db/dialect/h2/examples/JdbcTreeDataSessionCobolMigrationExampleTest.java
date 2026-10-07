/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.dialect.h2.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionFailure;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult;
import com.sqlapp.jdbc.sql.JdbcTreeDataExecutionResult.TransactionOutcome;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** A fictional COBOL batch port: order -> line -> shipment allocation. */
class JdbcTreeDataSessionCobolMigrationExampleTest {

	/**
	 * Business processing uses three nested loops, corresponding to COBOL PERFORMs.
	 * A dedicated connection with auto-commit disabled is supplied by the caller.
	 */
	private JdbcTreeDataExecutionResult priceOrders(Connection connection) throws SQLException {
		var schema = SchemaUtils.getSchema(connection, "PUBLIC", "SALES_ORDER", "SALES_LINE", "SHIPMENT_ALLOCATION")
				.orElseThrow();
		var orders = schema.getTables().get("SALES_ORDER");
		var lines = schema.getTables().get("SALES_LINE");
		var allocations = schema.getTables().get("SHIPMENT_ALLOCATION");
		return new JdbcTreeDataSession(connection, orders, lines, allocations).execute(session -> {
			session.setTableOperationMode(TableOperationMode.NONE);
			session.setRootBatchSize(2);
			session.select(allocations);
			session.select(lines);
			session.select(orders, """
					SELECT * FROM SALES_ORDER WHERE STATUS = /*status*/'READY' ORDER BY ID
					""", Map.of("status", "READY"));
			while (session.next(orders)) {
				Row order = session.getRow(orders);
				BigDecimal orderAmount = new BigDecimal("0.00");
				while (session.next(lines)) {
					Row line = session.getRow(lines);
					BigDecimal price = line.get("UNIT_PRICE");
					Number orderedQuantity = line.get("ORDERED_QTY");
					if (price.signum() < 0 || orderedQuantity.longValue() <= 0) {
						throw invalid(order, line, "price must be nonnegative and ordered quantity positive");
					}
					long allocatedQuantity = 0;
					BigDecimal lineAmount = new BigDecimal("0.00");
					while (session.next(allocations)) {
						Row allocation = session.getRow(allocations);
						Number quantity = allocation.get("ALLOCATED_QTY");
						if (quantity.longValue() <= 0) {
							throw invalid(order, line, "allocation " + allocation.get("ID") + " must be positive");
						}
						BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity.longValue()));
						allocation.put("AMOUNT", amount);
						allocation.update();
						allocatedQuantity += quantity.longValue();
						lineAmount = lineAmount.add(amount);
					}
					if (allocatedQuantity != orderedQuantity.longValue()) {
						throw invalid(order, line, "allocated quantity differs from ordered quantity");
					}
					line.put("AMOUNT", lineAmount);
					line.update();
					orderAmount = orderAmount.add(lineAmount);
				}
				order.put("TOTAL", orderAmount);
				order.put("STATUS", "PRICED");
				order.update();
			}
		});
	}

	private SQLException invalid(Row order, Row line, String reason) {
		return new SQLException("Order " + order.get("ID") + ", line " + line.get("ID") + ": " + reason);
	}

	@Test
	void pricesThreeLevelsAndLeavesSqlExcludedHierarchyUnchanged() throws Exception {
		try (Connection connection = database()) {
			var result = priceOrders(connection);
			assertTrue(result.successful());
			assertEquals(2, result.completedRootBatches());
			assertEquals(1, result.commits());
			assertEquals(14, result.operations().stream().mapToLong(o -> o.committed().knownAffectedRows()).sum());
			assertEquals(List.of("1:PRICED:350.00", "2:PRICED:30.00", "3:PRICED:15.00", "4:HOLD:0.00"),
					query(connection, "SELECT ID, STATUS, TOTAL FROM SALES_ORDER ORDER BY ID"));
			assertEquals(List.of("11:300.00", "12:50.00", "21:30.00", "31:15.00", "41:0.00"),
					query(connection, "SELECT ID, AMOUNT FROM SALES_LINE ORDER BY ID"));
			assertEquals(List.of("111:100.00", "112:200.00", "121:25.00", "122:25.00", "211:10.00", "212:20.00",
					"311:15.00", "411:0.00"), query(connection, "SELECT ID, AMOUNT FROM SHIPMENT_ALLOCATION ORDER BY ID"));
		}
	}

	@Test
	void invalidGrandchildInFinalBatchRollsBackAllThreeLevels() throws Exception {
		try (Connection connection = database()) {
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("UPDATE SHIPMENT_ALLOCATION SET ALLOCATED_QTY=2 WHERE ID=311");
			}
			connection.commit();
			SQLException failure = assertThrows(SQLException.class, () -> priceOrders(connection));
			assertTrue(failure.getMessage().contains("Order 3, line 31"));
			var result = JdbcTreeDataExecutionFailure.result(failure).orElseThrow();
			assertEquals(1, result.completedRootBatches());
			assertEquals(0, result.commits());
			assertEquals(TransactionOutcome.ROLLED_BACK, result.remainingTransaction());
			assertTrue(result.operations().stream().anyMatch(o -> o.executed().knownAffectedRows() > 0));
			assertEquals(0, result.operations().stream().mapToLong(o -> o.committed().knownAffectedRows()).sum());
			assertEquals(List.of("1:READY:0.00", "2:READY:0.00", "3:READY:0.00", "4:HOLD:0.00"),
					query(connection, "SELECT ID, STATUS, TOTAL FROM SALES_ORDER ORDER BY ID"));
			for (String table : List.of("SALES_LINE", "SHIPMENT_ALLOCATION")) {
				assertEquals(List.of("0"), query(connection, "SELECT COUNT(*) FROM " + table + " WHERE AMOUNT <> 0"));
			}
		}
	}

	private Connection database() throws SQLException {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:cobol-port-" + UUID.randomUUID());
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SALES_ORDER (ID INT PRIMARY KEY, STATUS VARCHAR(16) NOT NULL, TOTAL DECIMAL(12,2) NOT NULL DEFAULT 0)");
			statement.execute("CREATE TABLE SALES_LINE (ID INT PRIMARY KEY, ORDER_ID INT NOT NULL REFERENCES SALES_ORDER(ID), ORDERED_QTY INT NOT NULL, UNIT_PRICE DECIMAL(12,2) NOT NULL, AMOUNT DECIMAL(12,2) NOT NULL DEFAULT 0)");
			statement.execute("CREATE TABLE SHIPMENT_ALLOCATION (ID INT PRIMARY KEY, LINE_ID INT NOT NULL REFERENCES SALES_LINE(ID), ALLOCATED_QTY INT NOT NULL, AMOUNT DECIMAL(12,2) NOT NULL DEFAULT 0)");
			statement.executeUpdate("INSERT INTO SALES_ORDER(ID, STATUS) VALUES (1,'READY'),(2,'READY'),(3,'READY'),(4,'HOLD')");
			statement.executeUpdate("INSERT INTO SALES_LINE(ID, ORDER_ID, ORDERED_QTY, UNIT_PRICE) VALUES (11,1,3,100),(12,1,2,25),(21,2,3,10),(31,3,1,15),(41,4,1,999)");
			statement.executeUpdate("INSERT INTO SHIPMENT_ALLOCATION(ID, LINE_ID, ALLOCATED_QTY) VALUES (111,11,1),(112,11,2),(121,12,1),(122,12,1),(211,21,1),(212,21,2),(311,31,1),(411,41,1)");
		}
		connection.commit();
		return connection;
	}

	private List<String> query(Connection connection, String sql) throws SQLException {
		List<String> values = new ArrayList<>();
		try (Statement statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			while (rows.next()) {
				List<String> columns = new ArrayList<>();
				for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
					columns.add(rows.getString(i));
				}
				values.add(String.join(":", columns));
			}
		}
		return values;
	}
}
