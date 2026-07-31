package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresStatisticsBuilderTest {
	private final PostgresStatisticsBuilder builder =
			new PostgresStatisticsBuilder(DialectHolder.postgreSQL180);

	@Test
	void testRestoreRelationStatistics() {
		assertEquals(
				"SELECT pg_restore_relation_stats('schemaname', 'sales', 'relname', 'orders', 'relpages', 173::integer, 'reltuples', 10000::real, 'version', 170000)",
				builder.restoreRelation("sales", "orders")
						.statistic("relpages", "173::integer")
						.statistic("reltuples", "10000::real")
						.sourceVersion(170000)
						.build());
	}

	@Test
	void testRestoreAttributeStatistics() {
		assertEquals(
				"SELECT pg_restore_attribute_stats('schemaname', 'sales', 'relname', 'orders', 'attname', 'amount', 'inherited', false, 'avg_width', 16::integer, 'null_frac', 0.1::real)",
				builder.restoreAttribute("sales", "orders", "amount", false)
						.statistic("avg_width", "16::integer")
						.statistic("null_frac", "0.1::real")
						.build());
	}

	@Test
	void testClearStatistics() {
		assertEquals("SELECT pg_clear_relation_stats('sales', 'orders')",
				builder.clearRelation("sales", "orders"));
		assertEquals(
				"SELECT pg_clear_attribute_stats('sales', 'orders', 'amount', true)",
				builder.clearAttribute("sales", "orders", "amount", true));
	}

	@Test
	void testValidationAndVersionBoundary() {
		assertThrows(IllegalArgumentException.class,
				() -> builder.restoreRelation("sales", "orders")
						.statistic("relpages", ""));
		assertThrows(IllegalArgumentException.class,
				() -> builder.restoreRelation("sales", "orders")
						.sourceVersion(0));
		PostgresStatisticsBuilder postgres17 =
				new PostgresStatisticsBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.clearRelation("sales", "orders"));
	}
}
