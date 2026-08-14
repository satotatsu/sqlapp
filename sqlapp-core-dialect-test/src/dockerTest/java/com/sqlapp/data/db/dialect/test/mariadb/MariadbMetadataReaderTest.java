/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.mariadb;

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
import org.testcontainers.mariadb.MariaDBContainer;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_80;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.ParameterDirection;

/** MariaDB 11.8 integration coverage for the metadata reader tree. */
class MariadbMetadataReaderTest {
	private static final MariaDBContainer MARIADB = ReusableTestcontainers
			.configure(new MariaDBContainer("mariadb:11.8")
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
		try (Connection connection = MARIADB.createConnection("");
				Statement statement = connection.createStatement()) {
			createObjects(statement);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Mariadb11_80.class, dialect);
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
			assertNotNull(parent.getConstraints().get("uk_metadata_parent_code"));
			assertNotNull(parent.getConstraints().get("ck_metadata_parent_amount"));
			assertNotNull(parent.getIndexes().get("idx_metadata_parent_name"));

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
			assertEquals(3, partitioned.getPartitioning().getPartitions().size());
			assertNotNull(schema.getViews().get("metadata_view"));

			var sequence = schema.getSequences().get("metadata_sequence");
			assertNotNull(sequence);
			assertEquals(10L, sequence.getStartValue().longValue());
			assertEquals(5L, sequence.getIncrementBy().longValue());
			assertEquals(10L, sequence.getMinValue().longValue());
			assertEquals(1000L, sequence.getMaxValue().longValue());
			assertTrue(sequence.isCycle());

			var procedure = schema.getProcedures().get("metadata_procedure");
			assertNotNull(procedure);
			assertNotNull(procedure.getStatement());
			assertEquals(ParameterDirection.Input,
					procedure.getArguments().get("p_id").getDirection());
			assertEquals(ParameterDirection.Output,
					procedure.getArguments().get("p_name").getDirection());
			var function = schema.getFunctions().get("metadata_function");
			assertNotNull(function);
			assertNotNull(function.getArguments().get("p_amount"));
			assertNotNull(schema.getTriggers().get("metadata_trigger"));
			assertNotNull(schema.getEvents().get("metadata_event"));
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		statement.execute("DROP EVENT IF EXISTS metadata_event");
		statement.execute("DROP TRIGGER IF EXISTS metadata_trigger");
		statement.execute("DROP FUNCTION IF EXISTS metadata_function");
		statement.execute("DROP PROCEDURE IF EXISTS metadata_procedure");
		statement.execute("DROP SEQUENCE IF EXISTS metadata_sequence");
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
				 INDEX idx_metadata_parent_name (name)
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
				 PARTITION BY RANGE (bucket) (
				  PARTITION p0 VALUES LESS THAN (10),
				  PARTITION p1 VALUES LESS THAN (20),
				  PARTITION pmax VALUES LESS THAN MAXVALUE)
				""");
		statement.execute("CREATE TABLE metadata_audit (parent_id BIGINT NOT NULL)");
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
				 DO DELETE FROM metadata_audit WHERE parent_id < 0
				""");
	}
}
