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
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Oracle Database Free integration coverage for hierarchical JDBC writes. */
@Testcontainers
class OracleJdbcTreeDataSessionTest {
	private static final String IMAGE = "gvenzl/oracle-free:23-slim-faststart";

	@Container
	private static final OracleContainer ORACLE = new OracleContainer(IMAGE);

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
				session.setTableOperationMode(TableOperationMode.INSERT);
				Row row = addParent(session, parent, "parent-100");
				row.put("ID", 100L);
				addChild(session, child, "child-100");
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.id, c.parent_id
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							WHERE p.id = 100
							""")) {
				assertTrue(resultSet.next());
				assertEquals(100L, resultSet.getLong(1));
				assertEquals(100L, resultSet.getLong(2));
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
