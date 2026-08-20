/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.MySql840;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.ParameterDirection;

/** MySQL 8.4 integration coverage for the metadata reader tree. */
class MySqlMetadataReaderTest {
	private static final MySQLContainer MYSQL = ReusableTestcontainers
			.configure(new MySQLContainer("mysql:8.4")
					.withCommand("--log-bin-trust-function-creators=1"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(MYSQL);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(MYSQL);
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromMySql84() throws SQLException {
		try (Connection connection = MYSQL.createConnection("");
				Statement statement = connection.createStatement()) {
			createObjects(statement);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(MySql840.class, dialect);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName(MYSQL.getDatabaseName());
			var schema = reader.getAllFull(connection).stream()
					.filter(s -> MYSQL.getDatabaseName().equals(s.getName()))
					.findFirst().orElseThrow();

			var parent = schema.getTables().get("metadata_parent");
			assertNotNull(parent);
			assertEquals("metadata parent", parent.getRemarks());
			assertTrue(parent.getColumns().get("id").isIdentity());
			assertEquals("display name",
					parent.getColumns().get("name").getRemarks());
			var primaryKey = assertInstanceOf(UniqueConstraint.class,
					parent.getConstraints().get("PRIMARY"));
			assertTrue(primaryKey.isPrimaryKey());
			assertEquals("id", primaryKey.getColumns().get(0).getName());
			assertNotNull(parent.getConstraints().get("uk_metadata_parent_code"));
			assertNotNull(parent.getConstraints().get("ck_metadata_parent_amount"));
			assertNotNull(parent.getIndexes().get("idx_metadata_parent_name"));
			assertTrue(!parent.getIndexes().get("idx_metadata_parent_invisible").isEnable());
			var functionalIndex = parent.getIndexes().get("idx_metadata_parent_lower_name");
			assertNotNull(functionalIndex);
			assertTrue(functionalIndex.getColumns().get(0).getName()
					.toLowerCase().contains("lower"));
			var orderedIndex = parent.getIndexes().get("idx_metadata_parent_ordered");
			assertEquals(Order.Desc, orderedIndex.getColumns().get(0).getOrder());
			assertEquals(10L, orderedIndex.getColumns().get(1).getLength());

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
			assertNotNull(partitioned.getPartitioning().getPartitions()
					.get("pmax").getSubPartitions().get("pmaxs1"));

			var view = schema.getViews().get("metadata_view");
			assertNotNull(view);
			assertTrue(view.getDefinition().toString().contains("metadata_parent"));
			assertEquals(2, view.getColumns().size());

			var procedure = schema.getProcedures().get("metadata_procedure");
			assertNotNull(procedure);
			assertNotNull(procedure.getStatement());
			assertEquals(ParameterDirection.Input,
					procedure.getArguments().get("p_id").getDirection());
			assertEquals(ParameterDirection.Output,
					procedure.getArguments().get("p_name").getDirection());
			var function = schema.getFunctions().get("metadata_function");
			assertNotNull(function);
			assertNotNull(function.getDefinition());
			assertNotNull(function.getArguments().get("p_amount"));
			assertNotNull(function.getReturning().getDataType());
			assertNotNull(schema.getTriggers().get("metadata_trigger"));
			var event = schema.getEvents().get("metadata_event");
			assertNotNull(event);
			assertEquals("DAY", event.getIntervalField());
			assertEquals(1, event.getIntervalValue().intValue());
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		statement.execute("DROP EVENT IF EXISTS metadata_event");
		statement.execute("DROP TRIGGER IF EXISTS metadata_trigger");
		statement.execute("DROP FUNCTION IF EXISTS metadata_function");
		statement.execute("DROP PROCEDURE IF EXISTS metadata_procedure");
		statement.execute("DROP VIEW IF EXISTS metadata_view");
		statement.execute("DROP TABLE IF EXISTS metadata_child");
		statement.execute("DROP TABLE IF EXISTS metadata_parent");
		statement.execute("DROP TABLE IF EXISTS metadata_partitioned");
		statement.execute("DROP TABLE IF EXISTS metadata_audit");
		statement.execute("""
				CREATE TABLE metadata_parent (
				 id BIGINT NOT NULL AUTO_INCREMENT,
				 code VARCHAR(40) NOT NULL,
				 name VARCHAR(100) COMMENT 'display name',
				 amount DECIMAL(18,2) NOT NULL,
				 PRIMARY KEY (id),
				 CONSTRAINT uk_metadata_parent_code UNIQUE (code),
				 CONSTRAINT ck_metadata_parent_amount CHECK (amount >= 0),
				 INDEX idx_metadata_parent_name (name),
				 INDEX idx_metadata_parent_invisible (amount) INVISIBLE,
				 INDEX idx_metadata_parent_lower_name ((LOWER(name))),
				 INDEX idx_metadata_parent_ordered (name DESC, code(10))
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
				  PARTITION p0 VALUES LESS THAN (10) (
				   SUBPARTITION p0s0 COMMENT='p0s0 comment', SUBPARTITION p0s1),
				  PARTITION p1 VALUES LESS THAN (20) (
				   SUBPARTITION p1s0, SUBPARTITION p1s1),
				  PARTITION pmax VALUES LESS THAN MAXVALUE (
				   SUBPARTITION pmaxs0, SUBPARTITION pmaxs1))
				""");
		statement.execute("CREATE TABLE metadata_audit (parent_id BIGINT NOT NULL)");
		statement.execute("CREATE VIEW metadata_view AS SELECT id, code FROM metadata_parent");
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
				 DO DELETE FROM metadata_audit WHERE parent_id < 0
				""");
	}
}
