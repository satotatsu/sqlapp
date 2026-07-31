/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** PostgreSQL integration coverage for hierarchical JDBC batch writes. */
@Testcontainers
class PostgresJdbcTreeDataSessionTest {
	private static final String IMAGE = "postgres:18.4";

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE);

	@Test
	void testBatchGeneratedKeysPropagateToMatchingChildren() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);

				Row firstParent = session.newRow(parent);
				firstParent.put("txt", "parent-3");
				Row firstChild = session.newRow(child);
				firstChild.put("txt", "child-3");

				Row secondParent = session.newRow(parent);
				secondParent.put("txt", "parent-4");
				Row secondChild = session.newRow(child);
				secondChild.put("txt", "child-4");
			}

			assertEquals(4, count(connection, "parent_table"));
			assertEquals(2, count(connection, "child_table"));
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							ORDER BY p.id
							""")) {
				assertParentChild(resultSet, "parent-3", "child-3");
				assertParentChild(resultSet, "parent-4", "child-4");
				assertFalse(resultSet.next());
			}
		}
	}

	@Test
	void testSelectCursorRemainsUsableDuringHierarchicalInsert() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (Statement statement = connection.createStatement(
					ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY,
					ResultSet.HOLD_CURSORS_OVER_COMMIT)) {
				statement.setFetchSize(1);
				try (ResultSet cursor = statement.executeQuery(
						"SELECT id, txt FROM parent_table ORDER BY id")) {
					assertTrue(cursor.next());

					try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
						session.setRootBatchSize(1);
						session.setTableOperationMode(TableOperationMode.INSERT);
						Row parentRow = session.newRow(parent);
						parentRow.put("txt", "parent-3");
						Row childRow = session.newRow(child);
						childRow.put("txt", "child-3");
					}

					assertTrue(cursor.next(),
							"The open SELECT cursor must remain usable after a hierarchical batch INSERT.");
				}
			}

			assertEquals(3, count(connection, "parent_table"));
			assertEquals(1, count(connection, "child_table"));
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("SELECT parent_id FROM child_table")) {
				assertTrue(resultSet.next());
				assertTrue(resultSet.getLong(1) > 0);
			}
		}
	}

	private Schema loadPublicSchema(Connection connection) throws SQLException {
		return SchemaUtils.getSchema(connection, "public")
				.orElseThrow(() -> new AssertionError("PostgreSQL public schema was not loaded."));
	}

	private void assertParentChild(ResultSet resultSet, String parentText, String childText) throws SQLException {
		assertTrue(resultSet.next());
		assertEquals(parentText, resultSet.getString(1));
		assertEquals(childText, resultSet.getString(2));
	}

	private void createTables(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS child_table");
			statement.execute("DROP TABLE IF EXISTS parent_table");
			statement.execute("""
					CREATE TABLE parent_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						txt VARCHAR(256)
					)""");
			statement.execute("""
					CREATE TABLE child_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						parent_id BIGINT NOT NULL,
						txt VARCHAR(256),
						CONSTRAINT fk_child_parent FOREIGN KEY (parent_id)
							REFERENCES parent_table(id)
					)""");
			statement.execute("""
					INSERT INTO parent_table(txt)
					VALUES ('parent-1'), ('parent-2')
					""");
			connection.commit();
		}
	}

	private int count(Connection connection, String tableName) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}
}
