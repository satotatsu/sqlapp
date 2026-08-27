/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

	private static Table table(final String name) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		table.getColumns().add(new Column("PARENT_ID"));
		return table;
	}

	private record Entry(String id, Table table) {
	}
}
