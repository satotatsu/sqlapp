/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixIndexMigrationTest {
	private Table table() {
		final var table = new Table("events").setSchemaName("Sales");
		table.getColumns().add(new Column("a").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("b").setDataType(DataType.BIGINT));
		table.getColumns().get("b").getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "details");
		return table;
	}

	@Test
	void xmlAddsAndDropsShareNativeFactoriesOnAllVersions() throws Exception {
		for (final var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect4_9,
				DialectHolder.defaultDialect5_3_1)) {
			final var original = table();
			final var target = original.clone();
			final var index = new Index("by_value");
			index.getColumns().add("b", Order.Desc);
			index.getIncludes().add("a");
			index.getSpecifics().put("phoenix.indexType", "LOCAL");
			target.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			registry.getOptions().setQuateColumnName(true);
			registry.getOptions().setCreateIfNotExists(true);
			registry.getOptions().setDropIfExists(true);
			final var additions = registry.createSql(original.diff(restored), SqlType.ALTER);
			assertEquals(1, additions.size());
			assertEquals(registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText(),
					additions.get(0).getSqlText());
			assertTrue(additions.get(0).getSqlText().contains("CREATE LOCAL INDEX IF NOT EXISTS"));
			assertTrue(additions.get(0).getSqlText().contains("\"details\".\"b\" DESC"));
			final var drops = registry.createSql(restored.diff(original), SqlType.ALTER);
			assertEquals(1, drops.size());
			assertEquals("DROP INDEX IF EXISTS \"by_value\" ON \"Sales\".\"events\"", drops.get(0).getSqlText());
			registry.getOptions().setDropIfExists(false);
			assertEquals("DROP INDEX \"by_value\" ON \"Sales\".\"events\"",
					registry.createSql(restored.diff(original), SqlType.ALTER).get(0).getSqlText());
		}
	}

	@Test
	void replacesBeforeColumnDropsAndAfterColumnAdds() {
		final var original = table();
		final var index = new Index("idx");
		index.getColumns().add("a");
		original.getIndexes().add(index);
		final var target = original.clone();
		target.getIndexes().get(0).getColumns().clear();
		target.getIndexes().get(0).getColumns().add("b");
		target.getColumns().remove("a");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(3, operations.size());
		assertTrue(operations.get(0).getSqlText().startsWith("DROP INDEX"));
		assertTrue(operations.get(0).getSqlText().contains(" ON "));
		assertTrue(operations.get(1).getSqlText().contains("DROP COLUMN"));
		assertTrue(operations.get(2).getSqlText().startsWith("CREATE INDEX"));
		final var extended = target.clone();
		extended.getColumns().add(new Column("c").setDataType(DataType.BIGINT));
		extended.getIndexes().get(0).getColumns().clear();
		extended.getIndexes().get(0).getColumns().add("c");
		final var additions = registry.createSql(target.diff(extended), SqlType.ALTER);
		assertEquals(3, additions.size());
		assertTrue(additions.get(0).getSqlText().startsWith("DROP INDEX"));
		assertTrue(additions.get(1).getSqlText().contains(" ADD "));
		assertTrue(additions.get(2).getSqlText().startsWith("CREATE INDEX"));
	}

	@Test
	void supportsViewIndexDiffWithoutRecreatingItsQuery() {
		final var original = new View("filtered");
		original.setStatement("SELECT * FROM events WHERE a = 1");
		original.getColumns().add(new Column("b").setDataType(DataType.BIGINT));
		final var target = original.clone();
		final var index = new Index("view_idx");
		index.getColumns().add("b");
		target.getIndexes().add(index);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals(1, registry.createSql(original.diff(target), SqlType.ALTER).size());
		assertTrue(registry.createSql(target.diff(original), SqlType.ALTER).get(0).getSqlText()
				.endsWith(" ON \"filtered\""));
	}

	@Test
	void rejectsUnrepresentableTargetRatherThanLosingSemantics() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (int mode = 0; mode < 4; mode++) {
			final var original = table();
			final var target = original.clone();
			final var index = new Index("bad");
			index.getColumns().add("a");
			switch (mode) {
			case 0 -> index.setIndexType(IndexType.Hash);
			case 1 -> index.setWhere("a IS NOT NULL");
			case 2 -> index.getSpecifics().put("phoenix.indexState", "DISABLE");
			case 3 -> index.setUnique(true);
			}
			target.getIndexes().add(index);
			final Class<? extends RuntimeException> expected = mode == 3 ? IllegalArgumentException.class
					: UnsupportedOperationException.class;
			assertThrows(expected, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		final var orphan = new Index("orphan");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(orphan, SqlType.DROP));
	}

	@Test
	void rejectsTargetIndexesThatStillReferenceDeletedKeysOrIncludes() {
		for (boolean include : new boolean[] { false, true }) {
			final var original = table();
			final var index = new Index("idx");
			index.getColumns().add(include ? "b" : "a");
			if (include)
				index.getIncludes().add("a");
			original.getIndexes().add(index);
			final var target = original.clone();
			target.getColumns().remove("a");
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
	}
}
