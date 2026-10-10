/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.ProductVersionInfo;
class PostgresSequenceQueryVersionTest {
	@Test void isolatesPgSequencesToPostgres10AndLater() {
		for (int major : new int[] {8,9,10,11,15,18}) {
			var reader = new PostgresSequenceReader(DialectResolver.getInstance().getDialect("postgres", major, major<10 ? 4 : 0, null));
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(major)).toString();
			assertEquals(major>=10, sql.contains("pg_catalog.pg_sequences"));
			assertEquals(major<10, sql.contains("information_schema.sequences"));
		}
	}

	@Test void pre10ColumnsDoNotReadIdentityCatalogs() {
		for (int minor : new int[] {3,4,5,6}) {
			var dialect = DialectResolver.getInstance().getDialect("postgres",9,minor,null);
			var reader = new Postgres93ColumnReader(dialect);
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(9).setMinorVersion(minor)).toString();
			assertFalse(sql.contains("pg_sequence"));
			assertTrue(sql.contains("quote_ident(n.nspname)"));
		}
	}
}
