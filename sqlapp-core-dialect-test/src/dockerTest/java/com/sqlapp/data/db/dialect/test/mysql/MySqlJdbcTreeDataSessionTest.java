/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** MySQL 8.4 integration coverage for hierarchical JDBC batch writes. */
class MySqlJdbcTreeDataSessionTest {
	private static final String IMAGE = "mysql:8.4";

	private static final MySQLContainer MYSQL =
			ReusableTestcontainers.configure(new MySQLContainer(IMAGE));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MYSQL);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MYSQL);
	}

	@Test
	void testCommitEveryRootBatchControlsCrossConnectionVisibility() throws SQLException {
		try (Connection writer = MYSQL.createConnection("");
				Connection observer = MYSQL.createConnection("")) {
			writer.setAutoCommit(false);
			createTables(writer);
			Schema schema = loadSchema(writer);
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

				addParent(session, parent, "parent-5");
				assertEquals(4, count(observer, "parent_table"));
				assertEquals(2, count(observer, "child_table"));
				addChild(session, child, "child-5");
			}

			assertEquals(5, count(observer, "parent_table"));
			assertEquals(3, count(observer, "child_table"));
		}
	}

	@Test
	void testCloseCommitsFinalPartialBatch() throws SQLException {
		try (Connection connection = MYSQL.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(10);
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
	void testSelectResultSetRemainsUsableDuringHierarchicalInsert() throws SQLException {
		try (Connection connection = MYSQL.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY);
					ResultSet resultSet = statement.executeQuery("SELECT id, txt FROM parent_table ORDER BY id")) {
				assertTrue(resultSet.next());
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
					session.setRootBatchSize(1);
					session.setTableOperationMode(TableOperationMode.INSERT);
					addParent(session, parent, "parent-3");
					addChild(session, child, "child-3");
				}
				assertTrue(resultSet.next());
			}
		}
	}

	@Test
	void testAutoIncrementAcceptsExplicitValues() throws SQLException {
		try (Connection connection = MYSQL.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				Row first = addParent(session, parent, "parent-100");
				first.put("id", 100L);
				addChild(session, child, "child-100");
				Row second = addParent(session, parent, "parent-200");
				second.put("id", 200L);
				addChild(session, child, "child-200");
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.id, c.parent_id
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							WHERE p.id IN (100, 200)
							ORDER BY p.id
							""")) {
				assertIdentityPair(resultSet, 100L);
				assertIdentityPair(resultSet, 200L);
				assertFalse(resultSet.next());
			}
		}
	}

	@Test
	void testAutoIncrementRejectsMixedExplicitAndGeneratedValues() throws SQLException {
		try (Connection connection = MYSQL.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
					session.setRootBatchSize(2);
					session.setTableOperationMode(TableOperationMode.INSERT);
					addParent(session, parent, "generated-parent");
					Row explicit = addParent(session, parent, "explicit-parent");
					explicit.put("id", 100L);
				}
			});

			assertTrue(exception.getMessage().contains("cannot mix"));
		}
	}

	@Test
	void testBatchGeneratedKeysPropagateToMatchingChildren() throws SQLException {
		try (Connection connection = MYSQL.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				addParent(session, parent, "parent-3");
				addChild(session, child, "child-3");
				addParent(session, parent, "parent-4");
				addChild(session, child, "child-4");
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							WHERE p.txt IN ('parent-3', 'parent-4')
							ORDER BY p.id
							""")) {
				assertParentChild(resultSet, "parent-3", "child-3");
				assertParentChild(resultSet, "parent-4", "child-4");
				assertFalse(resultSet.next());
			}
		}
	}

	@Test
	void testGeneratedKeysRemainAlignedAcrossRootBatches() throws SQLException {
		try (Connection connection = MYSQL.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");
			Set<PreparedStatement> statements = Collections.newSetFromMap(new IdentityHashMap<>());
			AtomicInteger executions = new AtomicInteger();

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setPreparedStatementBeforeExecuteHandler(statement -> {
					statements.add(statement);
					executions.incrementAndGet();
				});
				for (int i = 3; i <= 8; i++) {
					addParent(session, parent, "parent-" + i);
					addChild(session, child, "child-" + i);
				}
			}
			assertEquals(2, statements.size());
			assertEquals(6, executions.get());

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							ORDER BY p.id
							""")) {
				for (int i = 3; i <= 8; i++) {
					assertParentChild(resultSet, "parent-" + i, "child-" + i);
				}
				assertFalse(resultSet.next());
			}
		}
	}

	@Test
	void testGeneratedKeysRemainAlignedWithUnevenChildCounts() throws SQLException {
		try (Connection connection = MYSQL.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
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

	private Schema loadSchema(final Connection connection) throws SQLException {
		return SchemaUtils.getSchema(connection, MYSQL.getDatabaseName(), "parent_table", "child_table")
				.orElseThrow(() -> new AssertionError("MySQL test schema was not loaded."));
	}

	private Row addParent(final JdbcTreeDataSession session, final Table parent, final String text) throws SQLException {
		Row row = session.newRow(parent);
		row.put("txt", text);
		return row;
	}

	private Row addChild(final JdbcTreeDataSession session, final Table child, final String text) throws SQLException {
		Row row = session.newRow(child);
		row.put("txt", text);
		return row;
	}

	private void assertParentChild(final ResultSet resultSet, final String parentText, final String childText)
			throws SQLException {
		assertTrue(resultSet.next());
		assertEquals(parentText, resultSet.getString(1));
		assertEquals(childText, resultSet.getString(2));
	}

	private void assertIdentityPair(final ResultSet resultSet, final long identity) throws SQLException {
		assertTrue(resultSet.next());
		assertEquals(identity, resultSet.getLong(1));
		assertEquals(identity, resultSet.getLong(2));
	}

	private int count(final Connection connection, final String tableName) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	private void createTables(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS child_table");
			statement.execute("DROP TABLE IF EXISTS parent_table");
			statement.execute("""
					CREATE TABLE parent_table (
						id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
						txt VARCHAR(256)
					)""");
			statement.execute("""
					CREATE TABLE child_table (
						id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
						parent_id BIGINT NOT NULL,
						txt VARCHAR(256),
						CONSTRAINT fk_child_parent FOREIGN KEY (parent_id)
							REFERENCES parent_table(id)
					)""");
			statement.execute("INSERT INTO parent_table(txt) VALUES ('seed-1'), ('seed-2')");
			connection.commit();
		}
	}
}
