/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
	void testAlwaysIdentityRejectsExplicitValues() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "ALWAYS");
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
					session.setTableOperationMode(TableOperationMode.INSERT);
					Row row = addParent(session, parent, "parent-100");
					row.put("id", 100L);
				}
			});
			assertTrue(exception.getMessage().contains("GENERATED ALWAYS"));
			connection.rollback();
			assertEquals(2, count(connection, "parent_table"));
		}
	}

	@Test
	void testByDefaultIdentityRejectsMixedExplicitAndGeneratedValues() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
					session.setRootBatchSize(2);
					session.setTableOperationMode(TableOperationMode.INSERT);
					addParent(session, parent, "generated-parent");
					Row explicitParent = addParent(session, parent, "explicit-parent");
					explicitParent.put("id", 100L);
				}
			});
			assertTrue(exception.getMessage().contains("cannot mix"));
			connection.rollback();
			assertEquals(2, count(connection, "parent_table"));
		}
	}

	@Test
	void testExplicitByDefaultIdentityValuesPropagateToChildren() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				Row firstParent = addParent(session, parent, "parent-100");
				firstParent.put("id", 100L);
				addChild(session, child, "child-100");
				Row secondParent = addParent(session, parent, "parent-200");
				secondParent.put("id", 200L);
				addChild(session, child, "child-200");
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.id, c.parent_id, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							WHERE p.id IN (100, 200)
							ORDER BY p.id
							""")) {
				assertIdentityChild(resultSet, 100L, "child-100");
				assertIdentityChild(resultSet, 200L, "child-200");
				assertFalse(resultSet.next());
			}
		}
	}

	@Test
	void testCommitEveryRootBatchControlsCrossConnectionVisibility() throws SQLException {
		try (Connection writer = POSTGRES.createConnection("");
				Connection observer = POSTGRES.createConnection("")) {
			writer.setAutoCommit(false);
			createTables(writer);
			Schema schema = loadPublicSchema(writer);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(writer, parent, child)) {
				session.setRootBatchSize(2);
				session.setCommitEveryRootBatches(1);
				session.setTableOperationMode(TableOperationMode.INSERT);
				addParent(session, parent, "parent-3");
				addChild(session, child, "child-3");
				addParent(session, parent, "parent-4");
				addChild(session, child, "child-4");

				// Starting the next root flushes and commits the completed two-row batch.
				addParent(session, parent, "parent-5");
				assertEquals(4, count(observer, "parent_table"));
				assertEquals(2, count(observer, "child_table"));
				addChild(session, child, "child-5");
				assertEquals(4, count(observer, "parent_table"));
				assertEquals(2, count(observer, "child_table"));
			}

			assertEquals(5, count(observer, "parent_table"));
			assertEquals(3, count(observer, "child_table"));
		}
	}

	@Test
	void testCloseCommitsFinalPartialBatch() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(1);
				session.setTableOperationMode(TableOperationMode.INSERT);
				addParent(session, parent, "parent-3");
				addChild(session, child, "child-3");
			}

			assertEquals(3, count(connection, "parent_table"));
			assertEquals(1, count(connection, "child_table"));
			connection.rollback();
			assertEquals(3, count(connection, "parent_table"));
			assertEquals(1, count(connection, "child_table"));
		}
	}

	@Test
	void testGeneratedKeysRemainAlignedAcrossRootBatches() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				for (int i = 3; i <= 7; i++) {
					addParent(session, parent, "parent-" + i);
					addChild(session, child, "child-" + i);
				}
			}

			assertEquals(7, count(connection, "parent_table"));
			assertEquals(5, count(connection, "child_table"));
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							ORDER BY p.id
							""")) {
				for (int i = 3; i <= 7; i++) {
					assertParentChild(resultSet, "parent-" + i, "child-" + i);
				}
				assertFalse(resultSet.next());
			}
		}
	}

	@Test
	void testGeneratedKeysRemainAlignedWithUnevenChildCounts() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadPublicSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(3);
				session.setTableOperationMode(TableOperationMode.INSERT);

				addParent(session, parent, "parent-3");
				addChild(session, child, "child-3a");
				addChild(session, child, "child-3b");
				addParent(session, parent, "parent-4");
				addParent(session, parent, "parent-5");
				addChild(session, child, "child-5");
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt
							FROM parent_table p
							LEFT JOIN child_table c ON c.parent_id = p.id
							WHERE p.txt IN ('parent-3', 'parent-4', 'parent-5')
							ORDER BY p.id, c.id
							""")) {
				assertParentChild(resultSet, "parent-3", "child-3a");
				assertParentChild(resultSet, "parent-3", "child-3b");
				assertParentChild(resultSet, "parent-4", null);
				assertParentChild(resultSet, "parent-5", "child-5");
				assertFalse(resultSet.next());
			}
		}
	}

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

	private Row addParent(JdbcTreeDataSession session, Table parent, String text) throws SQLException {
		Row row = session.newRow(parent);
		row.put("txt", text);
		return row;
	}

	private Row addChild(JdbcTreeDataSession session, Table child, String text) throws SQLException {
		Row row = session.newRow(child);
		row.put("txt", text);
		return row;
	}

	private void assertParentChild(ResultSet resultSet, String parentText, String childText) throws SQLException {
		assertTrue(resultSet.next());
		assertEquals(parentText, resultSet.getString(1));
		assertEquals(childText, resultSet.getString(2));
	}

	private void assertIdentityChild(ResultSet resultSet, long parentId, String childText) throws SQLException {
		assertTrue(resultSet.next());
		assertEquals(parentId, resultSet.getLong(1));
		assertEquals(parentId, resultSet.getLong(2));
		assertEquals(childText, resultSet.getString(3));
	}

	private void createTables(Connection connection) throws SQLException {
		createTables(connection, "BY DEFAULT");
	}

	private void createTables(Connection connection, String identityGeneration) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS child_table");
			statement.execute("DROP TABLE IF EXISTS parent_table");
			statement.execute("""
					CREATE TABLE parent_table (
						id BIGINT GENERATED %s AS IDENTITY PRIMARY KEY,
						txt VARCHAR(256)
					)""".formatted(identityGeneration));
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
