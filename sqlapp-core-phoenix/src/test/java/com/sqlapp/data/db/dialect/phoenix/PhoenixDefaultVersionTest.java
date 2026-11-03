/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixDefaultVersionTest {
	@Test
	void resolvesDefaultBoundaryWithoutChangingValuesBoundary() {
		for (int[] version : new int[][] { { 0, 0, 0 }, { 3, 9, 0 }, { 4, 8, 99 }, { 4, 9, 0 }, { 4, 16, 0 },
				{ 5, 0, 0 }, { 5, 3, 0 }, { 5, 3, 1 }, { 6, 0, 0 } }) {
			final var dialect = (Phoenix) DialectResolver.getInstance().getDialect("Apache Phoenix", version[0],
					version[1], version[2]);
			assertEquals(version[0] > 4 || (version[0] == 4 && version[1] >= 9), dialect.supportsColumnDefault());
			assertEquals(
					version[0] > 5 || (version[0] == 5 && (version[1] > 3 || (version[1] == 3 && version[2] >= 1))),
					dialect.supportsValues());
		}
		final var unknownRevision = (Phoenix) DialectResolver.getInstance().getDialect("Phoenix", 5, 3, null);
		assertTrue(unknownRevision.supportsColumnDefault());
		assertFalse(unknownRevision.supportsValues());
	}

	@Test
	void generatesCreateAndAddDefaultsThroughXmlOnlyOnSupportedVersions() throws Exception {
		final var original = new Table("events");
		original.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		final var target = original.clone();
		target.getColumns().add(new Column("value").setDataType(DataType.INT).setDefaultValue("1 + 2"));
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		for (final var dialect : java.util.List.of(DialectHolder.defaultDialect4_9,
				DialectHolder.defaultDialect5_3_1)) {
			final var registry = dialect.createSqlFactoryRegistry();
			assertTrue(registry.createSql(restored, SqlType.CREATE).get(0).getSqlText().contains("DEFAULT 1 + 2"));
			assertTrue(registry.createSql(original.diff(restored), SqlType.ALTER).get(0).getSqlText()
					.contains("DEFAULT 1 + 2"));
			final var required = new Column("id").setDataType(DataType.INT).setNotNull(true).setDefaultValue("1");
			assertEquals("INTEGER NOT NULL DEFAULT 1",
					dialect.createSqlBuilder().definition(required, false).toString());
			assertEquals("1", required.getDefaultValue());
		}
		final var legacy = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(UnsupportedOperationException.class, () -> legacy.createSql(restored, SqlType.CREATE));
		assertThrows(UnsupportedOperationException.class,
				() -> legacy.createSql(original.diff(restored), SqlType.ALTER));
		assertFalse(legacy.createSql(original, SqlType.CREATE).isEmpty());
	}

	@Test
	void rejectsIgnoredOnUpdateAndUnsupportedChecksOnEveryVersion() {
		for (final var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_9,
				DialectHolder.defaultDialect5_3_1)) {
			final var column = new Column("value").setDataType(DataType.INT).setOnUpdate("1");
			assertThrows(UnsupportedOperationException.class, () -> dialect.createSqlBuilder().definition(column));
			column.setOnUpdate(null).setCheck("value > 0");
			assertThrows(UnsupportedOperationException.class, () -> dialect.createSqlBuilder().definition(column));
		}
	}
}
