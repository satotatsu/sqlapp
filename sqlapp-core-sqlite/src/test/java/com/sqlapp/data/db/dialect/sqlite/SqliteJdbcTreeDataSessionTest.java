/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** SQLite integration coverage for multi-row RETURNING key propagation. */
class SqliteJdbcTreeDataSessionTest {

	@Test
	void testMultiRowReturningPropagatesKeysAndPreparedStatementsAreReused() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Table[] tables = createTableModels(connection);
			Table parent = tables[0];
			Table child = tables[1];
			Set<PreparedStatement> statements = Collections.newSetFromMap(new IdentityHashMap<>());
			AtomicInteger executions = new AtomicInteger();

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(3);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setPreparedStatementBeforeExecuteHandler(statement -> {
					statements.add(statement);
					executions.incrementAndGet();
				});
				for (int i = 1; i <= 6; i++) {
					Row parentRow = session.newRow(parent);
					parentRow.put("txt", "parent-" + i);
					Row childRow = session.newRow(child);
					childRow.put("txt", "child-" + i);
				}
			}

			assertEquals(2, statements.size());
			assertEquals(4, executions.get());
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.id, p.txt, c.parent_id, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							ORDER BY p.id
							""")) {
				for (long i = 1; i <= 6; i++) {
					assertTrue(resultSet.next());
					assertEquals(i, resultSet.getLong(1));
					assertEquals("parent-" + i, resultSet.getString(2));
					assertEquals(i, resultSet.getLong(3));
					assertEquals("child-" + i, resultSet.getString(4));
				}
				assertFalse(resultSet.next());
			}
		}
	}

	private Table[] createTableModels(final Connection connection) {
		Table parent = new Table("parent_table");
		parent.setDialect(DialectResolver.getInstance().getDialect(connection));
		Column parentId = new Column("id").setDataType(DataType.BIGINT).setIdentity(true);
		parent.getColumns().add(parentId);
		parent.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
		parent.getConstraints().addPrimaryKeyConstraint("pk_parent", parentId);

		Table child = new Table("child_table");
		child.setDialect(parent.getDialect());
		Column childId = new Column("id").setDataType(DataType.BIGINT).setIdentity(true);
		Column parentIdFk = new Column("parent_id").setDataType(DataType.BIGINT).setNullable(false);
		child.getColumns().add(childId);
		child.getColumns().add(parentIdFk);
		child.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR));
		child.getConstraints().addPrimaryKeyConstraint("pk_child", childId);
		child.getConstraints().addForeignKeyConstraint("fk_child_parent", parentIdFk, parentId);
		return new Table[] { parent, child };
	}

	private void createTables(final Connection connection) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE parent_table (id INTEGER PRIMARY KEY, txt TEXT)");
			statement.execute("""
					CREATE TABLE child_table (
						id INTEGER PRIMARY KEY,
						parent_id INTEGER NOT NULL REFERENCES parent_table(id),
						txt TEXT
					)
					""");
			connection.commit();
		}
	}
}
