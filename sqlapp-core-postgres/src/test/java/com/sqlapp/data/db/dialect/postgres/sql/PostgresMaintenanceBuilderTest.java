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

class PostgresMaintenanceBuilderTest {
	private final PostgresMaintenanceBuilder builder =
			new PostgresMaintenanceBuilder(DialectHolder.postgreSQL180);

	@Test
	void testVacuumOnlyPartitionedTable() {
		assertEquals("VACUUM ONLY sales.orders",
				builder.vacuum("sales", "orders").only().build());
	}

	@Test
	void testAnalyzeOnlySelectedColumns() {
		assertEquals("ANALYZE ONLY sales.orders (amount, ordered_at)",
				builder.analyze("sales", "orders")
						.only()
						.column("amount")
						.column("ordered_at")
						.build());
	}

	@Test
	void testMaintenanceIncludingChildrenOnEarlierVersion() {
		PostgresMaintenanceBuilder postgres17 =
				new PostgresMaintenanceBuilder(DialectHolder.postgreSQL170);
		assertEquals("VACUUM sales.orders",
				postgres17.vacuum("sales", "orders").build());
		assertEquals("ANALYZE sales.orders (amount)",
				postgres17.analyze("sales", "orders")
						.column("amount")
						.build());
	}

	@Test
	void testRejectOnlyBeforePostgres18() {
		PostgresMaintenanceBuilder postgres17 =
				new PostgresMaintenanceBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.vacuum("orders").only().build());
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.analyze("orders").only().build());
	}

	@Test
	void testQuoteIdentifiersAndValidateNames() {
		assertEquals("VACUUM ONLY \"Sales Data\".\"Order\"",
				builder.vacuum("Sales Data", "Order").only().build());
		assertThrows(IllegalArgumentException.class,
				() -> builder.vacuum(""));
		assertThrows(IllegalArgumentException.class,
				() -> builder.analyze("orders").column(""));
	}
}
