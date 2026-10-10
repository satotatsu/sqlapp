/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.ProductVersionInfo;

class PostgresIndexQueryVersionTest {
	@Test
	void isolatesCatalogPredicateAndSortFieldsToModernQueries() {
		for (int major : new int[] { 10, 11, 14, 15 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
			var reader = new PostgresIndexReader(dialect);
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(major)).toString();
			assertEquals(major >= 11, sql.contains("pg_get_expr(i.indpred,i.indrelid) AS predicate"));
			assertEquals(major >= 11, sql.contains("AS is_desc"));
			assertEquals(major >= 11, sql.contains("AS nulls_first"));
			assertEquals(major >= 15, sql.contains("i.indnullsnotdistinct,"));
		}
	}

	@Test
	void isolatesCollationCatalogsTo91AndRetainsIndexDetailsInAllQueries() {
		for (int[] version : new int[][] {{8,4},{9,0},{9,1},{9,6},{10,0},{11,0},{15,0},{18,0}}) {
			var dialect = DialectResolver.getInstance().getDialect("postgres",version[0],version[1],null);
			var reader = new PostgresIndexReader(dialect);
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0]).setMinorVersion(version[1])).toString();
			assertEquals(version[0]>9 || version[0]==9 && version[1]>=1, sql.contains("pg_catalog.pg_collation"));
			assertTrue(sql.contains("AS key_sql"));
			assertTrue(sql.contains("AS index_options"));
			assertTrue(sql.contains("AS index_tablespace"));
		}
	}
}
