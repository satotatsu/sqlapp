/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.informix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
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
					"metadata_update_trigger",
					"metadata_function", "metadata_sequence", "metadata_fragmented",
					"metadata_round_robin", "metadata_list_fragmented", "metadata_range_fragmented",
					"metadata_parent_synonym", "metadata_serial8", "metadata_bigserial",
					"metadata_types", "MetadataCaseTable")
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
			assertBigSerialIdentity(schema.getTables().get("metadata_serial8"));
			assertBigSerialIdentity(schema.getTables().get("metadata_bigserial"));
			var types = schema.getTables().get("metadata_types");
			assertEquals(DataType.BOOLEAN, types.getColumns().get("boolean_value").getDataType());
			assertEquals(DataType.LONGVARCHAR, types.getColumns().get("long_text").getDataType());
			assertEquals(DataType.CLOB, types.getColumns().get("text_value").getDataType());
			var byteColumn = types.getColumns().get("byte_value");
			assertEquals(DataType.BINARY, byteColumn.getDataType(),
					() -> "dataTypeName=" + byteColumn.getDataTypeName());
			assertEquals(DataType.DATETIME, types.getColumns().get("date_time_value").getDataType());
			assertEquals(DataType.TIME, types.getColumns().get("time_value").getDataType());
			assertEquals(DataType.TIMESTAMP, types.getColumns().get("timestamp_value").getDataType());
			var decimalColumn = types.getColumns().get("decimal_value");
			assertEquals(DataType.DECIMAL, decimalColumn.getDataType());
			assertEquals(20L, decimalColumn.getLength());
			assertEquals(4, decimalColumn.getScale());
			var moneyColumn = types.getColumns().get("money_value");
			assertEquals(DataType.MONEY, moneyColumn.getDataType());
			assertEquals(DataType.INTERVAL_YEAR_TO_MONTH,
					types.getColumns().get("year_month_value").getDataType());
			assertEquals(DataType.INTERVAL_DAY_TO_SECOND,
					types.getColumns().get("day_second_value").getDataType());
			var caseTable = schema.getTables().get("MetadataCaseTable");
			assertNotNull(caseTable);
			assertEquals("MetadataCaseTable", caseTable.getName());
			assertNotNull(caseTable.getColumns().get("MixedId"));
			assertEquals(DataType.INT, caseTable.getColumns().get("MixedId").getDataType());
			var reservedColumn = caseTable.getColumns().get("Select");
			assertNotNull(reservedColumn);
			assertEquals(DataType.VARCHAR, reservedColumn.getDataType());
			assertEquals("'quoted'", reservedColumn.getDefaultValue());
			assertNotNull(parent.getConstraints().getPrimaryKeyConstraint(),
					() -> constraintDetails(connection, parent));
			assertEquals("id", parent.getConstraints().getPrimaryKeyConstraint()
					.getColumns().get(0).getName());
			assertEquals("'child-default'", child.getColumns().get("txt").getDefaultValue());
			var textCheck = assertInstanceOf(CheckConstraint.class,
					child.getConstraints().get("ck_child_txt"));
			String textCheckExpression = textCheck.getExpression().toLowerCase()
					.replaceAll("\\s+", "");
			assertTrue(textCheckExpression.contains("txt!=''"),
					textCheck::getExpression);
			assertTrue(textCheck.getExpression().length() < 100,
					textCheck::getExpression);
			var complexCheck = assertInstanceOf(CheckConstraint.class,
					child.getConstraints().get("ck_child_complex"));
			String complexCheckExpression = complexCheck.getExpression().toLowerCase()
					.replaceAll("\\s+", "");
			assertTrue(complexCheckExpression.contains("parent_id>0"),
					complexCheck::getExpression);
			assertTrue(complexCheckExpression.contains("length(txt)>=1"),
					complexCheck::getExpression);
			assertTrue(complexCheckExpression.contains("txt='allow,empty'"),
					complexCheck::getExpression);
			assertTrue(complexCheck.getExpression().length() < 200,
					complexCheck::getExpression);
			ForeignKeyConstraint foreignKey = child.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertEquals("fk_child_parent", foreignKey.getName());
			assertEquals(CascadeRule.Cascade, foreignKey.getDeleteRule());
			var unique = assertInstanceOf(UniqueConstraint.class,
					child.getConstraints().get("uq_child_parent_txt"));
			assertEquals(2, unique.getColumns().size());
			assertEquals("parent_id", unique.getColumns().get(0).getName());
			assertEquals("txt", unique.getColumns().get(1).getName());
			assertTrue(child.getIndexes().stream().anyMatch(
					index -> "idx_child_txt".equalsIgnoreCase(index.getName())));
			var descendingIndex = child.getIndexes().stream()
					.filter(index -> "idx_child_txt_desc".equalsIgnoreCase(index.getName()))
					.findFirst().orElseThrow();
			assertTrue(descendingIndex.isUnique());
			assertEquals(Order.Desc, descendingIndex.getColumns().get(0).getOrder());
			var mixedOrderIndex = child.getIndexes().stream()
					.filter(index -> "idx_child_parent_txt_mixed".equalsIgnoreCase(index.getName()))
					.findFirst().orElseThrow();
			assertFalse(mixedOrderIndex.isUnique());
			assertEquals(2, mixedOrderIndex.getColumns().size());
			assertEquals("parent_id", mixedOrderIndex.getColumns().get(0).getName());
			assertEquals(Order.Asc, mixedOrderIndex.getColumns().get(0).getOrder());
			assertEquals("txt", mixedOrderIndex.getColumns().get(1).getName());
			assertEquals(Order.Desc, mixedOrderIndex.getColumns().get(1).getOrder());
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
			var updateTrigger = schema.getTriggers().get("metadata_update_trigger");
			assertNotNull(updateTrigger);
			assertEquals("child_table", updateTrigger.getTableName());
			assertEquals("AFTER", updateTrigger.getActionTiming());
			assertEquals("ROW", updateTrigger.getActionOrientation());
			assertTrue(updateTrigger.getEventManipulation().contains("UPDATE"));
			assertEquals("old_row", updateTrigger.getActionReferenceOldRow());
			assertEquals("new_row", updateTrigger.getActionReferenceNewRow());
			assertTrue(updateTrigger.getStatement().toString().toLowerCase()
					.contains("old_row.parent_id"));
			var procedure = schema.getProcedures().get("metadata_procedure");
			assertNotNull(procedure);
			assertTrue(procedure.getStatement().toString().toLowerCase()
					.contains("insert into metadata_audit"));
			assertEquals(4, procedure.getArguments().size());
			assertEquals("p_id", procedure.getArguments().get(0).getName().toLowerCase());
			assertEquals(ParameterDirection.Input, procedure.getArguments().get(0).getDirection());
			assertEquals("p_value", procedure.getArguments().get(1).getName().toLowerCase());
			assertEquals(ParameterDirection.Input, procedure.getArguments().get(1).getDirection());
			assertEquals("p_double", procedure.getArguments().get(2).getName().toLowerCase());
			assertEquals(ParameterDirection.Output, procedure.getArguments().get(2).getDirection());
			assertEquals("p_counter", procedure.getArguments().get(3).getName().toLowerCase());
			assertEquals(ParameterDirection.Inout, procedure.getArguments().get(3).getDirection());
			var function = schema.getFunctions().get("metadata_function");
			assertNotNull(function);
			assertTrue(function.getStatement().toString().toLowerCase()
					.contains("return p_value * 2"));
			assertEquals(2, function.getArguments().size());
			assertEquals("p_value", function.getArguments().get(0).getName().toLowerCase());
			assertEquals(ParameterDirection.Input, function.getArguments().get(0).getDirection());
			assertEquals("p_label", function.getArguments().get(1).getName().toLowerCase());
			assertEquals(ParameterDirection.Input, function.getArguments().get(1).getDirection());
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
			var roundRobin = schema.getTables().get("metadata_round_robin");
			assertNotNull(roundRobin.getPartitioning(), () -> fragmentDetails(connection));
			assertEquals(PartitioningType.RoundRobin,
					roundRobin.getPartitioning().getPartitioningType());
			assertEquals("R", roundRobin.getPartitioning().getSpecifics()
					.get(InformixTableReader.INFORMIX_FRAGMENT_STRATEGY));
			assertEquals(2, roundRobin.getPartitioning().getPartitions().size());
			assertEquals("rootdbs", roundRobin.getPartitioning().getPartitions()
					.get("round_robin_one").getTableSpaceName());
			assertEquals("rootdbs", roundRobin.getPartitioning().getPartitions()
					.get("round_robin_two").getTableSpaceName());
			var listFragmented = schema.getTables().get("metadata_list_fragmented");
			assertNotNull(listFragmented.getPartitioning(), () -> fragmentDetails(connection));
			assertEquals(PartitioningType.List,
					listFragmented.getPartitioning().getPartitioningType());
			assertEquals("L", listFragmented.getPartitioning().getSpecifics()
					.get(InformixTableReader.INFORMIX_FRAGMENT_STRATEGY));
			assertEquals(3, listFragmented.getPartitioning().getPartitions().size());
			assertEquals("rootdbs", listFragmented.getPartitioning().getPartitions()
					.get("list_active").getTableSpaceName());
			assertEquals("rootdbs", listFragmented.getPartitioning().getPartitions()
					.get("list_inactive").getTableSpaceName());
			assertEquals("rootdbs", listFragmented.getPartitioning().getPartitions()
					.get("list_remainder").getTableSpaceName());
			var rangeFragmented = schema.getTables().get("metadata_range_fragmented");
			assertNotNull(rangeFragmented.getPartitioning(), () -> fragmentDetails(connection));
			assertEquals(PartitioningType.Range,
					rangeFragmented.getPartitioning().getPartitioningType());
			assertEquals("N", rangeFragmented.getPartitioning().getSpecifics()
					.get(InformixTableReader.INFORMIX_FRAGMENT_STRATEGY));
			assertEquals(2, rangeFragmented.getPartitioning().getPartitions().size());
			assertEquals("rootdbs", rangeFragmented.getPartitioning().getPartitions()
					.get("range_low").getTableSpaceName());
			assertEquals("rootdbs", rangeFragmented.getPartitioning().getPartitions()
					.get("range_transition").getTableSpaceName());
			var synonym = schema.getSynonyms().get("metadata_parent_synonym");
			assertNotNull(synonym);
			assertEquals("parent_table", synonym.getObjectName());
			assertEquals("informix", synonym.getObjectSchemaName());
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

	private void assertBigSerialIdentity(final Table table) {
		var column = table.getColumns().get("id");
		assertTrue(column.isIdentity());
		assertEquals(DataType.BIGSERIAL, column.getDataType());
		assertEquals(IdentityGenerationType.ByDefault,
				column.getIdentityGenerationType());
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

	private String constraintDetails(final Connection connection, final Table table) {
		StringBuilder builder = new StringBuilder("model=").append(table.getConstraints());
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("""
						SELECT c.constrname, c.constrtype, c.idxname, t.tabid
						FROM sysconstraints c JOIN systables t ON c.tabid = t.tabid
						WHERE t.tabname = 'parent_table'
						""")) {
			builder.append(", catalog=");
			while (resultSet.next()) {
				builder.append('[').append(resultSet.getString(1)).append(',')
						.append(resultSet.getString(2)).append(',')
						.append(resultSet.getString(3)).append(',')
						.append(resultSet.getInt(4)).append(']');
			}
		} catch (SQLException e) {
			builder.append(", error=").append(e.getMessage());
		}
		return builder.toString();
	}

	private void createTables(final Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			dropTrigger(statement, "metadata_update_trigger");
			dropTrigger(statement, "metadata_trigger");
			dropRoutine(statement, "metadata_procedure", true);
			dropRoutine(statement, "metadata_function", false);
			dropSequence(statement, "metadata_sequence");
			dropSynonym(statement, "metadata_parent_synonym");
			dropView(statement, "parent_view");
			dropTable(statement, "metadata_range_fragmented");
			dropTable(statement, "metadata_list_fragmented");
			dropTable(statement, "metadata_round_robin");
			dropTable(statement, "metadata_fragmented");
			dropQuotedTable(statement, "MetadataCaseTable");
			dropTable(statement, "metadata_types");
			dropTable(statement, "metadata_bigserial");
			dropTable(statement, "metadata_serial8");
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
							REFERENCES parent_table(id)
							ON DELETE CASCADE CONSTRAINT fk_child_parent,
						UNIQUE (parent_id, txt) CONSTRAINT uq_child_parent_txt,
						CHECK (txt <> '') CONSTRAINT ck_child_txt,
						CHECK ((parent_id > 0 AND LENGTH(txt) >= 1)
							OR txt = 'allow,empty') CONSTRAINT ck_child_complex
					)
					""");
			statement.execute("CREATE INDEX idx_child_txt ON child_table(txt)");
			statement.execute("CREATE UNIQUE INDEX idx_child_txt_desc ON child_table(txt DESC)");
			statement.execute(
					"CREATE INDEX idx_child_parent_txt_mixed ON child_table(parent_id, txt DESC)");
			statement.execute("CREATE TABLE metadata_serial8 (id SERIAL8 PRIMARY KEY)");
			statement.execute("CREATE TABLE metadata_bigserial (id BIGSERIAL PRIMARY KEY)");
			statement.execute("""
					CREATE TABLE metadata_types (
						boolean_value BOOLEAN,
						long_text LVARCHAR(1000),
						text_value TEXT,
						byte_value BYTE,
						date_time_value DATETIME YEAR TO SECOND,
						time_value DATETIME HOUR TO SECOND,
						timestamp_value DATETIME YEAR TO FRACTION,
						decimal_value DECIMAL(20, 4),
						money_value MONEY(16, 2),
						year_month_value INTERVAL YEAR TO MONTH,
						day_second_value INTERVAL DAY TO SECOND
					)
					""");
			statement.execute("""
					CREATE TABLE "MetadataCaseTable" (
						"MixedId" INTEGER PRIMARY KEY,
						"Select" VARCHAR(20) DEFAULT 'quoted'
					)
					""");
			statement.execute("CREATE VIEW parent_view AS SELECT id, txt FROM parent_table");
			statement.execute("CREATE TABLE metadata_audit (parent_id INTEGER NOT NULL)");
			statement.execute("""
					CREATE TRIGGER metadata_trigger INSERT ON parent_table
					REFERENCING NEW AS new_row
					FOR EACH ROW
					(INSERT INTO metadata_audit(parent_id) VALUES (new_row.id))
					""");
			statement.execute("""
					CREATE TRIGGER metadata_update_trigger UPDATE OF txt ON child_table
					REFERENCING OLD AS old_row NEW AS new_row
					FOR EACH ROW
					(INSERT INTO metadata_audit(parent_id) VALUES (old_row.parent_id))
					""");
			statement.execute("""
					CREATE PROCEDURE metadata_procedure(
						p_id INTEGER,
						p_value INTEGER,
						OUT p_double INTEGER,
						INOUT p_counter INTEGER)
					LET p_double = p_value * 2;
					LET p_counter = p_counter + 1;
					INSERT INTO metadata_audit(parent_id) VALUES (p_id);
					END PROCEDURE
					""");
			statement.execute("""
					CREATE FUNCTION metadata_function(
						p_value INTEGER,
						p_label VARCHAR(20) DEFAULT '(x,y)')
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
			statement.execute("""
					CREATE TABLE metadata_round_robin (
						id INTEGER NOT NULL,
						value_text VARCHAR(50)
					)
					FRAGMENT BY ROUND ROBIN
						PARTITION round_robin_one IN rootdbs,
						PARTITION round_robin_two IN rootdbs
					""");
			statement.execute("""
					CREATE TABLE metadata_list_fragmented (
						id INTEGER NOT NULL,
						status VARCHAR(20)
					)
					FRAGMENT BY LIST (status)
						PARTITION list_active VALUES ('active', 'pending') IN rootdbs,
						PARTITION list_inactive VALUES ('inactive') IN rootdbs,
						PARTITION list_remainder REMAINDER IN rootdbs
					""");
			statement.execute("""
					CREATE TABLE metadata_range_fragmented (
						id INTEGER NOT NULL,
						value_text VARCHAR(50)
					)
					FRAGMENT BY RANGE (id)
						INTERVAL (100) STORE IN (rootdbs)
						PARTITION range_low VALUES < 100 IN rootdbs,
						PARTITION range_transition VALUES < 200 IN rootdbs
					""");
			statement.execute("CREATE SYNONYM metadata_parent_synonym FOR parent_table");
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

	private void dropSynonym(final Statement statement, final String synonymName) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(
				"SELECT COUNT(*) FROM systables WHERE tabname = '" + synonymName
						+ "' AND tabtype IN ('P', 'S')")) {
			resultSet.next();
			if (resultSet.getInt(1) == 0) {
				return;
			}
		}
		statement.execute("DROP SYNONYM " + synonymName);
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

	private void dropQuotedTable(final Statement statement, final String tableName)
			throws SQLException {
		try {
			statement.execute("DROP TABLE \"" + tableName.replace("\"", "\"\"") + "\"");
		} catch (SQLException e) {
			if (e.getErrorCode() != -206) {
				throw e;
			}
		}
	}
}
