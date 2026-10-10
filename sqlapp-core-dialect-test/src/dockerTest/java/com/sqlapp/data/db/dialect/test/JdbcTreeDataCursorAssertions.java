/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/**
 * Shared assertions against disposable real-database fixtures with two initial
 * parents.
 */
public final class JdbcTreeDataCursorAssertions {
	private JdbcTreeDataCursorAssertions() {
	}

	public static void verifyPeriodicCommitCursor(Connection connection, Table parent, Table child)
			throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate("INSERT INTO " + parent.getName() + "(TXT) VALUES ('parent-3')");
			for (int i = 0; i < 2; i++) {
				statement.executeUpdate("INSERT INTO " + child.getName() + "(PARENT_ID, TXT) SELECT ID, 'child' FROM "
						+ parent.getName());
			}
		}
		connection.commit();
		String parentId = parent.getColumns().stream().filter(c -> c.getName().equalsIgnoreCase("ID")).findFirst()
				.orElseThrow().getName();
		String parentText = parent.getColumns().stream().filter(c -> c.getName().equalsIgnoreCase("TXT")).findFirst()
				.orElseThrow().getName();
		String childParentId = child.getColumns().stream().filter(c -> c.getName().equalsIgnoreCase("PARENT_ID"))
				.findFirst().orElseThrow().getName();
		String childText = child.getColumns().stream().filter(c -> c.getName().equalsIgnoreCase("TXT")).findFirst()
				.orElseThrow().getName();
		int[] visited = new int[2];
		var result = new JdbcTreeDataSession(connection, parent, child).execute(session -> {
			session.setRootBatchSize(2);
			session.setCommitEveryRootBatches(1);
			session.setTableOperationMode(TableOperationMode.NONE);
			session.select(child);
			session.select(parent, "SELECT * FROM " + parent.getName() + " ORDER BY ID");
			while (session.next(parent)) {
				var parentRow = session.getRow(parent);
				visited[0]++;
				while (session.next(child)) {
					var childRow = session.getRow(child);
					assertEquals(((Number) parentRow.get(parentId)).longValue(),
							((Number) childRow.get(childParentId)).longValue());
					visited[1]++;
					childRow.put(childText, "processed");
					childRow.update();
				}
				parentRow.put(parentText, "processed");
				parentRow.update();
			}
		});
		assertTrue(result.successful());
		assertEquals(3, visited[0]);
		assertEquals(6, visited[1]);
		assertEquals(2, result.completedRootBatches());
		assertEquals(2, result.commits());
		for (Table table : new Table[] { parent, child }) {
			try (Statement statement = connection.createStatement();
					var rows = statement
							.executeQuery("SELECT COUNT(*) FROM " + table.getName() + " WHERE TXT='processed'")) {
				assertTrue(rows.next());
				assertEquals(table == parent ? 3 : 6, rows.getInt(1));
			}
		}
	}
}
