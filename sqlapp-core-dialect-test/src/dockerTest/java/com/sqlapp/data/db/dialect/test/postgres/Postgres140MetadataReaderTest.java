/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.postgres.Postgres140;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.PartitioningType;

/** PostgreSQL 14 compatibility coverage for the metadata reader tree. */
class Postgres140MetadataReaderTest {
	private static final PostgreSQLContainer POSTGRES =
			ReusableTestcontainers.configure(new PostgreSQLContainer("postgres:14"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(POSTGRES);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(POSTGRES);
	}

	@Test
	void testMetadataReaderRemainsOperationalOnPostgres14() throws SQLException {
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement()) {
			createObjects(statement);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Postgres140.class, dialect);
			var reader = dialect.getCatalogReader();
			String catalogName = connection.getCatalog();
			reader.setCatalogName(catalogName);
			var catalog = reader.getAllFull(connection).stream()
					.filter(c -> catalogName.equals(c.getName()))
					.findFirst().orElseThrow();
			var schema = catalog.getSchemas().get("metadata_test_14");
			assertNotNull(schema);
			var child = schema.getTables().get("metadata_child");
			assertNotNull(child);
			var foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
					child.getConstraints().get("fk_metadata_child_parent"));
			assertEquals(2, foreignKey.getColumns().size());
			assertNotNull(child.getIndexes().get("idx_metadata_child_parent"));
			assertNotNull(schema.getSequences().get("metadata_seq"));
			var view = schema.getViews().get("metadata_view");
			assertNotNull(view);
			assertEquals(2, view.getColumns().size());
			assertTrue(String.join("\n", view.getStatement())
					.toLowerCase(Locale.ROOT).contains("metadata_child"));
			var function = schema.getFunctions().get("metadata_function");
			assertNotNull(function);
			assertEquals(1, function.getArguments().size());
			assertEquals("p_amount", function.getArguments().get(0).getName());
			assertTrue(String.join("\n", function.getStatement())
					.toLowerCase(Locale.ROOT).contains("p_amount * 2"));
			var trigger = schema.getTriggers().get("metadata_trigger");
			assertNotNull(trigger);
			assertEquals("metadata_child", trigger.getTableName());
			assertTrue(String.join("\n", trigger.getStatement())
					.toLowerCase(Locale.ROOT).contains("metadata_trigger_function"));
			var operator = schema.getOperators().get("#@#");
			assertNotNull(operator);
			assertEquals(DataType.INT,
					operator.getLeftArgument().getDataType());
			assertEquals(DataType.INT,
					operator.getRightArgument().getDataType());
			assertEquals("metadata_test_14", operator.getFunctionSchemaName());
			assertEquals("metadata_int_add", operator.getFunctionName());
			var operatorClass = schema.getOperatorClasses()
					.get("metadata_int_ops");
			assertNotNull(operatorClass);
			assertEquals(DataType.INT, operatorClass.getDataType());
			assertEquals(IndexType.BTree, operatorClass.getIndexType());
			assertEquals(5, operatorClass.getOperatorFamilies().size());
			assertEquals(1, operatorClass.getFunctionFamilies().size());
			assertTrue(operatorClass.getFunctionFamilies().get(0)
					.getFunctionName().startsWith("metadata_int_cmp"));
			var partitioned = schema.getTables().get("metadata_events");
			assertNotNull(partitioned);
			assertEquals(PartitioningType.Range,
					partitioned.getPartitioning().getPartitioningType());
			var partition = schema.getTables().get("metadata_events_2025");
			assertNotNull(partition);
			assertEquals("metadata_events",
					partition.getPartitionParent().getTableName());
			var booking = schema.getTables().get("metadata_booking");
			assertNotNull(booking);
			assertNotNull(booking.getConstraints()
					.get("ex_metadata_booking_during"));
			var rule = schema.getRules().get("metadata_child_audit");
			assertNotNull(rule);
			assertEquals("metadata_child", rule.getTableName());
			assertTrue(rule.getDefinition().stream()
					.anyMatch(line -> line.contains("metadata_audit")));
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		statement.execute("DROP SCHEMA IF EXISTS metadata_test_14 CASCADE");
		statement.execute("CREATE SCHEMA metadata_test_14");
		statement.execute("CREATE SEQUENCE metadata_test_14.metadata_seq START 10");
		statement.execute("CREATE TABLE metadata_test_14.metadata_audit (child_id bigint)");
		statement.execute("""
				CREATE TABLE metadata_test_14.metadata_events (
				 id bigint NOT NULL, occurred_at date NOT NULL)
				 PARTITION BY RANGE (occurred_at)
				""");
		statement.execute("""
				CREATE TABLE metadata_test_14.metadata_events_2025
				 PARTITION OF metadata_test_14.metadata_events
				 FOR VALUES FROM ('2025-01-01') TO ('2026-01-01')
				""");
		statement.execute("""
				CREATE TABLE metadata_test_14.metadata_booking (
				 id bigint PRIMARY KEY, during daterange NOT NULL,
				 CONSTRAINT ex_metadata_booking_during
				 EXCLUDE USING gist (during WITH &&))
				""");
		statement.execute("""
				CREATE TABLE metadata_test_14.metadata_parent (
				 id bigint NOT NULL, region char(2) NOT NULL,
				 CONSTRAINT pk_metadata_parent PRIMARY KEY (id, region))
				""");
		statement.execute("""
				CREATE TABLE metadata_test_14.metadata_child (
				 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
				 parent_id bigint NOT NULL, parent_region char(2) NOT NULL,
				 amount numeric(18,2) NOT NULL,
				 CONSTRAINT ck_metadata_child_amount CHECK (amount >= 0),
				 CONSTRAINT fk_metadata_child_parent FOREIGN KEY (parent_id, parent_region)
				 REFERENCES metadata_test_14.metadata_parent(id, region))
				""");
		statement.execute("""
				CREATE INDEX idx_metadata_child_parent
				 ON metadata_test_14.metadata_child(parent_id, parent_region)
				""");
		statement.execute("""
				CREATE VIEW metadata_test_14.metadata_view AS
				 SELECT id, amount FROM metadata_test_14.metadata_child
				""");
		statement.execute("""
				CREATE FUNCTION metadata_test_14.metadata_function(p_amount numeric)
				 RETURNS numeric LANGUAGE sql IMMUTABLE AS $$ SELECT p_amount * 2 $$
				""");
		statement.execute("""
				CREATE FUNCTION metadata_test_14.metadata_trigger_function()
				 RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$
				""");
		statement.execute("""
				CREATE TRIGGER metadata_trigger BEFORE INSERT
				 ON metadata_test_14.metadata_child FOR EACH ROW
				 EXECUTE FUNCTION metadata_test_14.metadata_trigger_function()
				""");
		statement.execute("""
				CREATE FUNCTION metadata_test_14.metadata_int_add(integer, integer)
				 RETURNS integer LANGUAGE sql IMMUTABLE
				 AS $$ SELECT $1 + $2 $$
				""");
		statement.execute("""
				CREATE OPERATOR metadata_test_14.#@# (
				 LEFTARG = integer, RIGHTARG = integer,
				 FUNCTION = metadata_test_14.metadata_int_add)
				""");
		statement.execute("""
				CREATE FUNCTION metadata_test_14.metadata_int_cmp(integer, integer)
				 RETURNS integer LANGUAGE sql IMMUTABLE STRICT
				 AS $$ SELECT CASE WHEN $1 < $2 THEN -1
				  WHEN $1 > $2 THEN 1 ELSE 0 END $$
				""");
		statement.execute("""
				CREATE OPERATOR FAMILY metadata_test_14.metadata_int_family
				 USING btree
				""");
		statement.execute("""
				CREATE OPERATOR CLASS metadata_test_14.metadata_int_ops
				 FOR TYPE integer USING btree
				 FAMILY metadata_test_14.metadata_int_family AS
				  OPERATOR 1 <, OPERATOR 2 <=, OPERATOR 3 =,
				  OPERATOR 4 >=, OPERATOR 5 >,
				  FUNCTION 1 metadata_test_14.metadata_int_cmp(integer, integer)
				""");
		statement.execute("""
				CREATE RULE metadata_child_audit AS
				 ON INSERT TO metadata_test_14.metadata_child DO ALSO
				 INSERT INTO metadata_test_14.metadata_audit(child_id) VALUES (NEW.id)
				""");
	}
}
