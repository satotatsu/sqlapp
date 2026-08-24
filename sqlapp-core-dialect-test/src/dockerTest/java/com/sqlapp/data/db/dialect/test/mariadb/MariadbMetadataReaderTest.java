/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.mariadb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mariadb.MariaDBContainer;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_80;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.EventType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.ParameterDirection;

/** MariaDB 11.8 integration coverage for the metadata reader tree. */
class MariadbMetadataReaderTest {
	private static final String ROOT_PASSWORD = "metadata-root";
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer("mariadb:11.8")
					.withEnv("MARIADB_ROOT_PASSWORD", ROOT_PASSWORD)
					.withCommand("--log-bin-trust-function-creators=1"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MARIADB);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MARIADB);
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromMariaDb118() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				MARIADB.getJdbcUrl(), "root", ROOT_PASSWORD);
				Statement statement = connection.createStatement()) {
			createObjects(statement);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Mariadb11_80.class, dialect);
			var roleReader = dialect.getCatalogReader().getRoleReader();
			roleReader.setObjectName("metadata_reader_role");
			var roles = roleReader.getAllFull(connection);
			assertEquals(1, roles.size());
			assertEquals("metadata_reader_role", roles.get(0).getName());
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName(MARIADB.getDatabaseName());
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> MARIADB.getDatabaseName().equals(s.getName()))
					.findFirst().orElseThrow();

			var parent = schema.getTables().get("metadata_parent");
			assertNotNull(parent);
			assertEquals("metadata parent", parent.getRemarks());
			assertTrue(parent.getColumns().get("id").isIdentity());
			var primaryKey = assertInstanceOf(UniqueConstraint.class,
					parent.getConstraints().get("PRIMARY"));
			assertTrue(primaryKey.isPrimaryKey());
			assertEquals("id", primaryKey.getColumns().get(0).getName());
			var unique = assertInstanceOf(UniqueConstraint.class,
					parent.getConstraints().get("uk_metadata_parent_code"));
			assertEquals("code", unique.getColumns().get(0).getName());
			var check = assertInstanceOf(CheckConstraint.class,
					parent.getConstraints().get("ck_metadata_parent_amount"));
			assertTrue(check.getExpression().replace("`", "")
					.contains("amount >= 0"), check::getExpression);
			var nameIndex = parent.getIndexes().get("idx_metadata_parent_name");
			assertNotNull(nameIndex);
			assertEquals("name", nameIndex.getColumns().get(0).getName());
			assertTrue(parent.getColumns().get("secret_value").isHidden());
			assertTrue(!parent.getIndexes().get("idx_metadata_parent_ignored").isEnable());

			var child = schema.getTables().get("metadata_child");
			assertNotNull(child);
			var foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
					child.getConstraints().get("fk_metadata_child_parent"));
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			assertNotNull(child.getColumns().get("normalized_code")
					.getFormula());

			var partitioned = schema.getTables().get("metadata_partitioned");
			assertNotNull(partitioned);
			assertEquals(PartitioningType.Range,
					partitioned.getPartitioning().getPartitioningType());
			assertEquals(PartitioningType.Hash,
					partitioned.getPartitioning().getSubPartitioningType());
			assertEquals("bucket", partitioned.getPartitioning()
					.getPartitioningColumns().get(0).getName());
			assertEquals("id", partitioned.getPartitioning()
					.getSubPartitioningColumns().get(0).getName());
			assertEquals(3, partitioned.getPartitioning().getPartitions().size());
			assertTrue(partitioned.getPartitioning().getPartitions().stream()
					.allMatch(partition -> partition.getSubPartitions().size() == 2));
			assertEquals("p0s0 comment", partitioned.getPartitioning().getPartitions()
					.get("p0").getSubPartitions().get("p0s0").getRemarks());
			assertNull(partitioned.getPartitioning().getPartitions()
					.get("p0").getRemarks());
			assertEquals("10", partitioned.getPartitioning().getPartitions()
					.get("p0").getHighValue());
			assertEquals("MAXVALUE", partitioned.getPartitioning().getPartitions()
					.get("pmax").getHighValue());
			assertNotNull(partitioned.getPartitioning().getPartitions()
					.get("pmax").getSubPartitions().get("pmaxs1"));
			var versioned = schema.getTables().get("metadata_versioned");
			assertNotNull(versioned.getSystemVersioning());
			assertTrue(versioned.getSystemVersioning().isEnable());
			assertEquals("row_start", versioned.getTemporalPeriods().get(0)
					.getStartColumnName());
			assertEquals("row_end", versioned.getTemporalPeriods().get(0)
					.getEndColumnName());
			var view = schema.getViews().get("metadata_view");
			assertNotNull(view);
			String viewStatement = String.join("\n", view.getStatement()).toLowerCase();
			assertTrue(viewStatement.contains("metadata_parent"), viewStatement);
			assertEquals(2, view.getColumns().size());
			assertEquals("id", view.getColumns().get(0).getName());
			assertEquals("code", view.getColumns().get(1).getName());

			var sequence = schema.getSequences().get("metadata_sequence");
			assertNotNull(sequence);
			assertEquals(10L, sequence.getStartValue().longValue());
			assertEquals(5L, sequence.getIncrementBy().longValue());
			assertEquals(10L, sequence.getMinValue().longValue());
			assertEquals(1000L, sequence.getMaxValue().longValue());
			assertTrue(sequence.isCycle());

			var procedure = schema.getProcedures().get("metadata_procedure");
			assertNotNull(procedure);
			String procedureStatement = String.join("\n", procedure.getStatement()).toLowerCase();
			assertTrue(procedureStatement.contains("select name into p_name"), procedureStatement);
			assertEquals(ParameterDirection.Input,
					procedure.getArguments().get("p_id").getDirection());
			assertEquals(DataType.BIGINT, procedure.getArguments().get("p_id").getDataType());
			assertEquals(ParameterDirection.Output,
					procedure.getArguments().get("p_name").getDirection());
			assertEquals(DataType.VARCHAR, procedure.getArguments().get("p_name").getDataType());
			var function = schema.getFunctions().get("metadata_function");
			assertNotNull(function);
			String functionStatement = String.join("\n", function.getStatement()).toLowerCase();
			assertTrue(functionStatement.contains("return p_amount * 2"), functionStatement);
			assertEquals(DataType.DECIMAL, function.getArguments().get("p_amount").getDataType());
			assertEquals(2, function.getArguments().get("p_amount").getScale());
			assertEquals(DataType.DECIMAL, function.getReturning().getDataType());
			assertEquals(2, function.getReturning().getScale());
			var trigger = schema.getTriggers().get("metadata_trigger");
			assertNotNull(trigger);
			assertEquals("metadata_parent", trigger.getTableName());
			assertEquals("AFTER", trigger.getActionTiming());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertTrue(String.join("\n", trigger.getStatement())
					.contains("metadata_audit"));
			var event = schema.getEvents().get("metadata_event");
			assertNotNull(event);
			assertEquals(EventType.Recurring, event.getEventType());
			assertEquals("DAY", event.getIntervalField());
			assertEquals(1, event.getIntervalValue().intValue());
			assertEquals("PRESERVE", event.getOnCompletion());
			assertTrue(!event.isEnable());
			assertEquals("metadata event", event.getRemarks());
			assertTrue(String.join("\n", event.getStatement())
					.contains("metadata_audit"));
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		statement.execute("DROP ROLE IF EXISTS metadata_reader_role");
		statement.execute("CREATE ROLE metadata_reader_role");
		statement.execute("DROP EVENT IF EXISTS metadata_event");
		statement.execute("DROP TRIGGER IF EXISTS metadata_trigger");
		statement.execute("DROP FUNCTION IF EXISTS metadata_function");
		statement.execute("DROP PROCEDURE IF EXISTS metadata_procedure");
		statement.execute("DROP SEQUENCE IF EXISTS metadata_sequence");
		statement.execute("DROP VIEW IF EXISTS metadata_view");
		statement.execute("DROP TABLE IF EXISTS metadata_child");
		statement.execute("DROP TABLE IF EXISTS metadata_parent");
		statement.execute("DROP TABLE IF EXISTS metadata_partitioned");
		statement.execute("DROP TABLE IF EXISTS metadata_versioned");
		statement.execute("DROP TABLE IF EXISTS metadata_audit");
		statement.execute("""
				CREATE TABLE metadata_parent (
				 id BIGINT NOT NULL AUTO_INCREMENT,
				 code VARCHAR(40) NOT NULL,
				 name VARCHAR(100) COMMENT 'display name',
				 secret_value VARCHAR(40) INVISIBLE,
				 amount DECIMAL(18,2) NOT NULL,
				 PRIMARY KEY (id),
				 CONSTRAINT uk_metadata_parent_code UNIQUE (code),
				 CONSTRAINT ck_metadata_parent_amount CHECK (amount >= 0),
				 INDEX idx_metadata_parent_name (name),
				 INDEX idx_metadata_parent_ignored (amount) IGNORED
				) COMMENT='metadata parent'
				""");
		statement.execute("""
				CREATE TABLE metadata_child (
				 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
				 parent_id BIGINT NOT NULL,
				 code VARCHAR(40) NOT NULL,
				 normalized_code VARCHAR(40) GENERATED ALWAYS AS (LOWER(code)) STORED,
				 CONSTRAINT fk_metadata_child_parent FOREIGN KEY (parent_id)
				  REFERENCES metadata_parent(id))
				""");
		statement.execute("""
				CREATE TABLE metadata_partitioned (
				 id BIGINT NOT NULL, bucket INT NOT NULL, PRIMARY KEY (id, bucket))
				 PARTITION BY RANGE (bucket)
				 SUBPARTITION BY HASH (id) (
				  PARTITION p0 VALUES LESS THAN (10) COMMENT='p0 comment' (
				   SUBPARTITION p0s0 COMMENT='p0s0 comment', SUBPARTITION p0s1),
				  PARTITION p1 VALUES LESS THAN (20) (
				   SUBPARTITION p1s0, SUBPARTITION p1s1),
				  PARTITION pmax VALUES LESS THAN MAXVALUE (
				   SUBPARTITION pmaxs0, SUBPARTITION pmaxs1))
				""");
		statement.execute("CREATE TABLE metadata_audit (parent_id BIGINT NOT NULL)");
		statement.execute("""
				CREATE TABLE metadata_versioned (
				 id BIGINT NOT NULL PRIMARY KEY,
				 value_text VARCHAR(100),
				 row_start TIMESTAMP(6) GENERATED ALWAYS AS ROW START,
				 row_end TIMESTAMP(6) GENERATED ALWAYS AS ROW END,
				 PERIOD FOR SYSTEM_TIME (row_start, row_end)
				) WITH SYSTEM VERSIONING
				""");
		statement.execute("CREATE VIEW metadata_view AS SELECT id, code FROM metadata_parent");
		statement.execute("CREATE SEQUENCE metadata_sequence START WITH 10 INCREMENT BY 5 MINVALUE 10 MAXVALUE 1000 CYCLE");
		statement.execute("""
				CREATE PROCEDURE metadata_procedure(IN p_id BIGINT, OUT p_name VARCHAR(100))
				 SELECT name INTO p_name FROM metadata_parent WHERE id = p_id
				""");
		statement.execute("""
				CREATE FUNCTION metadata_function(p_amount DECIMAL(18,2))
				 RETURNS DECIMAL(18,2) DETERMINISTIC RETURN p_amount * 2
				""");
		statement.execute("""
				CREATE TRIGGER metadata_trigger AFTER INSERT ON metadata_parent
				 FOR EACH ROW INSERT INTO metadata_audit(parent_id) VALUES (NEW.id)
				""");
		statement.execute("""
				CREATE EVENT metadata_event ON SCHEDULE EVERY 1 DAY
				 ON COMPLETION PRESERVE DISABLE COMMENT 'metadata event'
				 DO DELETE FROM metadata_audit WHERE parent_id < 0
				""");
	}
}
