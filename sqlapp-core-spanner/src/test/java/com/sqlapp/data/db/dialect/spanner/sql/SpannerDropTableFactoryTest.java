/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SpannerDropTableFactoryTest {
	@Test
	void distinguishesSameNamedTablesInDifferentSchemasForNamedReferences() {
		final var parent = table().setName("Parent").setSchemaName("Archive");
		final var child = table().setName("Parent").setSchemaName("Sales");
		final var reference = new ForeignKeyConstraint("CrossSchema").setRelatedTableName("Parent")
				.setRelatedTableSchemaName("Archive");
		reference.getColumns().add(child.getColumns().getFirst());
		reference.getRelatedColumns().add("id");
		child.getConstraints().add(reference);
		assertEquals("Archive", reference.getRelatedTableSchemaName());
		final SqlFactory<Table> factory = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.getSqlFactory(parent, SqlType.DROP);
		final var operations = factory.createSql(List.of(child, parent));
		assertEquals(2, operations.size());
		assertTrue(operations.getFirst().getSqlText().contains("`Sales`.`Parent`"));
		assertTrue(operations.getLast().getSqlText().contains("`Archive`.`Parent`"));
	}

	@Test
	void retainsBoundColumnReferencesWhenTheTargetNameIsOmitted() {
		final var parent = table().setName("Parent");
		final var child = table().setName("Child");
		child.getConstraints().add(new ForeignKeyConstraint("ChildParent",
				child.getColumns().getFirst(), parent.getColumns().getFirst()).setRelatedTableName(null));
		final SqlFactory<Table> factory = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.getSqlFactory(parent, SqlType.DROP);
		final var operations = factory.createSql(List.of(child, parent));
		assertEquals(2, operations.size());
		assertTrue(operations.getFirst().getSqlText().contains("`Sales`.`Child`"));
		assertTrue(operations.getLast().getSqlText().contains("`Sales`.`Parent`"));
	}

	@Test
	void resolvesNamedForeignKeysWithoutAContainingSchema() {
		final var parent = table().setName("Parent");
		final var child = table().setName("Child");
		final var reference = new ForeignKeyConstraint("ChildParent").setRelatedTableName("Parent")
				.setRelatedTableSchemaName("Sales");
		reference.getColumns().add(child.getColumns().getFirst());
		reference.getRelatedColumns().add("id");
		child.getConstraints().add(reference);
		final SqlFactory<Table> factory = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.getSqlFactory(parent, SqlType.DROP);
		final var operations = factory.createSql(List.of(child, parent));
		assertEquals(2, operations.size());
		assertTrue(operations.getFirst().getSqlText().contains("`Sales`.`Child`"));
		assertTrue(operations.getLast().getSqlText().contains("`Sales`.`Parent`"));
	}

	@Test
	void ordersForeignKeyOnlyDropBatchesReferencingTableFirst() {
		final var schema = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var parent = table().setName("AParent");
		final var child = table().setName("ZChild");
		schema.getTables().add(parent);
		schema.getTables().add(child);
		child.getConstraints().add(new ForeignKeyConstraint("ChildParent",
				child.getColumns().getFirst(), parent.getColumns().getFirst()));
		final SqlFactory<Table> factory = DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.getSqlFactory(parent, SqlType.DROP);
		final var operations = factory.createSql(List.of(parent, child));
		assertEquals(2, operations.size());
		assertTrue(operations.getFirst().getSqlText().contains("`Sales`.`ZChild`"));
		assertTrue(operations.getLast().getSqlText().contains("`Sales`.`AParent`"));
	}

	@Test
	void rejectsDuplicateAndCyclicDirectDropBatches() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final SqlFactory<Table> factory = registry.getSqlFactory(table(), SqlType.DROP);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(List.of(table(), table())));
		final var first = table().setName("First");
		final var second = table().setName("Second");
		for (final var table : List.of(first, second)) {
			table.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME,
					table == first ? "Second" : "First");
			table.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
			table.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		}
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(List.of(first, second)));
		assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(List.of(
				new DbObjectDifference(first, null, new EqualsHandler()),
				new DbObjectDifference(second, null, new EqualsHandler()))));
	}

	@Test
	void ordersDirectTableAndDifferenceBatchesChildFirst() {
		final var schema = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var parent = table().setName("ZParent");
		final var child = table().setName("AChild");
		child.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "ZParent");
		child.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
		child.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		child.getIndexes().add(new Index("ById").setIndexType(IndexType.BTree));
		schema.getTables().add(parent);
		schema.getTables().add(child);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDropIfExists(false);
		final SqlFactory<Table> factory = registry.getSqlFactory(parent, SqlType.DROP);
		final var operations = factory.createSql(List.of(parent, child));
		assertEquals(3, operations.size());
		assertEquals("DROP INDEX `Sales`.`ById`", operations.get(0).getSqlText());
		assertEquals("DROP TABLE `Sales`.`AChild`", operations.get(1).getSqlText());
		assertEquals("DROP TABLE `Sales`.`ZParent`", operations.get(2).getSqlText());
		final var differences = factory.createDiffSql(List.of(
				new DbObjectDifference(parent, null, new EqualsHandler()),
				new DbObjectDifference(child, null, new EqualsHandler())));
		assertEquals(operations.stream().map(operation -> operation.getSqlText()).toList(),
				differences.stream().map(operation -> operation.getSqlText()).toList());
	}

	@Test
	void dropsEveryUserIndexBeforeTheTableAndLeavesManagedIndexesToTheService() {
		final var table = table();
		for (final var type : List.of(IndexType.BTree, IndexType.FullText, IndexType.Vector))
			table.getIndexes().add(new Index(type.name()).setIndexType(type));
		final var managed = new Index("Backing");
		managed.getSpecifics().put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED, true);
		table.getIndexes().add(managed);
		table.getConstraints().addUniqueConstraint("UniqueId", table.getColumns().getFirst());
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDropIfExists(true);
		final var operations = registry.createSql(table, SqlType.DROP);
		assertEquals(5, operations.size());
		assertEquals("DROP INDEX IF EXISTS `Sales`.`BTree`", operations.get(0).getSqlText());
		assertEquals("DROP SEARCH INDEX IF EXISTS `Sales`.`FullText`", operations.get(1).getSqlText());
		assertEquals("DROP INDEX IF EXISTS `Sales`.`Vector`", operations.get(2).getSqlText());
		assertEquals("DROP INDEX IF EXISTS `Sales`.`UniqueId`", operations.get(3).getSqlText());
		assertEquals("DROP TABLE IF EXISTS `Sales`.`Order`", operations.getLast().getSqlText());
		assertEquals(4, table.getIndexes().size());
	}

	@Test
	void dropsConstraintIndexesOnceThroughSchemaDifferences() {
		final var original = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var table = table();
		table.getConstraints().addUniqueConstraint("UniqueId", table.getColumns().getFirst());
		table.getIndexes().add(new Index("UniqueId").setIndexType(IndexType.BTree));
		original.getTables().add(table);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDropIfExists(false);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(table.getIndexes().getFirst(), SqlType.DROP));
		final var operations = registry.createSql(original.diff(new Schema("Sales")));
		assertEquals(2, operations.size());
		assertEquals("DROP INDEX `Sales`.`UniqueId`", operations.getFirst().getSqlText());
		assertEquals("DROP TABLE `Sales`.`Order`", operations.getLast().getSqlText());
	}

	@Test
	void rejectsInvalidTableNamesAndManagedFlags() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class,
				() -> registry.createSql(new Table(" "), SqlType.DROP));
		final var table = table();
		final var index = new Index("BadFlag");
		index.getSpecifics().put(SpannerCreateIndexFactory.SPANNER_IS_MANAGED, "unknown");
		table.getIndexes().add(index);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.DROP));
	}

	private Table table() {
		final var table = new Table("Order").setSchemaName("Sales");
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
		table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().getFirst());
		return table;
	}
}
