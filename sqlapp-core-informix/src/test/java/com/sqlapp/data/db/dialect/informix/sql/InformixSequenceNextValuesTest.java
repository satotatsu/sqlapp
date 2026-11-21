/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class InformixSequenceNextValuesTest {
	@Test
	void bindsOneCountAndUsesAnUnlimitedByCatalogRowSourceAcrossSupportedVersions() {
		for (int version : new int[] { 11, 12, 14, 15 }) {
			final var dialect = DialectResolver.getInstance().getDialect("Informix", version, version == 11 ? 70 : 10,
					null);
			final var sequence = new Sequence("Order sequence").setSchemaName("owner").setCatalogName("db");
			assertTrue(dialect.supportsSequencePreallocation());
			final var nodes = dialect.createSqlFactoryRegistry().createSqlNodes(sequence, SqlType.SEQUENCE_NEXT_VALUES);
			assertEquals(1, nodes.size());
			for (int count : new int[] { 0, 1, 1000, Integer.MAX_VALUE }) {
				final var parameters = nodes.get(0).eval(count);
				final String sql = parameters.getSql();
				assertTrue(sql.contains("FIRST ? \"owner\".\"Order sequence\".NEXTVAL"), sql);
				assertEquals(1, sql.chars().filter(c -> c == '?').count());
				assertEquals(10, sql.split("TABLE\\(SET\\{", -1).length - 1);
				assertFalse(sql.contains("systables"));
				assertFalse(sql.contains("WITH"));
			}
		}
	}

	@Test
	void rejectsAnUnnamedSequence() {
		final var dialect = com.sqlapp.data.db.dialect.informix.DialectHolder.defaultDialect;
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(new Sequence(), SqlType.SEQUENCE_NEXT_VALUES));
	}

	@Test
	void escapesEmbeddedIdentifierQuotes() {
		final var dialect = com.sqlapp.data.db.dialect.informix.DialectHolder.defaultDialect;
		final var sequence = new Sequence("odd\"sequence").setSchemaName("odd\"owner");
		assertTrue(dialect.createSqlFactoryRegistry().createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0)
				.getSqlText().contains("\"odd\"\"owner\".\"odd\"\"sequence\".NEXTVAL"));
	}
}
