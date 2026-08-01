/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.spanner;

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
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Cloud Spanner emulator coverage for multi-row DML THEN RETURN. */
class SpannerJdbcTreeDataSessionTest {
	private static final GenericContainer<?> SPANNER = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("gcr.io/cloud-spanner-emulator/emulator:latest"))
					.withExposedPorts(9010)
					.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(SPANNER);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SPANNER);
	}

	@Test
	void generatedIdentityRemainsAlignedAndPreparedStatementsAreReused() throws SQLException {
		try (Connection connection = createConnection()) {
			createTables(connection);
			connection.setAutoCommit(false);
			Table[] tables = createSchemaModel(connection);
			Table parent = tables[0];
			Table child = tables[1];
			assertTrue(parent.getColumns().get("id").isIdentity());
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
					childRow.put("id", (long) i);
					childRow.put("txt", "child-" + i);
				}
			}

			assertEquals(2, statements.size());
			assertEquals(6, executions.get());
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.id, p.txt, c.parent_id, c.txt
							FROM parent_table p JOIN child_table c ON c.parent_id=p.id
							ORDER BY p.txt
							""")) {
				for (int i = 1; i <= 6; i++) {
					assertTrue(resultSet.next());
					assertEquals(resultSet.getLong(1), resultSet.getLong(3));
					assertEquals("parent-" + i, resultSet.getString(2));
					assertEquals("child-" + i, resultSet.getString(4));
				}
				assertFalse(resultSet.next());
			}
		}
	}

	private Table[] createSchemaModel(final Connection connection) throws SQLException {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		Table parent = new Table("parent_table");
		parent.setDialect(dialect);
		parent.getColumns().add(new Column("id")
				.setDataType(DataType.BIGINT).setIdentity(true));
		parent.getColumns().add(new Column("txt")
				.setDataType(DataType.VARCHAR).setLength(30));
		parent.setPrimaryKey(parent.getColumns().get("id"));
		Table child = new Table("child_table");
		child.setDialect(dialect);
		child.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		child.getColumns().add(new Column("parent_id").setDataType(DataType.BIGINT));
		child.getColumns().add(new Column("txt")
				.setDataType(DataType.VARCHAR).setLength(30));
		child.setPrimaryKey(child.getColumns().get("id"));
		child.getConstraints().addForeignKeyConstraint("fk_child_parent",
				child.getColumns().get("parent_id"), parent.getColumns().get("id"));
		return new Table[] { parent, child };
	}

	private Connection createConnection() throws SQLException {
		String url = "jdbc:cloudspanner://localhost:" + SPANNER.getMappedPort(9010)
				+ "/projects/test-project/instances/test-instance/databases/test-db"
				+ ";usePlainText=true;autoConfigEmulator=true";
		return DriverManager.getConnection(url);
	}

	private void createTables(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("ALTER DATABASE `test-db` SET OPTIONS (default_sequence_kind='bit_reversed_positive')");
			statement.execute("""
					CREATE TABLE parent_table (
					  id INT64 GENERATED BY DEFAULT AS IDENTITY,
					  txt STRING(30)
					) PRIMARY KEY (id)
					""");
			statement.execute("""
					CREATE TABLE child_table (
					  id INT64 NOT NULL,
					  parent_id INT64 NOT NULL,
					  txt STRING(30),
					  CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent_table (id)
					) PRIMARY KEY (id)
					""");
		}
	}
}
