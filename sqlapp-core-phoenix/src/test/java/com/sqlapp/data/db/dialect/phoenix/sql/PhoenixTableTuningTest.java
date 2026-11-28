/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.phoenix.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixTableTuningTest {
	private Table table() {
		final var table = new Table("Events").setSchemaName("Sales");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		return table;
	}

	@Test
	void createsAndAltersNativeValuesThroughXmlIncludingExplicitGuidepostReset() throws Exception {
		final var registry = DialectHolder.defaultDialect4_9.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		for (String cache : List.of("ALWAYS", "never", "0", "000900000", Long.toString(Long.MAX_VALUE))) {
			for (String width : List.of("0", "10000000", "NULL", Long.toString(Long.MAX_VALUE))) {
				final var original = table();
				final var target = original.clone();
				target.getSpecifics().put(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, cache);
				target.getSpecifics().put(PhoenixDdlOptions.GUIDE_POSTS_WIDTH, width);
				final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
				final String create = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
				assertTrue(create.contains("GUIDE_POSTS_WIDTH=" + width));
				assertTrue(create.contains("UPDATE_CACHE_FREQUENCY="));
				final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
				assertEquals(2, operations.size());
				assertTrue(operations.get(1).getSqlText()
						.equals("ALTER TABLE \"Sales\".\"Events\" SET GUIDE_POSTS_WIDTH=" + width));
			}
		}
	}

	@Test
	void preservesVersionBoundariesAndRejectsUnsupportedRequests() {
		for (int minor : new int[] { 3, 6, 7, 8, 9 }) {
			final var dialect = (Phoenix) DialectResolver.getInstance().getDialect("Phoenix", 4, minor, 0);
			assertEquals(minor >= 7, dialect.supportsUpdateCacheFrequency());
			assertEquals(minor >= 9, dialect.supportsGuidePostsWidth());
			assertEquals(minor >= 8, dialect.supportsLimitOffset());
			for (String key : List.of(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, PhoenixDdlOptions.GUIDE_POSTS_WIDTH)) {
				final var table = table();
				table.getSpecifics().put(key, "100");
				if (minor < (key.equals(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY) ? 7 : 9)) {
					assertThrows(UnsupportedOperationException.class,
							() -> dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
					assertThrows(UnsupportedOperationException.class,
							() -> dialect.createSqlFactoryRegistry().createSql(table().diff(table), SqlType.ALTER));
				}
			}
		}
	}

	@Test
	void normalizesEquivalentValuesButDoesNotGuessRemovalSemantics() {
		final var registry = DialectHolder.defaultDialect4_9.createSqlFactoryRegistry();
		final var original = table();
		original.getSpecifics().put(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, "always");
		original.getSpecifics().put(PhoenixDdlOptions.GUIDE_POSTS_WIDTH, "00100");
		final var target = original.clone();
		target.getSpecifics().put(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, "ALWAYS");
		target.getSpecifics().put(PhoenixDdlOptions.GUIDE_POSTS_WIDTH, "100");
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).isEmpty());
		target.getSpecifics().remove(PhoenixDdlOptions.GUIDE_POSTS_WIDTH);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		target.getSpecifics().put(PhoenixDdlOptions.GUIDE_POSTS_WIDTH, "NULL");
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
		target.getSpecifics().remove(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void rejectsInvalidNumericOrLiteralValuesWithoutProducingPartialPlans() {
		final var registry = DialectHolder.defaultDialect4_9.createSqlFactoryRegistry();
		for (String key : List.of(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, PhoenixDdlOptions.GUIDE_POSTS_WIDTH)) {
			for (String text : List.of("", " ", "-1", "1.5", "9223372036854775808", "100; DROP TABLE T", "nonsense")) {
				final var target = table();
				target.getSpecifics().put(key, text);
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(target, SqlType.CREATE));
				assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(table().diff(target), SqlType.ALTER));
			}
		}
	}
}
