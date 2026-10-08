/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.ProductVersionInfo;

class PostgresCompositeQueryVersionTest {
	@Test
	void uniqueCoveringAndNullOptionsRespectCatalogBoundaries() {
		var reader = new PostgresUniqueConstraintReader(DialectHolder.postgreSQL150);
		for (int major : new int[] {8, 9, 10, 11, 14, 15, 18}) {
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(major).setMinorVersion(4)).toString();
			assertEquals(major >= 11, sql.contains("backing.indnkeyatts"), sql);
			assertEquals(major >= 15, sql.contains("backing.indnullsnotdistinct"), sql);
			assertTrue(sql.contains("AS is_included"), sql);
			assertTrue(sql.contains("AS nulls_not_distinct"), sql);
		}
	}
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
	void uniqueQueryUsesGenerateSubscriptsOnlyFrom84() {
		var reader = new PostgresUniqueConstraintReader(DialectHolder.postgreSQL150);
		for (int[] version : new int[][] {{8, 3}, {8, 4}, {9, 0}, {11, 0}, {15, 0}}) {
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0])
					.setMinorVersion(version[1])).toString();
			assertEquals(version[0] > 8 || version[1] >= 4, sql.contains("generate_subscripts"), sql);
			assertTrue(sql.contains("obj_description(c.constraint_oid, 'pg_constraint')"), sql);
		}
	}
	@Test
	void viewOptionsAreReadOnlyFrom92() {
		var reader = new PostgresViewReader(DialectHolder.postgreSQL150);
		for (int[] version : new int[][] {{8, 4}, {9, 1}, {9, 2}, {11, 0}, {15, 0}}) {
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0])
					.setMinorVersion(version[1])).toString();
			assertEquals(version[0] > 9 || version[0] == 9 && version[1] >= 2, sql.contains("c.reloptions"), sql);
			assertTrue(sql.contains("AS view_options"), sql);
		}
	}

	@Test
	void constraintOrdinalityAvoidsLateralAndKeepsPre84Fallback() {
		var foreignKeys = new PostgresForeignKeyConstraintReader(DialectHolder.postgreSQL150);
		var uniqueKeys = new PostgresUniqueConstraintReader(DialectHolder.postgreSQL150);
		for (int[] version : new int[][] {{8,0}, {8,3}, {8,4}, {9,2}, {9,3}, {11,0}, {15,0}}) {
			var info = new ProductVersionInfo().setMajorVersion(version[0]).setMinorVersion(version[1]);
			String fk = foreignKeys.getSqlSqlNode(info).toString();
			String unique = uniqueKeys.getSqlSqlNode(info).toString();
			for (String sql : java.util.List.of(fk,unique)) {
				boolean modern = version[0] > 8 || version[1] >= 4;
				assertEquals(modern,sql.contains("generate_subscripts(source.conkey, 1)"),sql);
				assertEquals(!modern,sql.contains("generate_series(array_lower(source.conkey, 1)"),sql);
				assertFalse(sql.contains("JOIN generate_"),sql);
				assertFalse(sql.contains("pg_depend"),sql);
				assertTrue(sql.contains("AS key_position"),sql);
				assertTrue(sql.contains("source.oid AS constraint_oid"),sql);
				assertTrue(sql.contains("c.connamespace = nc.oid"),sql);
				assertTrue(sql.contains("c.conkey[c.key_position]"),sql);
			}
			assertTrue(fk.contains("c.confkey[c.key_position]"),fk);
			assertFalse(fk.contains("RISTRICT"),fk);
			assertTrue(fk.contains("THEN 'RESTRICT'"),fk);
		}
	}

}
