/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerIndexMigrationTest {
	private Table table() {
		final var table = new Table("Events").setSchemaName("Sales");
		table.getColumns().add(new Column("a").setDataType(DataType.BIGINT));
		table.getColumns().add(new Column("b").setDataType(DataType.BIGINT));
		return table;
	}

	@Test
	void addsAndDropsXmlIndexUsingTheSameFactoriesAsStandaloneOperations() throws Exception {
		final var original = table();
		final var target = original.clone();
		final var index = new Index("by value").setIndexType(IndexType.BTree).setUnique(true);
		index.getColumns().add("a", Order.Desc);
		index.getIncludes().add("b");
		index.setWhere("a IS NOT NULL");
		index.getSpecifics().put(SpannerCreateIndexFactory.IS_NULL_FILTERED, true);
		target.getIndexes().add(index);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setQuateColumnName(true);
		registry.getOptions().setCreateIfNotExists(true);
		registry.getOptions().setDropIfExists(true);
		final var add = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, add.size());
		final String sql = add.get(0).getSqlText();
		assertEquals(registry.createSql(restored.getIndexes().get(0), SqlType.CREATE).get(0).getSqlText(), sql);
		assertTrue(sql.contains("UNIQUE NULL_FILTERED INDEX IF NOT EXISTS `Sales`.`by value` ON `Sales`.`Events`"),
				sql);
		assertTrue(sql.contains("`a` DESC"), sql);
		assertTrue(sql.contains("STORING"), sql);
		assertTrue(sql.contains("WHERE a IS NOT NULL"), sql);
		final var drop = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, drop.size());
		assertEquals("DROP INDEX IF EXISTS `Sales`.`by value`", drop.get(0).getSqlText());
		assertEquals(SqlType.DROP, drop.get(0).getSqlType());
		registry.getOptions().setCreateIfNotExists(false);
		assertFalse(registry.createSql(original.diff(restored), SqlType.ALTER).get(0).getSqlText()
				.contains("IF NOT EXISTS"));
		registry.getOptions().setDropIfExists(false);
		assertEquals("DROP INDEX `Sales`.`by value`",
				registry.createSql(restored.diff(original), SqlType.ALTER).get(0).getSqlText());
		assertTrue(registry.createSql(original.diff(original), SqlType.ALTER).isEmpty());
	}

	@Test
	void typeChangesDropUsingTheOriginalIndexType() {
		final var original = table();
		original.getColumns().add(new Column("tokens").setDataType(DataType.OTHER).setDataTypeName("TOKENLIST"));
		final var index = new Index("idx");
		index.getColumns().add("a");
		original.getIndexes().add(index);
		final var search = original.clone();
		search.getIndexes().get(0).setIndexType(IndexType.FullText);
		search.getIndexes().get(0).getColumns().clear();
		search.getIndexes().get(0).getColumns().add("tokens");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var toSearch = registry.createSql(original.diff(search), SqlType.ALTER);
		assertEquals(2, toSearch.size());
		assertTrue(toSearch.get(0).getSqlText().startsWith("DROP INDEX"));
		assertTrue(toSearch.get(1).getSqlText().startsWith("CREATE SEARCH INDEX"));
		final var toRegular = registry.createSql(search.diff(original), SqlType.ALTER);
		assertEquals(2, toRegular.size());
		assertTrue(toRegular.get(0).getSqlText().startsWith("DROP SEARCH INDEX"));
		assertTrue(toRegular.get(1).getSqlText().startsWith("CREATE INDEX"));
	}

	@Test
	void dropsModifiedIndexBeforeColumnDeletionAndRecreatesAfterColumnAddition() {
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
		assertTrue(operations.get(1).getSqlText().contains("DROP COLUMN"));
		assertTrue(operations.get(2).getSqlText().startsWith("CREATE INDEX"));
		final var extended = target.clone();
		extended.getColumns().add(new Column("c").setDataType(DataType.BIGINT));
		extended.getIndexes().get(0).getColumns().clear();
		extended.getIndexes().get(0).getColumns().add("c");
		final var additions = registry.createSql(target.diff(extended), SqlType.ALTER);
		assertEquals(3, additions.size());
		assertTrue(additions.get(0).getSqlText().startsWith("DROP INDEX"));
		assertTrue(additions.get(1).getSqlText().contains("ADD COLUMN"));
		assertTrue(additions.get(2).getSqlText().startsWith("CREATE INDEX"));
	}

	@Test
	void changesStoredColumnsInPlaceAroundTheTableColumnChanges() throws Exception {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final var type : java.util.List.of(IndexType.BTree, IndexType.Vector)) {
			final var original = table();
			final var index = new Index("idx").setIndexType(type);
			index.getColumns().add("a");
			index.getIncludes().add("b");
			original.getIndexes().add(index);
			final var target = original.clone();
			target.getIndexes().getFirst().getIncludes().clear();
			target.getColumns().remove("b");
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
			final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
			final String alter = "ALTER " + (type == IndexType.Vector ? "VECTOR INDEX" : "INDEX")
					+ " `Sales`.`idx`";
			assertEquals(2, operations.size());
			assertEquals(alter + " DROP STORED COLUMN `b`", operations.get(0).getSqlText());
			assertTrue(operations.get(1).getSqlText().contains("DROP COLUMN `b`"));
			final var restoredColumn = registry.createSql(restored.diff(original), SqlType.ALTER);
			assertEquals(2, restoredColumn.size());
			assertTrue(restoredColumn.get(0).getSqlText().contains("ADD COLUMN"));
			assertEquals(alter + " ADD STORED COLUMN `b`", restoredColumn.get(1).getSqlText());
		}
	}

	@Test
	void rejectsInPlaceChangesToManagedIndexesAndMissingStoredColumns() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		final var index = new Index("idx");
		index.getColumns().add("a");
		original.getIndexes().add(index);
		final var invalid = original.clone();
		invalid.getIndexes().getFirst().getIncludes().add("missing");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(invalid), SqlType.ALTER));
		index.getSpecifics().put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED, true);
		final var target = original.clone();
		target.getIndexes().getFirst().getIncludes().add("b");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	@Test
	void setsAndResetsVectorSearchWithoutRequiringTheDistanceType() {
		final var original = table();
		final var vector = new Index("vector_idx").setIndexType(IndexType.Vector);
		vector.getColumns().add("a");
		original.getIndexes().add(vector);
		final var changed = original.clone();
		final var options = changed.getIndexes().getFirst().getSpecifics();
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final String value : java.util.List.of("TRUE", "false")) {
			options.put("disable_search", value);
			final var operations = registry.createSql(original.diff(changed), SqlType.ALTER);
			assertEquals(1, operations.size());
			assertEquals("ALTER VECTOR INDEX `Sales`.`vector_idx` SET OPTIONS (disable_search = "
					+ value.toLowerCase(java.util.Locale.ROOT) + ")", operations.getFirst().getSqlText());
		}
		assertEquals("ALTER VECTOR INDEX `Sales`.`vector_idx` SET OPTIONS (disable_search = NULL)",
				registry.createSql(changed.diff(original), SqlType.ALTER).getFirst().getSqlText());
		options.put("disable_search", "unknown");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(changed), SqlType.ALTER));
	}

	@Test
	void usesSearchDropSyntaxAndSupportsVectorDrop() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDropIfExists(true);
		for (final var type : java.util.List.of(IndexType.FullText, IndexType.Vector, IndexType.BTree)) {
			final var table = table();
			final var index = new Index("idx").setIndexType(type);
			table.getIndexes().add(index);
			assertEquals("DROP " + (type == IndexType.FullText ? "SEARCH " : "") + "INDEX IF EXISTS `Sales`.`idx`",
					registry.createSql(index, SqlType.DROP).get(0).getSqlText());
		}
	}

	@Test
	void preservesNativeSearchAndVectorCreationWhenAddedThroughTableDiff() {
		final var original = table();
		original.getColumns().add(new Column("tokens").setDataType(DataType.OTHER).setDataTypeName("TOKENLIST"));
		final var embedding = new Column("embedding").setDataType(DataType.DOUBLE).setArrayDimension(1)
				.setNotNull(true);
		embedding.getSpecifics().put(com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder.VECTOR_LENGTH, 8);
		original.getColumns().add(embedding);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final var type : java.util.List.of(IndexType.FullText, IndexType.Vector)) {
			final var target = original.clone();
			final var index = new Index("idx").setIndexType(type).setVectorDistanceType(VectorDistanceType.Cosine);
			index.getColumns().add(type == IndexType.FullText ? "tokens" : "embedding");
			target.getIndexes().add(index);
			final String sql = registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText();
			assertEquals(registry.createSql(index, SqlType.CREATE).get(0).getSqlText(), sql);
			assertTrue(sql.contains("CREATE " + (type == IndexType.FullText ? "SEARCH" : "VECTOR") + " INDEX"), sql);
		}
	}

	@Test
	void rejectsUnsupportedOrIncompleteIndexesInsteadOfReturningAPlan() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Index("detached"), SqlType.CREATE));
		for (final var type : java.util.List.of(IndexType.Hash, IndexType.Gin)) {
			final var original = table();
			final var target = original.clone();
			final var index = new Index("bad").setIndexType(type);
			index.getColumns().add("a");
			target.getIndexes().add(index);
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
			assertThrows(UnsupportedOperationException.class, () -> registry.createSql(index, SqlType.DROP));
		}
		final var original = table();
		final var target = original.clone();
		target.getIndexes().add(new Index("empty"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		for (final var type : java.util.List.of(IndexType.FullText, IndexType.Vector)) {
			final var invalid = original.clone();
			final var specialized = new Index("special").setIndexType(type).setUnique(true);
			specialized.getColumns().add("a");
			invalid.getIndexes().add(specialized);
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(original.diff(invalid), SqlType.ALTER));
		}
		final var constrained = table();
		final var unique = new UniqueConstraint("owned");
		unique.getColumns().add("a");
		constrained.getConstraints().add(unique);
		final var withIndex = constrained.clone();
		final var owned = new Index("owned");
		owned.getColumns().add("a");
		withIndex.getIndexes().add(owned);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(constrained.diff(withIndex), SqlType.ALTER));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(owned, SqlType.DROP));
	}
}
