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
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.IdentityGenerationType;
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
			assertEquals(IdentityGenerationType.ByDefault,
					parent.getColumns().get("ID").getIdentityGenerationType());
			assertNotNull(parent.getColumns().get("ID").getSequenceName());
			var alwaysIdentity = schema.getTables().get("METADATA_IDENTITY_ALWAYS")
					.getColumns().get("ID");
			assertTrue(alwaysIdentity.isIdentity());
			assertEquals(IdentityGenerationType.Always,
					alwaysIdentity.getIdentityGenerationType());
			assertNotNull(alwaysIdentity.getSequenceName());
			assertNotNull(parent.getConstraints().getPrimaryKeyConstraint());
			assertEquals("ID", parent.getConstraints().getPrimaryKeyConstraint()
					.getColumns().get(0).getName());
			ForeignKeyConstraint foreignKey = child.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("PARENT_ID", foreignKey.getColumns().get(0).getName());
			assertEquals("ID", foreignKey.getRelatedColumns().get(0).getName());
			assertEquals(CascadeRule.SetNull, foreignKey.getUpdateRule());
			assertEquals(CascadeRule.Cascade, foreignKey.getDeleteRule());
			var childTextIndex = child.getIndexes().get("IDX_CHILD_TXT");
			assertNotNull(childTextIndex);
			assertEquals("TXT", childTextIndex.getColumns().get(0).getName());
			var features = schema.getTables().get("METADATA_FEATURES");
			assertNotNull(features.getColumns().get("NORMALIZED_TXT").getFormula());
			var check = features.getConstraints().stream()
					.filter(CheckConstraint.class::isInstance)
					.map(CheckConstraint.class::cast).findFirst().orElseThrow();
			assertTrue(check.getExpression().toUpperCase()
					.contains("CHAR_LENGTH(TXT) > 0"), check::getExpression);
			var descendingIndex = features.getIndexes().get("IDX_FEATURES_TXT_DESC");
			assertTrue(descendingIndex.isUnique());
			assertEquals(Order.Desc, descendingIndex.getColumns().get(0).getOrder());
			var expressionIndex = features.getIndexes().get("IDX_FEATURES_UPPER_TXT");
			assertNotNull(expressionIndex);
			assertTrue(expressionIndex.getColumns().get(0).getName().toUpperCase()
					.contains("UPPER"));
			assertEquals("TXT IS NOT NULL", features.getIndexes()
					.get("IDX_FEATURES_TXT_PARTIAL").getWhere().toUpperCase());
			var view = schema.getViews().get("PARENT_VIEW");
			assertNotNull(view);
			String viewStatement = String.join("\n", view.getStatement()).toUpperCase();
			assertTrue(viewStatement.contains("SELECT ID, TXT FROM PARENT_TABLE"), viewStatement);
			assertEquals(2, view.getColumns().size());
			assertEquals("ID", view.getColumns().get(0).getName());
			assertEquals("TXT", view.getColumns().get(1).getName());
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
			String procedureStatement = String.join("\n", procedure.getStatement()).toUpperCase();
			assertTrue(procedureStatement.contains("P_VALUE = P_ID + 1"), procedureStatement);
			assertTrue(procedureStatement.contains("SUSPEND"), procedureStatement);
			assertNotNull(procedure.getArguments().get("P_ID"));
			assertEquals(ParameterDirection.Input, procedure.getArguments().get("P_ID").getDirection());
			assertEquals(DataType.BIGINT, procedure.getArguments().get("P_ID").getDataType());
			assertEquals(ParameterDirection.Output, procedure.getArguments().get("P_VALUE").getDirection());
			assertEquals(DataType.BIGINT, procedure.getArguments().get("P_VALUE").getDataType());
			var function = schema.getFunctions().get("METADATA_FUNCTION");
			assertNotNull(function);
			String functionStatement = String.join("\n", function.getStatement()).toUpperCase();
			assertTrue(functionStatement.contains("RETURN P_VALUE + 1"), functionStatement);
			assertNotNull(function.getArguments().get("P_VALUE"));
			assertEquals(ParameterDirection.Input, function.getArguments().get("P_VALUE").getDirection());
			assertEquals(DataType.BIGINT, function.getArguments().get("P_VALUE").getDataType());
			assertEquals(DataType.BIGINT, function.getReturning().getDataType());
			var domain = schema.getDomains().get("METADATA_POSITIVE_INT");
			assertNotNull(domain);
			assertEquals("1", domain.getDefaultValue());
			assertTrue(domain.isNotNull());
			assertTrue(domain.getCheck().toUpperCase().contains("VALUE > 0"));
			var metadataPackage = schema.getPackages().get("METADATA_PACKAGE");
			assertNotNull(metadataPackage);
			assertTrue(metadataPackage.getStatement().toString().toUpperCase()
					.contains("FUNCTION ADD_ONE"));
			var packageBody = schema.getPackageBodies().get("METADATA_PACKAGE");
			assertNotNull(packageBody);
			assertTrue(packageBody.getStatement().toString().toUpperCase()
					.contains("RETURN P_VALUE + 1"));

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
				"METADATA_FUNCTION", "METADATA_FEATURES", "METADATA_IDENTITY_ALWAYS",
				"METADATA_POSITIVE_INT", "METADATA_PACKAGE")
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
			dropObject(statement, "PACKAGE BODY", "METADATA_PACKAGE");
			dropObject(statement, "PACKAGE", "METADATA_PACKAGE");
			dropObject(statement, "PROCEDURE", "METADATA_PROCEDURE");
			dropObject(statement, "FUNCTION", "METADATA_FUNCTION");
			dropObject(statement, "VIEW", "PARENT_VIEW");
			dropObject(statement, "SEQUENCE", "METADATA_SEQUENCE");
			dropTable(statement, "METADATA_AUDIT");
			dropTable(statement, "METADATA_FEATURES");
			dropTable(statement, "METADATA_IDENTITY_ALWAYS");
			dropTable(statement, "CHILD_TABLE");
			dropTable(statement, "PARENT_TABLE");
			dropObject(statement, "DOMAIN", "METADATA_POSITIVE_INT");
			statement.execute("CREATE DOMAIN metadata_positive_int AS INTEGER "
					+ "DEFAULT 1 NOT NULL CHECK (VALUE > 0)");
			statement.execute("""
					CREATE TABLE parent_table (
					id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
					txt VARCHAR(256)
					)
					""");
			statement.execute("""
					CREATE TABLE child_table (
						id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
					parent_id BIGINT,
					txt VARCHAR(256),
					CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent_table(id)
					 ON UPDATE SET NULL ON DELETE CASCADE
					)
					""");
			statement.execute("CREATE INDEX idx_child_txt ON child_table(txt)");
			statement.execute("""
					CREATE TABLE metadata_identity_always (
					 id BIGINT GENERATED ALWAYS AS IDENTITY
					  (START WITH 42 INCREMENT BY 7) PRIMARY KEY
					)
					""");
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
			statement.execute("CREATE INDEX idx_features_upper_txt ON metadata_features "
					+ "COMPUTED BY (UPPER(txt))");
			statement.execute("CREATE INDEX idx_features_txt_partial ON metadata_features(txt) "
					+ "WHERE txt IS NOT NULL");
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
			statement.execute("""
					CREATE PACKAGE metadata_package AS
					BEGIN
					  FUNCTION add_one(p_value BIGINT) RETURNS BIGINT;
					END
					""");
			statement.execute("""
					CREATE PACKAGE BODY metadata_package AS
					BEGIN
					  FUNCTION add_one(p_value BIGINT) RETURNS BIGINT
					  AS
					  BEGIN
					    RETURN p_value + 1;
					  END
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
