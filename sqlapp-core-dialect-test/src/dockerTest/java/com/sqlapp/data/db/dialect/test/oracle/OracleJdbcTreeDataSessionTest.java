/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.oracle.OracleContainer;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Oracle Database Free integration coverage for hierarchical JDBC writes. */
class OracleJdbcTreeDataSessionTest {
	private static final String IMAGE = "gvenzl/oracle-free:23-slim-faststart";

	private static final OracleContainer ORACLE =
			ReusableTestcontainers.configure(new OracleContainer(IMAGE));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ORACLE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ORACLE);
	}

	@Test
	void testCommitEveryRootBatchControlsCrossConnectionVisibility() throws SQLException {
		try (Connection writer = ORACLE.createConnection("");
				Connection observer = ORACLE.createConnection("")) {
			writer.setAutoCommit(false);
			createTables(writer, "BY DEFAULT");
			Schema schema = loadSchema(writer);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");
			assertTrue(parent.getColumns().get("ID").getSequenceName().startsWith("ISEQ$$_"));

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(writer, parent, child)) {
				session.setRootBatchSize(2);
				session.setCommitEveryRootBatches(1);
				session.setTableOperationMode(TableOperationMode.INSERT);
				addParent(session, parent, "parent-3");
				addChild(session, child, "child-3");
				addParent(session, parent, "parent-4");
				addChild(session, child, "child-4");

				addParent(session, parent, "parent-5");
				assertEquals(4, count(observer, "PARENT_TABLE"));
				assertEquals(2, count(observer, "CHILD_TABLE"));
				addChild(session, child, "child-5");
			}

			assertEquals(5, count(observer, "PARENT_TABLE"));
			assertEquals(3, count(observer, "CHILD_TABLE"));
		}
	}

	@Test
	void testCloseCommitsFinalPartialBatch() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "BY DEFAULT");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(10);
				session.setTableOperationMode(TableOperationMode.INSERT);
				addParent(session, parent, "parent-3");
				addChild(session, child, "child-3");
			}

			assertEquals(3, count(connection, "PARENT_TABLE"));
			assertEquals(1, count(connection, "CHILD_TABLE"));
			connection.rollback();
			assertEquals(3, count(connection, "PARENT_TABLE"));
			assertEquals(1, count(connection, "CHILD_TABLE"));
		}
	}

	@Test
	void testSelectCursorRemainsUsableDuringHierarchicalInsert() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "BY DEFAULT");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

			try (Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY)) {
				statement.setFetchSize(1);
				try (ResultSet cursor = statement.executeQuery("SELECT id, txt FROM parent_table ORDER BY id")) {
					assertTrue(cursor.next());

					try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
						session.setRootBatchSize(1);
						session.setTableOperationMode(TableOperationMode.INSERT);
						addParent(session, parent, "parent-3");
						addChild(session, child, "child-3");
					}

					assertTrue(cursor.next(),
							"The open Oracle SELECT cursor must remain usable after a hierarchical INSERT.");
				}
			}
		}
	}

	@Test
	void testGeneratedKeysRemainAlignedAcrossRootBatches() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "BY DEFAULT");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");
			Set<Object> preparedStatements = Collections.newSetFromMap(new IdentityHashMap<>());
			AtomicInteger executions = new AtomicInteger();

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setPreparedStatementBeforeExecuteHandler(statement -> {
					preparedStatements.add(statement);
					executions.incrementAndGet();
				});
				for (int i = 3; i <= 7; i++) {
					addParent(session, parent, "parent-" + i);
					addChild(session, child, "child-" + i);
				}
			}
			assertEquals(6, executions.get());
			assertEquals(4, preparedStatements.size(),
					"Two-row batches must reuse their prepared statements; only final one-row shapes differ.");

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
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "BY DEFAULT");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

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
	void testByDefaultIdentityRejectsMixedExplicitAndGeneratedValues() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "BY DEFAULT");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
					session.setRootBatchSize(2);
					session.setTableOperationMode(TableOperationMode.INSERT);
					addParent(session, parent, "generated-parent");
					Row explicit = addParent(session, parent, "explicit-parent");
					explicit.put("ID", 100L);
				}
			});

			assertTrue(exception.getMessage().contains("cannot mix"));
		}
	}

	@Test
	void testBatchGeneratedKeysPropagateToMatchingChildren() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "BY DEFAULT");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

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
	void testByDefaultIdentityAcceptsExplicitValues() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "BY DEFAULT");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				Row first = addParent(session, parent, "parent-100");
				first.put("ID", 100L);
				addChild(session, child, "child-100");
				Row second = addParent(session, parent, "parent-200");
				second.put("ID", 200L);
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
	void testAlwaysIdentityRejectsExplicitValue() throws SQLException {
		try (Connection connection = ORACLE.createConnection("")) {
			connection.setAutoCommit(false);
			createTables(connection, "ALWAYS");
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

			IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
					session.setTableOperationMode(TableOperationMode.INSERT);
					Row row = addParent(session, parent, "parent-100");
					row.put("ID", 100L);
				}
			});
			assertTrue(exception.getMessage().contains("GENERATED ALWAYS"));
		}
	}

	private Schema loadSchema(final Connection connection) throws SQLException {
		String schemaName = ORACLE.getUsername().toUpperCase(Locale.ROOT);
		return SchemaUtils.getSchema(connection, schemaName, "PARENT_TABLE", "CHILD_TABLE")
				.orElseThrow(() -> new AssertionError("Oracle test schema was not loaded."));
	}

	private Row addParent(final JdbcTreeDataSession session, final Table parent, final String text) throws SQLException {
		Row row = session.newRow(parent);
		row.put("TXT", text);
		return row;
	}

	private Row addChild(final JdbcTreeDataSession session, final Table child, final String text) throws SQLException {
		Row row = session.newRow(child);
		row.put("TXT", text);
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

	private void createTables(final Connection connection, final String identityGeneration) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			dropTable(statement, "CHILD_TABLE");
			dropTable(statement, "PARENT_TABLE");
			statement.execute("""
					CREATE TABLE parent_table (
						id NUMBER(19) GENERATED %s AS IDENTITY PRIMARY KEY,
						txt VARCHAR2(256)
					)""".formatted(identityGeneration));
			statement.execute("""
					CREATE TABLE child_table (
						id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						parent_id NUMBER(19) NOT NULL,
						txt VARCHAR2(256),
						CONSTRAINT fk_child_parent FOREIGN KEY (parent_id)
							REFERENCES parent_table(id)
					)""");
			statement.execute("""
					INSERT INTO parent_table(txt)
					SELECT 'parent-1' FROM dual UNION ALL
					SELECT 'parent-2' FROM dual
					""");
			connection.commit();
		}
	}

	private void dropTable(final Statement statement, final String tableName) throws SQLException {
		try {
			statement.execute("DROP TABLE " + tableName + " CASCADE CONSTRAINTS PURGE");
		} catch (SQLException e) {
			if (e.getErrorCode() != 942) {
				throw e;
			}
		}
	}
}
