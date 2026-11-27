/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;

class SpannerAlterSchemaFactoryTest {
	@Test
	void distinguishesSelfReferencesFromForeignKeyCycles() {
		final var empty = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var populated = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var first = table("First");
		first.getConstraints().add(new ForeignKeyConstraint("SelfReference",
				first.getColumns().getFirst(), first.getColumns().getFirst()));
		populated.getTables().add(first);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertEquals(1, registry.createSql(empty.diff(populated)).size());
		assertEquals(1, registry.createSql(populated.diff(empty)).size());
		final var second = table("Second");
		populated.getTables().add(second);
		first.getConstraints().add(new ForeignKeyConstraint("FirstSecond",
				first.getColumns().getFirst(), second.getColumns().getFirst()));
		second.getConstraints().add(new ForeignKeyConstraint("SecondFirst",
				second.getColumns().getFirst(), first.getColumns().getFirst()));
		final var added = registry.createSql(empty.diff(populated));
		assertEquals(5, added.size());
		assertTrue(added.get(0).getSqlText().startsWith("CREATE TABLE"));
		assertTrue(added.get(1).getSqlText().startsWith("CREATE TABLE"));
		assertTrue(added.subList(2, 5).stream().allMatch(operation -> operation.getSqlText().contains("ADD CONSTRAINT")));
		final var removed = registry.createSql(populated.diff(empty));
		assertEquals(5, removed.size());
		assertTrue(removed.subList(0, 3).stream().allMatch(operation -> operation.getSqlText().contains("DROP CONSTRAINT")));
		assertTrue(removed.get(3).getSqlText().startsWith("DROP TABLE"));
		assertTrue(removed.get(4).getSqlText().startsWith("DROP TABLE"));
		assertEquals(3, first.getConstraints().size());
		assertEquals(2, second.getConstraints().size());
	}

	@Test
	void ordersForeignKeyOnlyChainsWithoutInterleaveMetadata() {
		final var empty = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var populated = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var parent = table("AParent");
		final var child = table("MChild");
		final var leaf = table("ZLeaf");
		child.getConstraints().add(new ForeignKeyConstraint("ChildParent",
				child.getColumns().getFirst(), parent.getColumns().getFirst()));
		leaf.getConstraints().add(new ForeignKeyConstraint("LeafChild",
				leaf.getColumns().getFirst(), child.getColumns().getFirst()));
		populated.getTables().add(leaf);
		populated.getTables().add(parent);
		populated.getTables().add(child);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var added = registry.createSql(empty.diff(populated));
		assertEquals(3, added.size());
		assertTrue(added.get(0).getSqlText().startsWith("CREATE TABLE IF NOT EXISTS `Sales`.`AParent`"));
		assertTrue(added.get(1).getSqlText().startsWith("CREATE TABLE IF NOT EXISTS `Sales`.`MChild`"));
		assertTrue(added.get(2).getSqlText().startsWith("CREATE TABLE IF NOT EXISTS `Sales`.`ZLeaf`"));
		final var deleted = registry.createSql(populated.diff(empty));
		assertEquals(3, deleted.size());
		assertTrue(deleted.get(0).getSqlText().contains("`Sales`.`ZLeaf`"));
		assertTrue(deleted.get(1).getSqlText().contains("`Sales`.`MChild`"));
		assertTrue(deleted.get(2).getSqlText().contains("`Sales`.`AParent`"));
	}

	@Test
	void rejectsCyclicInterleaveDependenciesInSchemaDifferences() {
		final var empty = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var cyclic = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		for (String name : java.util.List.of("First", "Second")) {
			final var table = table(name);
			table.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME,
					"First".equals(name) ? "Second" : "First");
			table.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
			table.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
			cyclic.getTables().add(table);
		}
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(empty.diff(cyclic)));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(cyclic.diff(empty)));
	}

	@Test
	void ordersAddedAndDeletedInterleaveChainsInOppositeDirections() {
		final var empty = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var populated = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		final var leaf = table("ALeaf");
		leaf.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "BChild");
		leaf.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
		leaf.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		final var child = table("BChild");
		child.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "ZParent");
		child.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
		child.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
		populated.getTables().add(leaf);
		populated.getTables().add(table("ZParent"));
		populated.getTables().add(child);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var added = registry.createSql(empty.diff(populated));
		assertEquals(3, added.size());
		assertTrue(added.get(0).getSqlText().startsWith("CREATE TABLE IF NOT EXISTS `Sales`.`ZParent`"));
		assertTrue(added.get(1).getSqlText().startsWith("CREATE TABLE IF NOT EXISTS `Sales`.`BChild`"));
		assertTrue(added.get(2).getSqlText().startsWith("CREATE TABLE IF NOT EXISTS `Sales`.`ALeaf`"));
		final var deleted = registry.createSql(populated.diff(empty));
		assertEquals(3, deleted.size());
		assertTrue(deleted.get(0).getSqlText().contains("`Sales`.`ALeaf`"));
		assertTrue(deleted.get(1).getSqlText().contains("`Sales`.`BChild`"));
		assertTrue(deleted.get(2).getSqlText().contains("`Sales`.`ZParent`"));
	}

	@Test
	void removesAliasesBeforeReplacingTablesAndAddsThemAfterwards() {
		final var original = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		original.getTables().add(table("OldOrders"));
		original.getSynonyms().add(new Synonym("Legacy").setObjectName("OldOrders"));
		final var target = original.clone();
		target.getTables().clear();
		target.getTables().add(table("NewOrders"));
		target.getSynonyms().getFirst().setObjectName("NewOrders");
		final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target));
		assertEquals(4, operations.size());
		assertEquals("ALTER TABLE `Sales`.`OldOrders` DROP SYNONYM `Sales`.`Legacy`",
				operations.getFirst().getSqlText());
		assertEquals("ALTER TABLE `Sales`.`NewOrders` ADD SYNONYM `Sales`.`Legacy`",
				operations.getLast().getSqlText());
		assertTrue(operations.stream().anyMatch(operation -> operation.getSqlText().startsWith("DROP TABLE")));
		assertTrue(operations.stream().anyMatch(operation -> operation.getSqlText().startsWith("CREATE TABLE")));
	}

	@Test
	void removesAllOldAliasesBeforeSwappingTheirTargets() {
		final var original = new Schema("Sales").setDialect(DialectHolder.defaultDialect);
		original.getSynonyms().add(new Synonym("First").setObjectName("Orders"));
		original.getSynonyms().add(new Synonym("Second").setObjectName("Archive"));
		final var target = original.clone();
		target.getSynonyms().get("First").setObjectName("Archive");
		target.getSynonyms().get("Second").setObjectName("Orders");
		final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(original.diff(target));
		assertEquals(4, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("DROP SYNONYM"));
		assertTrue(operations.get(1).getSqlText().contains("DROP SYNONYM"));
		assertTrue(operations.get(2).getSqlText().contains("ADD SYNONYM"));
		assertTrue(operations.get(3).getSqlText().contains("ADD SYNONYM"));
	}

	private Table table(final String name) {
		final var table = new Table(name);
		table.getColumns().add(new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
		table.getConstraints().addPrimaryKeyConstraint("pk", table.getColumns().getFirst());
		return table;
	}
}
