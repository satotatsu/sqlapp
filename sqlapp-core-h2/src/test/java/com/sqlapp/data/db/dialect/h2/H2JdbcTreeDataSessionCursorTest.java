/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.dialect.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

class H2JdbcTreeDataSessionCursorTest {

	@Test
	void changingParentDiscardsUnreadChildrenAndHandlesEmptyParents() throws Exception {
		try (Connection connection = database()) {
			var schema = SchemaUtils.getSchema(connection, "PUBLIC", "P", "A").orElseThrow();
			var parent = schema.getTables().get("P");
			var child = schema.getTables().get("A");
			new JdbcTreeDataSession(connection, parent, child).execute(session -> {
				session.setTableOperationMode(TableOperationMode.NONE);
				session.setRootBatchSize(4);
				session.select(child);
				session.select(parent, "SELECT * FROM P ORDER BY ID");
				for (int id = 1; id <= 4; id++) {
					assertTrue(session.next(parent));
					assertEquals(id, ((Number) session.getRow(parent).get("ID")).intValue());
					if (id == 3) {
						assertFalse(session.next(child));
						assertFalse(session.next(child));
					} else {
						assertTrue(session.next(child));
						assertTrue(session.next(child)); // next() alone does not consume the row.
						assertEquals(id, ((Number) session.getRow(child).get("PID")).intValue());
						// Leave the other child unread, as when business logic breaks a loop.
					}
				}
				assertFalse(session.next(parent));
			});
		}
	}

	@Test
	void siblingCustomSelectsStayIndependentAcrossBatchesAndUpdates() throws Exception {
		try (Connection connection = database()) {
			var schema = SchemaUtils.getSchema(connection, "PUBLIC", "P", "A", "B").orElseThrow();
			var parent = schema.getTables().get("P");
			var a = schema.getTables().get("A");
			var b = schema.getTables().get("B");
			List<Integer> ids = new ArrayList<>();
			new JdbcTreeDataSession(connection, parent, a, b).execute(session -> {
				session.setTableOperationMode(TableOperationMode.NONE);
				session.setRootBatchSize(2);
				session.select(a, "SELECT * FROM A ORDER BY ID");
				session.select(b, "SELECT * FROM B ORDER BY ID");
				session.select(parent, "SELECT * FROM P ORDER BY ID");
				while (session.next(parent)) {
					session.getRow(parent);
					while (session.next(a)) {
						session.getRow(a);
					}
					while (session.next(b)) {
						var row = session.getRow(b);
						ids.add(((Number) row.get("ID")).intValue());
						row.put("TXT", "processed");
						row.update();
					}
				}
			});
			assertEquals(List.of(101, 201, 401), ids);
			try (Statement statement = connection.createStatement();
					var rows = statement.executeQuery(
							"SELECT (SELECT COUNT(*) FROM B WHERE TXT='processed'), (SELECT COUNT(*) FROM A WHERE TXT='processed')")) {
				assertTrue(rows.next());
				assertEquals(3, rows.getInt(1));
				assertEquals(0, rows.getInt(2));
			}
		}
	}

	@Test
	void childContextIsUsedAndReboundWhenStatementIsReused() throws Exception {
		try (Connection connection = database()) {
			var schema = SchemaUtils.getSchema(connection, "PUBLIC", "P", "A").orElseThrow();
			var parent = schema.getTables().get("P");
			var child = schema.getTables().get("A");
			Map<String, Object> context = new HashMap<>(Map.of("minimum", 12));
			List<Integer> ids = new ArrayList<>();
			new JdbcTreeDataSession(connection, parent, child).execute(session -> {
				session.setTableOperationMode(TableOperationMode.NONE);
				session.setRootBatchSize(2);
				session.select(child, "SELECT * FROM A WHERE ID >= /*minimum*/0 ORDER BY ID", context);
				session.select(parent, "SELECT * FROM P ORDER BY ID");
				while (session.next(parent)) {
					int id = ((Number) session.getRow(parent).get("ID")).intValue();
					while (session.next(child)) {
						ids.add(((Number) session.getRow(child).get("ID")).intValue());
					}
					if (id == 2) {
						context.put("minimum", 42);
					}
				}
			});
			assertEquals(List.of(12, 21, 22, 42), ids);
		}
	}

	private Connection database() throws Exception {
		Connection connection = DriverManager.getConnection("jdbc:h2:mem:cursor-" + UUID.randomUUID());
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE P(ID INT PRIMARY KEY)");
			statement.execute("CREATE TABLE A(ID INT PRIMARY KEY, PID INT REFERENCES P(ID), TXT VARCHAR(32))");
			statement.execute("CREATE TABLE B(ID INT PRIMARY KEY, PID INT REFERENCES P(ID), TXT VARCHAR(32))");
			statement.execute("INSERT INTO P VALUES(1),(2),(3),(4)");
			statement.execute("INSERT INTO A(ID, PID) VALUES(11,1),(12,1),(21,2),(22,2),(41,4),(42,4)");
			statement.execute("INSERT INTO B(ID, PID) VALUES(101,1),(201,2),(401,4)");
		}
		connection.commit();
		return connection;
	}
}
