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
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.postgres.Postgres180;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/** PostgreSQL 18 integration coverage for the metadata reader tree. */
class PostgresMetadataReaderTest {
	private static final PostgreSQLContainer POSTGRES =
			ReusableTestcontainers.configure(new PostgreSQLContainer("postgres:18.4"));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(POSTGRES);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(POSTGRES);
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromLatestPostgres()
			throws SQLException {
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement()) {
			createObjects(statement);
			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(Postgres180.class, dialect);
			var reader = dialect.getCatalogReader();
			String catalogName = connection.getCatalog();
			reader.setCatalogName(catalogName);
			var catalog = reader.getAllFull(connection).stream()
					.filter(c -> catalogName.equals(c.getName()))
					.findFirst().orElseThrow();
			Schema schema = catalog.getSchemas().get("metadata_test");
			assertNotNull(schema);
			Table parent = schema.getTables().get("metadata_parent");
			assertNotNull(parent);
			assertEquals("Metadata parent table", parent.getRemarks());
			assertEquals("Display name", parent.getColumns().get("name").getRemarks());
			Table child = schema.getTables().get("metadata_child");
			assertNotNull(child);
			assertTrue(child.getColumns().get("id").isIdentity());
			assertNotNull(child.getConstraints().get("ck_metadata_child_amount"));
			ForeignKeyConstraint foreignKey = assertInstanceOf(
					ForeignKeyConstraint.class,
					child.getConstraints().get("fk_metadata_child_parent"));
			assertEquals(2, foreignKey.getColumns().size());
			assertNotNull(child.getIndexes().get("idx_metadata_child_parent"));
			var expressionIndex = child.getIndexes()
					.get("idx_metadata_child_lower_code");
			assertNotNull(expressionIndex);
			assertTrue(expressionIndex.getColumns().get(0).getName()
					.toLowerCase(Locale.ROOT).contains("lower"));
			Table booking = schema.getTables().get("metadata_booking");
			assertNotNull(booking);
			assertNotNull(booking.getConstraints()
					.get("ex_metadata_booking_during"));
			assertNotNull(schema.getSequences().get("metadata_seq"));
			assertNotNull(schema.getViews().get("metadata_view"));
			assertNotNull(schema.getMviews().get("metadata_mview"));
			assertNotNull(schema.getFunctions().get("metadata_function"));
			var operator = schema.getOperators().get("#@#");
			assertNotNull(operator);
			assertEquals(DataType.INT,
					operator.getLeftArgument().getDataType());
			assertEquals(DataType.INT,
					operator.getRightArgument().getDataType());
			assertEquals("metadata_test", operator.getFunctionSchemaName());
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
			assertNotNull(schema.getTriggers().get("metadata_trigger"));
			var rule = schema.getRules().get("metadata_child_audit");
			assertNotNull(rule);
			assertEquals("metadata_child", rule.getTableName());
			assertTrue(rule.getDefinition().stream()
					.anyMatch(line -> line.contains("metadata_audit")));
			assertNotNull(schema.getDomains().get("metadata_code"));
			assertNotNull(schema.getDomains().get("metadata_status"));
			Table partitioned = schema.getTables().get("metadata_events");
			assertNotNull(partitioned);
			assertEquals(PartitioningType.Range,
					partitioned.getPartitioning().getPartitioningType());
			assertEquals("occurred_at", partitioned.getPartitioning()
					.getPartitioningColumns().get(0).getName());
			Table partition2025 = schema.getTables().get("metadata_events_2025");
			assertNotNull(partition2025);
			assertEquals("metadata_events",
					partition2025.getPartitionParent().getTableName());
			assertTrue(partition2025.getPartitionParent().getLowValue()
					.contains("2025-01-01"));
			assertTrue(partition2025.getPartitionParent().getHighValue()
					.contains("2026-01-01"));
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		statement.execute("DROP SCHEMA IF EXISTS metadata_test CASCADE");
		statement.execute("CREATE SCHEMA metadata_test");
		statement.execute("CREATE TYPE metadata_test.metadata_status AS ENUM ('NEW', 'DONE')");
		statement.execute("CREATE DOMAIN metadata_test.metadata_code AS varchar(40) CHECK (VALUE <> '')");
		statement.execute("CREATE SEQUENCE metadata_test.metadata_seq START 50 INCREMENT 10");
		statement.execute("CREATE TABLE metadata_test.metadata_audit (child_id bigint)");
		statement.execute("""
				CREATE TABLE metadata_test.metadata_events (
				 id bigint NOT NULL, occurred_at date NOT NULL, payload text)
				 PARTITION BY RANGE (occurred_at)
				""");
		statement.execute("""
				CREATE TABLE metadata_test.metadata_events_2025
				 PARTITION OF metadata_test.metadata_events
				 FOR VALUES FROM ('2025-01-01') TO ('2026-01-01')
				""");
		statement.execute("""
				CREATE TABLE metadata_test.metadata_events_future
				 PARTITION OF metadata_test.metadata_events
				 FOR VALUES FROM ('2026-01-01') TO (MAXVALUE)
				""");
		statement.execute("""
				CREATE TABLE metadata_test.metadata_parent (
				 id bigint NOT NULL, region char(2) NOT NULL,
				 name varchar(100) NOT NULL,
				 CONSTRAINT pk_metadata_parent PRIMARY KEY (id, region))
				""");
		statement.execute("""
				CREATE TABLE metadata_test.metadata_child (
				 id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
				 parent_id bigint NOT NULL, parent_region char(2) NOT NULL,
				 code metadata_test.metadata_code DEFAULT 'unknown' NOT NULL,
				 status metadata_test.metadata_status DEFAULT 'NEW',
				 amount numeric(18,2) NOT NULL,
				 total numeric(19,2) GENERATED ALWAYS AS (amount * 2) STORED,
				 CONSTRAINT uk_metadata_child_code UNIQUE (code),
				 CONSTRAINT ck_metadata_child_amount CHECK (amount >= 0),
				 CONSTRAINT fk_metadata_child_parent FOREIGN KEY (parent_id, parent_region)
				 REFERENCES metadata_test.metadata_parent(id, region))
				""");
		statement.execute("""
				CREATE INDEX idx_metadata_child_parent
				 ON metadata_test.metadata_child(parent_id DESC, parent_region)
				 INCLUDE (code) WHERE amount > 0
				""");
		statement.execute("""
				CREATE INDEX idx_metadata_child_lower_code
				 ON metadata_test.metadata_child(lower(code)) WHERE status = 'NEW'
				""");
		statement.execute("""
				CREATE TABLE metadata_test.metadata_booking (
				 id bigint PRIMARY KEY, during daterange NOT NULL,
				 CONSTRAINT ex_metadata_booking_during
				 EXCLUDE USING gist (during WITH &&))
				""");
		statement.execute("COMMENT ON TABLE metadata_test.metadata_parent IS 'Metadata parent table'");
		statement.execute("COMMENT ON COLUMN metadata_test.metadata_parent.name IS 'Display name'");
		statement.execute("CREATE VIEW metadata_test.metadata_view AS SELECT id, code, total FROM metadata_test.metadata_child");
		statement.execute("CREATE MATERIALIZED VIEW metadata_test.metadata_mview AS SELECT count(*) AS count FROM metadata_test.metadata_child");
		statement.execute("""
				CREATE FUNCTION metadata_test.metadata_function(p_amount numeric)
				 RETURNS numeric LANGUAGE sql IMMUTABLE AS $$ SELECT p_amount * 2 $$
				""");
		statement.execute("""
				CREATE FUNCTION metadata_test.metadata_int_add(integer, integer)
				 RETURNS integer LANGUAGE sql IMMUTABLE
				 AS $$ SELECT $1 + $2 $$
				""");
		statement.execute("""
				CREATE OPERATOR metadata_test.#@# (
				 LEFTARG = integer, RIGHTARG = integer,
				 FUNCTION = metadata_test.metadata_int_add)
				""");
		statement.execute("""
				CREATE FUNCTION metadata_test.metadata_int_cmp(integer, integer)
				 RETURNS integer LANGUAGE sql IMMUTABLE STRICT
				 AS $$ SELECT CASE WHEN $1 < $2 THEN -1
				  WHEN $1 > $2 THEN 1 ELSE 0 END $$
				""");
		statement.execute("""
				CREATE OPERATOR FAMILY metadata_test.metadata_int_family
				 USING btree
				""");
		statement.execute("""
				CREATE OPERATOR CLASS metadata_test.metadata_int_ops
				 FOR TYPE integer USING btree
				 FAMILY metadata_test.metadata_int_family AS
				  OPERATOR 1 <, OPERATOR 2 <=, OPERATOR 3 =,
				  OPERATOR 4 >=, OPERATOR 5 >,
				  FUNCTION 1 metadata_test.metadata_int_cmp(integer, integer)
				""");
		statement.execute("""
				CREATE FUNCTION metadata_test.metadata_trigger_function()
				 RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$
				""");
		statement.execute("""
				CREATE TRIGGER metadata_trigger BEFORE INSERT ON metadata_test.metadata_child
				 FOR EACH ROW EXECUTE FUNCTION metadata_test.metadata_trigger_function()
				""");
		statement.execute("""
				CREATE RULE metadata_child_audit AS
				 ON INSERT TO metadata_test.metadata_child DO ALSO
				 INSERT INTO metadata_test.metadata_audit(child_id) VALUES (NEW.id)
				""");
	}
}
