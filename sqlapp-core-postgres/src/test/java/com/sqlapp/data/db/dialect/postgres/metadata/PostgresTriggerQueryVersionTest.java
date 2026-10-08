/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;

class PostgresTriggerQueryVersionTest {
	@Test
	void excludesInternalTriggersOnlyOnCatalogsSupportingTheFlag() throws Exception {
		for (int version : new int[] {8, 9, 11, 15}) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", version, version == 8 ? 4 : 0, null);
			var reader = dialect.getCatalogReader().getSchemaReader().getTriggerReader();
			assertEquals(version >= 9, reader instanceof Postgres90TriggerReader);
			assertEquals(version >= 9, ((PostgresTriggerReader) reader).getSqlSqlNode(null).toString().contains("AND NOT t.tgisinternal"));
		}
		assertFalse(query("triggers.sql").contains("tgisinternal"));
		String modern = query("triggers90.sql");
		assertTrue(modern.contains("AND NOT t.tgisinternal"));
		assertTrue(modern.contains("pg_get_triggerdef(t.oid) AS definition"));
	}

	private String query(String name) throws Exception {
		try (var input = PostgresTriggerReader.class.getResourceAsStream(name)) {
			return new String(java.util.Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
