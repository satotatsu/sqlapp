package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresCopyToBuilderTest {

	@Test
	void testCopyTableToStdout() {
		String sql = new PostgresCopyToBuilder(
				DialectHolder.postgreSQL170, "public", "orders")
				.column("id")
				.column("total")
				.format("csv")
				.header(true)
				.build();

		assertEquals(
				"COPY public.orders (id, total) TO STDOUT WITH (FORMAT csv, HEADER true)",
				sql);
	}

	@Test
	void testCopyMaterializedViewOnPostgres18() {
		String sql = new PostgresCopyToBuilder(
				DialectHolder.postgreSQL180, "sales_summary")
				.materializedView(true)
				.format("csv")
				.forceQuoteAll(true)
				.build();

		assertEquals(
				"COPY sales_summary TO STDOUT WITH (FORMAT csv, FORCE_QUOTE *)",
				sql);
	}

	@Test
	void testRejectMaterializedViewBeforePostgres18() {
		PostgresCopyToBuilder builder = new PostgresCopyToBuilder(
				DialectHolder.postgreSQL170, "sales_summary")
				.materializedView(true);

		assertThrows(IllegalArgumentException.class, builder::build);
	}
}
