/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.ProductVersionInfo;

class PostgresCompositeQueryVersionTest {
	@Test
	void collationsAreReadOnlyFrom91() {
		var reader = new PostgresTypeReader(DialectHolder.postgreSQL150);
		for (int[] version : new int[][] {{8, 3}, {9, 0}, {9, 1}, {11, 0}, {15, 0}}) {
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0])
					.setMinorVersion(version[1])).toString();
			assertEquals(version[0] > 9 || version[0] == 9 && version[1] >= 1, sql.contains("a.attcollation"), sql);
			assertFalse(sql.contains("pg_get_ruledef"), sql);
			assertTrue(sql.contains("NOT a.attisdropped"), sql);
			assertTrue(sql.contains("ORDER BY a.attnum"), sql);
		}
	}

	@Test
	void uniqueKeyOrdinalityIsReadOnlyFrom84() {
		var reader = new PostgresUniqueConstraintReader(DialectHolder.postgreSQL150);
		for (int[] version : new int[][] {{8, 3}, {8, 4}, {9, 0}, {11, 0}, {15, 0}}) {
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0])
					.setMinorVersion(version[1])).toString();
			assertEquals(version[0] > 8 || version[1] >= 4, sql.contains("generate_subscripts"), sql);
			assertTrue(sql.contains("obj_description(c.oid, 'pg_constraint')"), sql);
		}
	}
}
