/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Table.TableOrder;

class TableInsertOrderSorterTest {
	@Test
	void mapperSortsWrapperObjectsInForeignKeyOrder() {
		final Table parent = table("PARENT");
		final Table child = table("CHILD");
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("PARENT_ID"), parent.getColumns().get("ID"));
		final Entry childEntry = new Entry("child", child);
		final Entry parentEntry = new Entry("parent", parent);

		final List<Entry> sorted = TableOrder.CREATE.sort(
				List.of(childEntry, parentEntry), Entry::table);

		assertEquals(List.of(parentEntry, childEntry), sorted);
	}

	@Test
	void resolvesAParentRepresentedByAnEquivalentTableInstance() {
		final Table parent = table("PARENT");
		final Table equivalentParent = table("parent");
		final Table child = table("CHILD");
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("PARENT_ID"), equivalentParent.getColumns().get("ID"));

		final List<Table> sorted = TableInsertOrderSorter.sort(List.of(child, parent), table -> table);

		assertEquals(List.of(parent, child), sorted);
	}

	@Test
	void ignoresAnUnresolvedForeignKey() {
		final Table first = table("A_TABLE");
		final Table second = table("B_TABLE");
		final ForeignKeyConstraint foreignKey = new ForeignKeyConstraint("FK_EXTERNAL");
		foreignKey.getColumns().add(second.getColumns().get("PARENT_ID"));
		foreignKey.setRelatedTableName("MISSING_PARENT");
		foreignKey.getRelatedColumns().add("ID");
		second.getConstraints().add(foreignKey);

		final List<Table> sorted = TableInsertOrderSorter.sort(List.of(second, first), table -> table);

		assertEquals(List.of(first, second), sorted);
	}

	@Test
	void ignoresASelfReferenceToAnEquivalentTableInstance() {
		final Table table = table("MEMBER");
		final Table equivalentTable = table("member");
		table.getConstraints().addForeignKeyConstraint("FK_MEMBER_PARENT",
				table.getColumns().get("PARENT_ID"), equivalentTable.getColumns().get("ID"));

		final List<Table> sorted = TableInsertOrderSorter.sort(List.of(table), value -> value);

		assertEquals(List.of(table), sorted);
	}

	@Test
	void resolvesEquivalentParentsWithoutCrossingCatalogs() {
		final Catalog firstCatalog = new Catalog("CATALOG1");
		final Schema firstSchema = new Schema("PUBLIC");
		firstCatalog.getSchemas().add(firstSchema);
		final Table firstParent = table("PARENT");
		firstSchema.getTables().add(firstParent);

		final Catalog secondCatalog = new Catalog("CATALOG2");
		final Schema secondSchema = new Schema("PUBLIC");
		secondCatalog.getSchemas().add(secondSchema);
		final Table secondParent = table("PARENT");
		secondSchema.getTables().add(secondParent);
		final Table equivalentSecondParent = table("parent");
		secondSchema.getTables().add(equivalentSecondParent);

		final Table child = table("CHILD");
		secondSchema.getTables().add(child);
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				child.getColumns().get("PARENT_ID"), equivalentSecondParent.getColumns().get("ID"));

		final List<Table> sorted = TableInsertOrderSorter.sort(
				List.of(child, firstParent, secondParent), table -> table);

		assertTrue(sorted.indexOf(secondParent) < sorted.indexOf(child));
	}

	private static Table table(final String name) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		table.getColumns().add(new Column("PARENT_ID"));
		return table;
	}

	private record Entry(String id, Table table) {
	}
}
