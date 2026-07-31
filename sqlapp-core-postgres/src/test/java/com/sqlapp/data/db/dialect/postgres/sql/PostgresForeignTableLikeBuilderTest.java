package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresForeignTableLikeBuilderTest {

	@Test
	void testCreateForeignTableLike() {
		assertEquals(
				"CREATE FOREIGN TABLE IF NOT EXISTS archive.orders_remote "
						+ "(LIKE public.orders INCLUDING ALL EXCLUDING STATISTICS) "
						+ "SERVER reporting_server OPTIONS (schema_name 'sales', table_name 'orders''2026')",
				new PostgresForeignTableLikeBuilder(DialectHolder.postgreSQL180,
						"orders_remote")
						.schema("archive")
						.like("public", "orders")
						.including("ALL")
						.excluding("STATISTICS")
						.server("reporting_server")
						.option("schema_name", "sales")
						.option("table_name", "orders'2026")
						.ifNotExists(true)
						.build());
	}

	@Test
	void testRejectMissingRequiredValues() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresForeignTableLikeBuilder(
						DialectHolder.postgreSQL180, "orders_remote")
						.server("reporting_server").build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresForeignTableLikeBuilder(
						DialectHolder.postgreSQL180, "orders_remote")
						.like("orders").build());
	}

	@Test
	void testRejectUnsupportedLikeOption() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresForeignTableLikeBuilder(
						DialectHolder.postgreSQL180, "orders_remote")
						.including("INDEXES"));
	}

	@Test
	void testRejectBeforePostgres18() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresForeignTableLikeBuilder(
						DialectHolder.postgreSQL170, "orders_remote")
						.like("orders")
						.server("reporting_server")
						.build());
	}
}
