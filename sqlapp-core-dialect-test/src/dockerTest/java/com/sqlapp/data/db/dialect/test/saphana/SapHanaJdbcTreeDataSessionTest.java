/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.saphana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** SAP HANA 2.0 integration coverage for sequence-preallocated INSERT_ROWS. */
class SapHanaJdbcTreeDataSessionTest {
	private static final String PASSWORD = "HxeTest9xA";
	private static final String PASSWORD_JSON = "{\"master_password\":\"" + PASSWORD + "\"}";
	private static final Path PASSWORD_DIRECTORY = createPasswordDirectory();
	private static final GenericContainer<?> HANA = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("saplabs/hanaexpress:2.00.088.00.20251110.1"))
					.withFileSystemBind(PASSWORD_DIRECTORY.toString(), "/hana/mounts", BindMode.READ_WRITE)
					.withCommand("--passwords-url", "file:///hana/mounts/password.json", "--agree-to-sap-license",
							"--dont-check-system")
					.withExposedPorts(39041)
					.withCreateContainerCmdModifier(command -> command.withHostName("hxehost")
							.getHostConfig().withShmSize(1L << 30))
					.waitingFor(Wait.forLogMessage(".*Startup finished.*", 1)
							.withStartupTimeout(Duration.ofMinutes(15))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(HANA);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(HANA);
		try {
			Files.deleteIfExists(PASSWORD_DIRECTORY.resolve("password.json"));
			Files.deleteIfExists(PASSWORD_DIRECTORY);
		} catch (Exception e) {
			// HANA normally removes password.json itself; temporary cleanup is best effort.
		}
	}

	private static Path createPasswordDirectory() {
		try {
			Path directory = Files.createTempDirectory("sqlapp-hanaexpress-");
			Files.writeString(directory.resolve("password.json"), PASSWORD_JSON, StandardCharsets.UTF_8);
			return directory;
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@Test
	void testSequencePreallocationPropagatesKeysToChildren() throws SQLException {
		try (Connection connection = createConnection()) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");
			assertTrue(parent.getColumns().get("ID").isIdentity());
			assertEquals(IdentityGenerationType.ByDefault,
					parent.getColumns().get("ID").getIdentityGenerationType());
			Sequence sequence = new Sequence("PARENT_SEQ");
			schema.getSequences().add(sequence);
			assertNotNull(schema.getSequences().get("PARENT_SEQ"));
			parent.getColumns().get("ID").setSequenceName("PARENT_SEQ");
			schema.getSequences().add(new Sequence("CHILD_SEQ"));
			child.getColumns().get("ID").setSequenceName("CHILD_SEQ");

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(3);
				session.setTableOperationMode(TableOperationMode.INSERT);
				for (int i = 1; i <= 5; i++) {
					addParent(session, parent, "parent-" + i);
					addChild(session, child, "child-" + i);
				}
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.id, p.txt, c.parent_id, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							ORDER BY p.id
							""")) {
				for (int i = 1; i <= 5; i++) {
					assertTrue(resultSet.next());
					assertEquals(1000L + i, resultSet.getLong(1));
					assertEquals("parent-" + i, resultSet.getString(2));
					assertEquals(resultSet.getLong(1), resultSet.getLong(3));
					assertEquals("child-" + i, resultSet.getString(4));
				}
				assertFalse(resultSet.next());
			}
		}
	}

	@Test
	void testGeneratedIdentityWithoutExplicitSequenceFailsClearly() throws SQLException {
		try (Connection connection = createConnection()) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

			SQLException exception = assertThrows(SQLException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
					session.setTableOperationMode(TableOperationMode.INSERT);
					addParent(session, parent, "unsupported-identity");
				}
			});
			assertTrue(exception.getMessage().contains("associate an explicit sequence"));
			connection.rollback();
		}
	}

	private Connection createConnection() throws SQLException {
		String url = "jdbc:sap://localhost:" + HANA.getMappedPort(39041) + "/?databaseName=HXE";
		return DriverManager.getConnection(url, "SYSTEM", PASSWORD);
	}

	private Schema loadSchema(final Connection connection) throws SQLException {
		return SchemaUtils.getSchema(connection, "SYSTEM", "PARENT_TABLE", "CHILD_TABLE")
				.orElseThrow(() -> new AssertionError("SAP HANA test schema was not loaded."));
	}

	private Row addParent(final JdbcTreeDataSession session, final Table table, final String text) throws SQLException {
		Row row = session.newRow(table);
		row.put("TXT", text);
		return row;
	}

	private Row addChild(final JdbcTreeDataSession session, final Table table, final String text) throws SQLException {
		Row row = session.newRow(table);
		row.put("TXT", text);
		return row;
	}

	private void createTables(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			drop(statement, "DROP TABLE CHILD_TABLE");
			drop(statement, "DROP TABLE PARENT_TABLE");
			drop(statement, "DROP SEQUENCE PARENT_SEQ");
			drop(statement, "DROP SEQUENCE CHILD_SEQ");
			statement.execute("CREATE SEQUENCE PARENT_SEQ START WITH 1001 INCREMENT BY 1");
			statement.execute("CREATE SEQUENCE CHILD_SEQ START WITH 2001 INCREMENT BY 1");
			statement.execute("""
					CREATE TABLE parent_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						txt NVARCHAR(256)
					)
					""");
			statement.execute("""
					CREATE TABLE child_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
						parent_id BIGINT NOT NULL,
						txt NVARCHAR(256),
						CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent_table(id)
					)
					""");
			connection.commit();
		}
	}

	private void drop(final Statement statement, final String sql) {
		try {
			statement.execute(sql);
		} catch (SQLException e) {
			// The test database starts empty and HANA has no portable DROP IF EXISTS here.
		}
	}
}
