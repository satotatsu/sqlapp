/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.AtomicTableReplacementProvider;

class PostgresAtomicTableReplacementProviderTest {
	@Test
	void locksAllSelectedTablesInStableOrderWithConnectionLocalNames() {
		var dialect = DialectHolder.postgreSQL180;
		var provider = AtomicTableReplacementProvider.resolve(dialect);
		assertInstanceOf(PostgresAtomicTableReplacementProvider.class, provider);
		var child = new Table("Order detail").setSchemaName("sales").setCatalogName("target");
		var parent = new Table("order_header").setSchemaName("sales").setCatalogName("target");
		assertEquals(List.of("LOCK TABLE sales.\"Order detail\", sales.order_header IN EXCLUSIVE MODE"),
				provider.lockSql(dialect, List.of(parent, child)));
	}
}
