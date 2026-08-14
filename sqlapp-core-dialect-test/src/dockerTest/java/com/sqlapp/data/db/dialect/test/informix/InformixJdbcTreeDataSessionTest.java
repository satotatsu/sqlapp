/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.informix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Informix 14.10 integration coverage for JDBC batch generated keys. */
class InformixJdbcTreeDataSessionTest {
	private static final GenericContainer<?> INFORMIX = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse(
					"icr.io/informix/informix-developer-database:14.10.FC9W1DE"))
					.withPrivilegedMode(true)
					.withEnv("LICENSE", "accept")
					.withEnv("STORAGE", "local")
					.withEnv("SIZE", "small")
					.withExposedPorts(9088)
					.waitingFor(Wait.forLogMessage(".*'sysadmin' database built successfully.*\\n", 1)
							.withStartupTimeout(Duration.ofMinutes(3))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(INFORMIX);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(INFORMIX);
	}

	@Test
	void testGeneratedKeysRemainAlignedAndPreparedStatementsAreReused() throws SQLException {
		try (Connection connection = createConnection()) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = SchemaUtils.getSchema(connection, "informix", "parent_table", "child_table")
					.orElseThrow(() -> new AssertionError("Informix test schema was not loaded."));
			Table parent = schema.getTables().get("parent_table");
			Table child = schema.getTables().get("child_table");
			assertTrue(parent.getColumns().get("id").isIdentity(), () -> "dialect="
					+ schema.getDialect().getClass().getName() + ", dataType="
					+ parent.getColumns().get("id").getDataType() + ", dataTypeName="
					+ parent.getColumns().get("id").getDataTypeName());
			assertEquals(DataType.SERIAL, parent.getColumns().get("id").getDataType());
			assertEquals(IdentityGenerationType.ByDefault,
					parent.getColumns().get("id").getIdentityGenerationType());
			assertEquals("id", parent.getConstraints().getPrimaryKeyConstraint()
					.getColumns().get(0).getName());
			ForeignKeyConstraint foreignKey = child.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			Set<PreparedStatement> statements = Collections.newSetFromMap(new IdentityHashMap<>());
			AtomicInteger executions = new AtomicInteger();

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
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
			assertEquals(6, executions.get());
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							ORDER BY p.id
							""")) {
				for (int i = 1; i <= 6; i++) {
					assertTrue(resultSet.next());
					assertEquals("parent-" + i, resultSet.getString(1));
					assertEquals("child-" + i, resultSet.getString(2));
				}
				assertFalse(resultSet.next());
			}
		}
	}

	private Connection createConnection() throws SQLException {
		String url = "jdbc:informix-sqli://localhost:" + INFORMIX.getMappedPort(9088)
				+ "/sysmaster:INFORMIXSERVER=informix;DELIMIDENT=Y";
		return DriverManager.getConnection(url, "informix", "in4mix");
	}

	private void createTables(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			dropTable(statement, "child_table");
			dropTable(statement, "parent_table");
			statement.execute("""
					CREATE TABLE parent_table (
						id SERIAL PRIMARY KEY,
						txt VARCHAR(255)
					)
					""");
			statement.execute("""
					CREATE TABLE child_table (
						id SERIAL PRIMARY KEY,
						parent_id INTEGER NOT NULL,
						txt VARCHAR(255),
						FOREIGN KEY (parent_id)
							REFERENCES parent_table(id)
					)
					""");
			connection.commit();
		}
	}

	private void dropTable(final Statement statement, final String tableName) throws SQLException {
		try {
			statement.execute("DROP TABLE " + tableName);
		} catch (SQLException e) {
			if (e.getErrorCode() != -206) {
				throw e;
			}
		}
	}
}
