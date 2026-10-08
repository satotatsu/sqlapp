/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.ProductVersionInfo;

class PostgresEnumQueryVersionTest {
	@Test
	void usesCatalogSortOrderOnlyFrom91() {
		var reader = new PostgresEnumReader(DialectHolder.postgreSQL150);
		for (int[] version : new int[][] {{8, 3}, {9, 0}, {9, 1}, {11, 0}, {15, 0}}) {
			String sql = reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(version[0])
					.setMinorVersion(version[1])).toString();
			boolean modern = version[0] > 9 || version[0] == 9 && version[1] >= 1;
			assertEquals(modern, sql.contains("e.enumsortorder"), sql);
			assertEquals(!modern, sql.contains("t.typname, e.oid"), sql);
			assertTrue(sql.contains("obj_description(t.oid, 'pg_type')"), sql);
		}
	}
	@Test
	void objectCommentsUseTheContainingSystemCatalog() throws Exception {
		for (String name : new String[] { "domains.sql", "types.sql", "indexes.sql", "indexes110.sql", "indexes150.sql" }) {
			try (var input = PostgresEnumReader.class.getResourceAsStream(name)) {
				String sql = new String(java.util.Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				String catalog = name.startsWith("indexes") ? "pg_class" : "pg_type";
				assertTrue(sql.contains("'" + catalog + "'"), sql);
				assertFalse(sql.contains("current_database())"), sql);
			}
		}
	}

}
