/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.junit.jupiter.api.Test;

/**
 * Every mutation path of the schema collections must keep name lookups,
 * ordinals, parents and remove hooks consistent.
 */
public class CollectionMutationPathTest {

	private Table a;
	private Table b;
	private Table c;

	private TableCollection tables() {
		final TableCollection tables = new TableCollection();
		a = new Table("A");
		b = new Table("B");
		c = new Table("C");
		tables.add(a);
		tables.add(b);
		tables.add(c);
		assertSame(b, tables.get("B"));
		return tables;
	}

	private void assertBRemoved(final TableCollection tables) {
		assertEquals(List.of("A", "C"), names(tables));
		assertNull(tables.get("B"));
		assertFalse(tables.contains("B"));
		assertNull(b.getParent());
		assertSame(c, tables.get("C"));
		assertEquals(1, c.getOrdinal());
	}

	@Test
	public void removeIfUpdatesLookup() {
		final TableCollection tables = tables();
		assertTrue(tables.removeIf(t -> "B".equals(t.getName())));
		assertBRemoved(tables);
		assertFalse(tables.removeIf(t -> "B".equals(t.getName())));
	}

	@Test
	public void iteratorRemoveUpdatesLookup() {
		final TableCollection tables = tables();
		final Iterator<Table> it = tables.iterator();
		assertThrows(IllegalStateException.class, it::remove);
		while (it.hasNext()) {
			if (it.next() == b) {
				it.remove();
				assertThrows(IllegalStateException.class, it::remove);
			}
		}
		assertBRemoved(tables);
	}

	@Test
	public void retainAllUpdatesLookup() {
		final TableCollection tables = tables();
		assertTrue(tables.retainAll(List.of(a, c)));
		assertBRemoved(tables);
		assertFalse(tables.retainAll(List.of(a, c)));
	}

	@Test
	public void removeAllUpdatesLookupAndReportsChange() {
		final TableCollection tables = tables();
		assertTrue(tables.removeAll(List.of(b, new Table("Z"))));
		assertBRemoved(tables);
		assertFalse(tables.removeAll(List.of(new Table("Z"))));
	}

	@Test
	public void subListClearUpdatesLookup() {
		final TableCollection tables = tables();
		tables.subList(1, 2).clear();
		assertBRemoved(tables);
	}

	@Test
	public void removeWithoutValidationAtChangeStillUpdatesLookup() {
		final TableCollection tables = tables();
		tables.setValidateAtChange(false);
		assertTrue(tables.remove(b));
		assertNull(tables.get("B"));
		tables.setValidateAtChange(true);
		tables.renew();
		assertBRemoved(tables);
	}

	@Test
	public void removeByMissingNameIsNoOp() {
		final TableCollection tables = tables();
		assertFalse(tables.remove("Z"));
		assertFalse(tables.remove((Object) new Table("Z")));
		assertEquals(List.of("A", "B", "C"), names(tables));
	}

	@Test
	public void setReplacesLookupAndParents() {
		final TableCollection tables = tables();
		final Table x = new Table("X");
		assertSame(b, tables.set(1, x));
		assertEquals(List.of("A", "X", "C"), names(tables));
		assertNull(tables.get("B"));
		assertSame(x, tables.get("X"));
		assertSame(tables, x.getParent());
		assertNull(b.getParent());
		assertEquals(1, x.getOrdinal());
	}

	@Test
	public void listIteratorSetAndAddUpdateLookup() {
		final TableCollection tables = tables();
		final ListIterator<Table> it = tables.listIterator(1);
		assertSame(b, it.next());
		final Table x = new Table("X");
		it.set(x);
		it.add(new Table("Y"));
		assertEquals(List.of("A", "X", "Y", "C"), names(tables));
		assertNull(tables.get("B"));
		assertNotNull(tables.get("Y"));
		assertEquals(2, tables.get("Y").getOrdinal());
	}

	@Test
	public void removingColumnThroughAnyPathCompactsRowValues() {
		for (final java.util.function.Consumer<ColumnCollection> removeB : List.<java.util.function.Consumer<ColumnCollection>>of(
				columns -> columns.remove("B"), columns -> columns.removeIf(col -> "B".equals(col.getName())),
				columns -> columns.removeAll(List.of(columns.get("B"))),
				columns -> columns.retainAll(List.of(columns.get("A"), columns.get("C"))), columns -> {
					final Iterator<Column> it = columns.iterator();
					while (it.hasNext()) {
						if ("B".equals(it.next().getName())) {
							it.remove();
						}
					}
				})) {
			final Table table = new Table("T");
			table.getColumns().add("A").add("B").add("C");
			final Row row = table.newRow();
			row.put("A", "a");
			row.put("B", "b");
			row.put("C", "c");
			table.getRows().add(row);

			removeB.accept(table.getColumns());

			assertNull(table.getColumns().get("B"));
			final Object valueA = row.get("A");
			final Object valueC = row.get("C");
			assertEquals("a", valueA);
			assertEquals("c", valueC);
		}
	}

	@Test
	public void rowCollectionRetainAllKeepsGivenRows() {
		final Table table = new Table("T");
		table.getColumns().add("A");
		final Row keep = table.newRow();
		keep.put("A", 1);
		final Row drop = table.newRow();
		drop.put("A", 2);
		table.getRows().add(keep);
		table.getRows().add(drop);

		assertTrue(table.getRows().retainAll(List.of(keep)));

		assertEquals(1, table.getRows().size());
		assertSame(keep, table.getRows().get(0));
		assertSame(table.getRows(), keep.getParent());
		assertNull(drop.getParent());
		assertFalse(table.getRows().retainAll(List.of(keep)));
	}

	@Test
	public void rowCollectionRemoveAllDetachesOnlyRemovedRows() {
		final Table table = new Table("T");
		table.getColumns().add("A");
		final Row row = table.newRow();
		row.put("A", 1);
		table.getRows().add(row);
		final Table other = new Table("O");
		other.getColumns().add("A");
		final Row foreign = other.newRow();
		foreign.put("A", 2);
		other.getRows().add(foreign);

		assertFalse(table.getRows().removeAll(List.of(foreign)));
		assertSame(other.getRows(), foreign.getParent());
		assertTrue(table.getRows().removeAll(List.of(row)));
		assertNull(row.getParent());
	}

	@Test
	public void rowCollectionSetDetachesReplacedRow() {
		final Table table = new Table("T");
		table.getColumns().add("A");
		final Row old = table.newRow();
		table.getRows().add(old);
		final Row replacement = table.newRow();

		assertSame(old, table.getRows().set(0, replacement));
		assertNull(old.getParent());
		assertSame(table.getRows(), replacement.getParent());
	}

	@Test
	public void equalRowsInDifferentTablesHaveEqualHashCodes() {
		final Row row1 = rowIn("T1");
		final Row row2 = rowIn("T2");
		assertEquals(row1, row2);
		assertEquals(row1.hashCode(), row2.hashCode());
	}

	@Test
	public void rowCloneEqualsOriginal() {
		final Row row = rowIn("T");
		row.setRowId(10L);
		row.setHasErrors(true);
		final Row clone = row.clone();
		assertEquals(row, clone);
		assertEquals(row.hashCode(), clone.hashCode());
		assertEquals(10L, clone.getRowId());
		assertTrue(clone.getHasErrors());
	}

	private static Row rowIn(final String tableName) {
		final Table table = new Table(tableName);
		table.getColumns().add("A");
		final Row row = table.newRow();
		row.put("A", "value");
		row.setRowId(1L);
		table.getRows().add(row);
		return row;
	}

	private static List<String> names(final AbstractNamedObjectCollection<?> collection) {
		return collection.stream().map(AbstractNamedObject::getName).toList();
	}
}
