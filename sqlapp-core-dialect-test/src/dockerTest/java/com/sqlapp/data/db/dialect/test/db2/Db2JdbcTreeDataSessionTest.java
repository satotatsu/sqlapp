/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.db2.Db2Container;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;

/** Db2 11.5 integration coverage for hierarchical JDBC batch writes. */
class Db2JdbcTreeDataSessionTest {
	private static final String IMAGE = "icr.io/db2_community/db2:11.5.8.0";
	private static final boolean REUSE = ReusableTestcontainers.isReuseEnabled();

	private static final Db2Container DB2 = new SqlReadyDb2Container(IMAGE, REUSE);

	@BeforeAll
	static void startContainer() {
		DB2.start();
	}

	@AfterAll
	static void stopContainer() {
		if (!REUSE) {
			DB2.stop();
		}
	}

	/** Rancher Desktop can miss the image's one-shot setup-complete log line. */
	private static final class SqlReadyDb2Container extends Db2Container {
		private SqlReadyDb2Container(final String image, final boolean reuse) {
			super(image);
			acceptLicense();
			withReuse(reuse);
		}

		@Override
		protected void waitUntilContainerStarted() {
			DriverManager.setLoginTimeout(2);
			final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
			SQLException lastException = null;
			while (System.nanoTime() < deadline) {
				try (Connection connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword());
						Statement statement = connection.createStatement();
						ResultSet resultSet = statement.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1")) {
					if (resultSet.next()) {
						return;
					}
				} catch (SQLException e) {
					lastException = e;
				}
				try {
					Thread.sleep(1000L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new ContainerLaunchException("Interrupted while waiting for Db2 readiness.", e);
				}
			}
			throw new ContainerLaunchException("Db2 did not accept JDBC connections within 10 minutes.", lastException);
		}
	}

	@Test
	void testBatchGeneratedKeysPropagateToMatchingChildren() throws SQLException {
		try (Connection connection = DB2.createConnection("")) {
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
				addParent(session, parent, "parent-5");
				addChild(session, child, "child-5");
				addParent(session, parent, "parent-6");
				addChild(session, child, "child-6");
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							WHERE p.txt IN ('parent-3', 'parent-4', 'parent-5', 'parent-6') ORDER BY p.id
							""")) {
				assertParentChild(resultSet, "parent-3", "child-3");
				assertParentChild(resultSet, "parent-4", "child-4");
				assertParentChild(resultSet, "parent-5", "child-5");
				assertParentChild(resultSet, "parent-6", "child-6");
				assertFalse(resultSet.next());
			}
			connection.rollback();
		}
	}

	@Test
	void testByDefaultIdentityAcceptsExplicitValues() throws SQLException {
		try (Connection connection = DB2.createConnection("")) {
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
							SELECT p.id, c.parent_id FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							WHERE p.id IN (100, 200) ORDER BY p.id
							""")) {
				assertIdentityPair(resultSet, 100L);
				assertIdentityPair(resultSet, 200L);
				assertFalse(resultSet.next());
			}
			connection.rollback();
		}
	}

	@Test
	void testByDefaultIdentityRejectsMixedExplicitAndGeneratedValues() throws SQLException {
		try (Connection connection = DB2.createConnection("")) {
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
			connection.rollback();
		}
	}

	@Test
	void testAlwaysIdentityRejectsExplicitValue() throws SQLException {
		try (Connection connection = DB2.createConnection("")) {
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
			connection.rollback();
		}
	}

	private Schema loadSchema(final Connection connection) throws SQLException {
		String schemaName = DB2.getUsername().toUpperCase(Locale.ROOT);
		return SchemaUtils.getSchema(connection, schemaName, "PARENT_TABLE", "CHILD_TABLE")
				.orElseThrow(() -> new AssertionError("Db2 test schema was not loaded."));
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

	private void createTables(final Connection connection, final String identityGeneration) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			dropTable(statement, "CHILD_TABLE");
			dropTable(statement, "PARENT_TABLE");
			statement.execute("""
					CREATE TABLE parent_table (
						id BIGINT NOT NULL GENERATED %s AS IDENTITY PRIMARY KEY,
						txt VARCHAR(256)
					)""".formatted(identityGeneration));
			statement.execute("""
					CREATE TABLE child_table (
						id BIGINT NOT NULL GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						parent_id BIGINT NOT NULL,
						txt VARCHAR(256),
						CONSTRAINT fk_child_parent FOREIGN KEY (parent_id)
							REFERENCES parent_table(id)
					)""");
			statement.execute("INSERT INTO parent_table(txt) VALUES ('seed-1')");
			statement.execute("INSERT INTO parent_table(txt) VALUES ('seed-2')");
			connection.commit();
		}
	}

	private void dropTable(final Statement statement, final String tableName) throws SQLException {
		try {
			statement.execute("DROP TABLE " + tableName);
		} catch (SQLException e) {
			if (!"42704".equals(e.getSQLState())) {
				throw e;
			}
		}
	}
}
