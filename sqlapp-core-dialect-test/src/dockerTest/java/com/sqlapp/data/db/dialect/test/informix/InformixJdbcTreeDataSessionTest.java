/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.informix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.sqlapp.data.db.dialect.informix.metadata.InformixTableReader;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;
import com.sqlapp.jdbc.sql.ParameterDirection;

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
			Schema schema = SchemaUtils.getSchema(connection, "informix", "parent_table", "child_table",
					"parent_view", "metadata_audit", "metadata_trigger", "metadata_procedure",
					"metadata_function", "metadata_sequence", "metadata_fragmented")
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
			assertEquals("'child-default'", child.getColumns().get("txt").getDefaultValue());
			assertTrue(child.getConstraints().stream()
					.anyMatch(CheckConstraint.class::isInstance));
			ForeignKeyConstraint foreignKey = child.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertTrue(child.getIndexes().stream().anyMatch(
					index -> "idx_child_txt".equalsIgnoreCase(index.getName())));
			var descendingIndex = child.getIndexes().stream()
					.filter(index -> "idx_child_txt_desc".equalsIgnoreCase(index.getName()))
					.findFirst().orElseThrow();
			assertTrue(descendingIndex.isUnique());
			assertEquals(Order.Desc, descendingIndex.getColumns().get(0).getOrder());
			var view = schema.getViews().get("parent_view");
			assertNotNull(view);
			assertTrue(view.getStatement().toString().toLowerCase()
					.contains("parent_table"), view.getStatement().toString());
			var trigger = schema.getTriggers().get("metadata_trigger");
			assertNotNull(trigger);
			assertEquals("parent_table", trigger.getTableName());
			assertEquals("AFTER", trigger.getActionTiming());
			assertEquals("ROW", trigger.getActionOrientation());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertTrue(trigger.getStatement().toString().toLowerCase()
					.contains("metadata_audit"));
			var procedure = schema.getProcedures().get("metadata_procedure");
			assertNotNull(procedure);
			assertTrue(procedure.getStatement().toString().toLowerCase()
					.contains("insert into metadata_audit"));
			assertEquals(1, procedure.getArguments().size());
			assertEquals("p_id", procedure.getArguments().get(0).getName().toLowerCase());
			assertEquals(ParameterDirection.Input, procedure.getArguments().get(0).getDirection());
			var function = schema.getFunctions().get("metadata_function");
			assertNotNull(function);
			assertTrue(function.getStatement().toString().toLowerCase()
					.contains("return p_value * 2"));
			assertEquals(1, function.getArguments().size());
			assertEquals("p_value", function.getArguments().get(0).getName().toLowerCase());
			assertEquals(ParameterDirection.Input, function.getArguments().get(0).getDirection());
			var sequence = schema.getSequences().get("metadata_sequence");
			assertNotNull(sequence);
			assertEquals(10L, sequence.getStartValue().longValue());
			assertEquals(5L, sequence.getIncrementBy().longValue());
			assertEquals(10L, sequence.getMinValue().longValue());
			assertEquals(1000L, sequence.getMaxValue().longValue());
			assertTrue(sequence.isCycle());
			assertEquals(10L, sequence.getCacheSize().longValue());
			var fragmented = schema.getTables().get("metadata_fragmented");
			assertNotNull(fragmented.getPartitioning(), () -> fragmentDetails(connection));
			assertEquals("E", fragmented.getPartitioning().getSpecifics()
					.get(InformixTableReader.INFORMIX_FRAGMENT_STRATEGY));
			assertEquals(2, fragmented.getPartitioning().getPartitions().size());
			var lowFragment = fragmented.getPartitioning().getPartitions().get("frag_low");
			assertNotNull(lowFragment);
			assertEquals("rootdbs", lowFragment.getTableSpaceName());
			assertTrue(lowFragment.getSpecifics()
					.get(InformixTableReader.INFORMIX_FRAGMENT_EXPRESSION).toString()
					.contains("id < 100"));
			var highFragment = fragmented.getPartitioning().getPartitions().get("frag_high");
			assertNotNull(highFragment);
			assertTrue(highFragment.getSpecifics()
					.get(InformixTableReader.INFORMIX_FRAGMENT_EXPRESSION).toString()
					.contains("id >= 100"));
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

	private String fragmentDetails(final Connection connection) {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("""
						SELECT t.owner, t.tabname, f.fragtype, f.strategy, f.evalpos,
						       f.partition, f.exprtext, f.dbspace
						FROM systables t JOIN sysfragments f ON t.tabid = f.tabid
						WHERE t.tabname = 'metadata_fragmented'
						ORDER BY f.evalpos
						""")) {
			StringBuilder builder = new StringBuilder("sysfragments=");
			while (resultSet.next()) {
				builder.append('[');
				for (int i = 1; i <= 8; i++) {
					if (i > 1) {
						builder.append(',');
					}
					builder.append(resultSet.getString(i));
				}
				builder.append(']');
			}
			return builder.toString();
		} catch (SQLException e) {
			return "Unable to query sysfragments: " + e.getMessage();
		}
	}

	private void createTables(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			dropTrigger(statement, "metadata_trigger");
			dropRoutine(statement, "metadata_procedure", true);
			dropRoutine(statement, "metadata_function", false);
			dropSequence(statement, "metadata_sequence");
			dropView(statement, "parent_view");
			dropTable(statement, "metadata_fragmented");
			dropTable(statement, "metadata_audit");
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
						txt VARCHAR(255) DEFAULT 'child-default',
						FOREIGN KEY (parent_id)
							REFERENCES parent_table(id),
						CHECK (txt <> '') CONSTRAINT ck_child_txt
					)
					""");
			statement.execute("CREATE INDEX idx_child_txt ON child_table(txt)");
			statement.execute("CREATE UNIQUE INDEX idx_child_txt_desc ON child_table(txt DESC)");
			statement.execute("CREATE VIEW parent_view AS SELECT id, txt FROM parent_table");
			statement.execute("CREATE TABLE metadata_audit (parent_id INTEGER NOT NULL)");
			statement.execute("""
					CREATE TRIGGER metadata_trigger INSERT ON parent_table
					REFERENCING NEW AS new_row
					FOR EACH ROW
					(INSERT INTO metadata_audit(parent_id) VALUES (new_row.id))
					""");
			statement.execute("""
					CREATE PROCEDURE metadata_procedure(p_id INTEGER)
					INSERT INTO metadata_audit(parent_id) VALUES (p_id);
					END PROCEDURE
					""");
			statement.execute("""
					CREATE FUNCTION metadata_function(p_value INTEGER)
					RETURNING INTEGER;
					RETURN p_value * 2;
					END FUNCTION
					""");
			statement.execute("""
					CREATE SEQUENCE metadata_sequence START WITH 10 INCREMENT BY 5
					MINVALUE 10 MAXVALUE 1000 CYCLE CACHE 10
					""");
			statement.execute("""
					CREATE TABLE metadata_fragmented (
						id INTEGER NOT NULL,
						value_text VARCHAR(50)
					)
					FRAGMENT BY EXPRESSION
						PARTITION frag_low id < 100 IN rootdbs,
						PARTITION frag_high id >= 100 IN rootdbs
					""");
			connection.commit();
		}
	}

	private void dropTrigger(final Statement statement, final String triggerName) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(
				"SELECT COUNT(*) FROM systriggers WHERE trigname = '" + triggerName + "'")) {
			resultSet.next();
			if (resultSet.getInt(1) == 0) {
				return;
			}
		}
		statement.execute("DROP TRIGGER " + triggerName);
	}

	private void dropRoutine(final Statement statement, final String routineName,
			final boolean procedure) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(
				"SELECT COUNT(*) FROM sysprocedures WHERE procname = '" + routineName
						+ "' AND isproc = '" + (procedure ? "t" : "f") + "'")) {
			resultSet.next();
			if (resultSet.getInt(1) == 0) {
				return;
			}
		}
		statement.execute("DROP " + (procedure ? "PROCEDURE " : "FUNCTION ") + routineName);
	}

	private void dropSequence(final Statement statement, final String sequenceName) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
				SELECT COUNT(*)
				FROM syssequences s JOIN systables t ON s.tabid = t.tabid
				WHERE t.tabname = '%s'
				""".formatted(sequenceName))) {
			resultSet.next();
			if (resultSet.getInt(1) == 0) {
				return;
			}
		}
		statement.execute("DROP SEQUENCE " + sequenceName);
	}

	private void dropView(final Statement statement, final String viewName) throws SQLException {
		try {
			statement.execute("DROP VIEW " + viewName);
		} catch (SQLException e) {
			if (e.getErrorCode() != -206) {
				throw e;
			}
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
