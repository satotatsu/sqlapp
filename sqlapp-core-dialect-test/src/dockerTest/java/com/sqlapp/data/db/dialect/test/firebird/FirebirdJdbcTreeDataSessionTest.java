/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.firebird;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Firebird 5 integration coverage for multi-row INSERT RETURNING. */
class FirebirdJdbcTreeDataSessionTest {
	private static final String PASSWORD = "masterkey";
	private static final GenericContainer<?> FIREBIRD = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("jacobalberty/firebird:v5.0"))
					.withEnv("ISC_PASSWORD", PASSWORD)
					.withEnv("FIREBIRD_DATABASE", "test.fdb")
					.withExposedPorts(3050)
					.waitingFor(Wait.forListeningPort()));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(FIREBIRD);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(FIREBIRD);
	}

	@Test
	void testBatchGeneratedKeysPropagateToMatchingChildren() throws SQLException {
		try (Connection connection = createConnection()) {
			connection.setAutoCommit(false);
			createTables(connection);
			Schema schema = loadSchema(connection);
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");
			assertTrue(parent.getColumns().get("ID").isIdentity());
			assertNotNull(parent.getColumns().get("ID").getSequenceName());
			assertNotNull(parent.getConstraints().getPrimaryKeyConstraint());
			assertEquals("ID", parent.getConstraints().getPrimaryKeyConstraint()
					.getColumns().get(0).getName());
			ForeignKeyConstraint foreignKey = child.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("PARENT_ID", foreignKey.getColumns().get(0).getName());
			assertEquals("ID", foreignKey.getRelatedColumns().get(0).getName());
			assertNotNull(child.getIndexes().get("IDX_CHILD_TXT"));
			var features = schema.getTables().get("METADATA_FEATURES");
			assertNotNull(features.getColumns().get("NORMALIZED_TXT").getFormula());
			assertTrue(features.getConstraints().stream().anyMatch(CheckConstraint.class::isInstance));
			var descendingIndex = features.getIndexes().get("IDX_FEATURES_TXT_DESC");
			assertTrue(descendingIndex.isUnique());
			assertEquals(Order.Desc, descendingIndex.getColumns().get(0).getOrder());
			var view = schema.getViews().get("PARENT_VIEW");
			assertNotNull(view);
			assertTrue(String.join("\n", view.getStatement()).toUpperCase().contains("PARENT_TABLE"));
			var trigger = schema.getTriggers().get("TRG_PARENT_AUDIT");
			assertNotNull(trigger);
			assertEquals("PARENT_TABLE", trigger.getTableName());
			assertEquals("AFTER", trigger.getActionTiming());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertTrue(String.join("\n", trigger.getStatement()).toUpperCase().contains("METADATA_AUDIT"));
			var sequence = schema.getSequences().get("METADATA_SEQUENCE");
			assertNotNull(sequence);
			assertEquals(10L, sequence.getStartValue().longValue());
			assertEquals(5L, sequence.getIncrementBy().longValue());
			var procedure = schema.getProcedures().get("METADATA_PROCEDURE");
			assertNotNull(procedure);
			assertNotNull(procedure.getStatement());
			assertNotNull(procedure.getArguments().get("P_ID"));
			assertEquals(ParameterDirection.Input, procedure.getArguments().get("P_ID").getDirection());
			assertEquals(ParameterDirection.Output, procedure.getArguments().get("P_VALUE").getDirection());
			var function = schema.getFunctions().get("METADATA_FUNCTION");
			assertNotNull(function);
			assertNotNull(function.getArguments().get("P_VALUE"));

			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(3);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setAfterRootBatchHandler((batch, table, rows) -> rows
						.forEach(row -> assertNotNull(row.get("ID"))));
				for (int i = 3; i <= 7; i++) {
					addParent(session, parent, "parent-" + i);
					addChild(session, child, "child-" + i);
				}
			}

			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT p.txt, c.txt
							FROM parent_table p
							JOIN child_table c ON c.parent_id = p.id
							ORDER BY p.id
							""")) {
				for (int i = 3; i <= 7; i++) {
					assertTrue(resultSet.next());
					assertEquals("parent-" + i, resultSet.getString(1).trim());
					assertEquals("child-" + i, resultSet.getString(2).trim());
				}
				assertFalse(resultSet.next());
			}
		}
	}

	private Connection createConnection() throws SQLException {
		String url = "jdbc:firebirdsql://localhost:" + FIREBIRD.getMappedPort(3050)
				+ "//firebird/data/test.fdb?encoding=UTF8";
		return DriverManager.getConnection(url, "SYSDBA", PASSWORD);
	}

	private Schema loadSchema(final Connection connection) throws SQLException {
		return SchemaUtils.getSchema(connection, null, "PARENT_TABLE", "CHILD_TABLE", "PARENT_VIEW",
				"METADATA_AUDIT", "TRG_PARENT_AUDIT", "METADATA_SEQUENCE", "METADATA_PROCEDURE",
				"METADATA_FUNCTION", "METADATA_FEATURES")
				.orElseThrow(() -> new AssertionError("Firebird test schema was not loaded."));
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
			dropObject(statement, "TRIGGER", "TRG_PARENT_AUDIT");
			dropObject(statement, "PROCEDURE", "METADATA_PROCEDURE");
			dropObject(statement, "FUNCTION", "METADATA_FUNCTION");
			dropObject(statement, "VIEW", "PARENT_VIEW");
			dropObject(statement, "SEQUENCE", "METADATA_SEQUENCE");
			dropTable(statement, "METADATA_AUDIT");
			dropTable(statement, "METADATA_FEATURES");
			dropTable(statement, "CHILD_TABLE");
			dropTable(statement, "PARENT_TABLE");
			statement.execute("""
					CREATE TABLE parent_table (
					id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
					txt VARCHAR(256)
					)
					""");
			statement.execute("""
					CREATE TABLE child_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
					parent_id BIGINT NOT NULL,
					txt VARCHAR(256),
					CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent_table(id)
					)
					""");
			statement.execute("CREATE INDEX idx_child_txt ON child_table(txt)");
			statement.execute("""
					CREATE TABLE metadata_features (
					 id BIGINT NOT NULL PRIMARY KEY,
					 txt VARCHAR(256),
					 normalized_txt COMPUTED BY (UPPER(txt)),
					 CONSTRAINT ck_features_txt CHECK (txt IS NULL OR CHAR_LENGTH(txt) > 0)
					)
					""");
			statement.execute("CREATE UNIQUE DESCENDING INDEX idx_features_txt_desc "
					+ "ON metadata_features(txt)");
			statement.execute("CREATE VIEW parent_view AS SELECT id, txt FROM parent_table");
			statement.execute("CREATE SEQUENCE metadata_sequence START WITH 10 INCREMENT BY 5");
			statement.execute("CREATE TABLE metadata_audit (parent_id BIGINT NOT NULL)");
			statement.execute("""
					CREATE TRIGGER trg_parent_audit FOR parent_table
					ACTIVE AFTER INSERT POSITION 0
					AS
					BEGIN
					  INSERT INTO metadata_audit(parent_id) VALUES (NEW.id);
					END
					""");
			statement.execute("""
					CREATE PROCEDURE metadata_procedure(p_id BIGINT)
					RETURNS (p_value BIGINT)
					AS
					BEGIN
					  p_value = p_id + 1;
					  SUSPEND;
					END
					""");
			statement.execute("""
					CREATE FUNCTION metadata_function(p_value BIGINT)
					RETURNS BIGINT
					AS
					BEGIN
					  RETURN p_value + 1;
					END
					""");
			connection.commit();
			statement.executeUpdate("INSERT INTO parent_table(txt) VALUES ('parent-1')");
			statement.executeUpdate("INSERT INTO parent_table(txt) VALUES ('parent-2')");
			connection.commit();
		}
	}

	private void dropObject(final Statement statement, final String objectType, final String objectName)
			throws SQLException {
		try {
			statement.execute("DROP " + objectType + " " + objectName);
		} catch (SQLException e) {
			if (e.getErrorCode() != 335544351) {
				throw e;
			}
		}
	}

	private void dropTable(final Statement statement, final String tableName) throws SQLException {
		try {
			statement.execute("DROP TABLE " + tableName);
		} catch (SQLException e) {
			if (e.getErrorCode() != 335544351) {
				throw e;
			}
		}
	}
}
