/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.ProductVersionInfo;

class PostgresIndexQueryVersionTest {
	@Test
	void isolatesCatalogPredicateAndSortFieldsToModernQueries() {
		for (int major : new int[] {10, 11, 14, 15}) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
			var reader = new PostgresIndexReader(dialect);
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(major)).toString();
			assertEquals(major >= 11, sql.contains("pg_get_expr(i.indpred,i.indrelid) AS predicate"));
			assertEquals(major >= 11, sql.contains("AS is_desc"));
			assertEquals(major >= 15, sql.contains("i.indnullsnotdistinct,"));
		}
	}
}
