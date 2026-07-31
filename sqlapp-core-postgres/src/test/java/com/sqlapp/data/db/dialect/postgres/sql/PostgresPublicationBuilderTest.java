/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresPublicationBuilderTest {
	@Test
	void testCreatePublicationWithStoredGeneratedColumns() {
		assertEquals(
				"CREATE PUBLICATION sales_changes FOR TABLE ONLY sales.orders (id, total_with_tax) WHERE (status = 'READY') WITH (publish = 'insert, update', publish_generated_columns = stored, publish_via_partition_root = true)",
				new PostgresPublicationBuilder(DialectHolder.postgreSQL180,
						"sales_changes")
						.table("sales", "orders", true,
								"status = 'READY'", "id", "total_with_tax")
						.publish("insert", "update")
						.publishGeneratedColumns("stored")
						.publishViaPartitionRoot(true)
						.create());
	}

	@Test
	void testCreateForAllTables() {
		assertEquals(
				"CREATE PUBLICATION all_changes FOR ALL TABLES WITH (publish = 'insert, update, delete, truncate')",
				new PostgresPublicationBuilder(DialectHolder.postgreSQL180,
						"all_changes")
						.allTables()
						.publish("insert", "update", "delete", "truncate")
						.create());
	}

	@Test
	void testAlterGeneratedColumnPublishing() {
		assertEquals(
				"ALTER PUBLICATION sales_changes SET (publish_generated_columns = none)",
				new PostgresPublicationBuilder(DialectHolder.postgreSQL180,
						"sales_changes")
						.publishGeneratedColumns("none")
						.alterParameters());
	}

	@Test
	void testVersionBoundaryAndValidation() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresPublicationBuilder(
						DialectHolder.postgreSQL170, "sales_changes")
						.publishGeneratedColumns("stored"));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresPublicationBuilder(
						DialectHolder.postgreSQL180, "sales_changes")
						.publish("merge"));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresPublicationBuilder(
						DialectHolder.postgreSQL180, "sales_changes")
						.allTables()
						.table("orders")
						.create());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresPublicationBuilder(
						DialectHolder.postgreSQL180, "sales_changes")
						.alterParameters());
	}
}
