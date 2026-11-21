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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

public class AbstractNamedObjectCollectionTest {

	@Test
	public void setCaseSensitiveRebuildsNameLookupAfterMapsWereBuilt() {
		final TableCollection tables = new TableCollection();
		final Table table = new Table("ORDERS");
		tables.add(table);
		assertSame(table, tables.get("ORDERS"));
		assertNull(tables.get("orders"));

		tables.setCaseSensitive(false);
		assertSame(table, tables.get("orders"));
		assertTrue(tables.contains("Orders"));

		tables.setCaseSensitive(true);
		assertNull(tables.get("orders"));
		assertSame(table, tables.get("ORDERS"));
	}

	@Test
	public void addAllWithIndexInsertsAtPositionInOrder() {
		final TableCollection tables = new TableCollection();
		tables.add(new Table("A"));
		tables.add(new Table("D"));

		assertTrue(tables.addAll(1, List.of(new Table("B"), new Table("C"))));

		assertEquals(List.of("A", "B", "C", "D"), names(tables));
		for (int i = 0; i < tables.size(); i++) {
			assertEquals(i, tables.get(i).getOrdinal());
		}
		assertSame(tables.get(2), tables.get("C"));
	}

	@Test
	public void addAllWithIndexAtStartAndEnd() {
		final TableCollection tables = new TableCollection();
		tables.add(new Table("B"));
		tables.addAll(0, List.of(new Table("A")));
		tables.addAll(tables.size(), List.of(new Table("C")));
		assertEquals(List.of("A", "B", "C"), names(tables));
	}

	@Test
	public void addAllWithIndexRejectsInvalidIndex() {
		final TableCollection tables = new TableCollection();
		tables.add(new Table("A"));
		assertThrows(IndexOutOfBoundsException.class, () -> tables.addAll(2, List.of(new Table("B"))));
		assertThrows(IndexOutOfBoundsException.class, () -> tables.addAll(-1, List.of(new Table("B"))));
		assertEquals(List.of("A"), names(tables));
	}

	@Test
	public void addAllWithIndexMergesDuplicateWithoutTakingPosition() {
		final TableCollection tables = new TableCollection();
		final Table a = new Table("A");
		tables.add(a);
		tables.add(new Table("C"));
		final Table duplicate = new Table("A").setRemarks("merged");

		assertTrue(tables.addAll(1, List.of(duplicate, new Table("B"))));

		assertEquals(List.of("A", "B", "C"), names(tables));
		assertSame(a, tables.get("A"));
		assertEquals("merged", a.getRemarks());
	}

	@Test
	public void addReportsWhetherNewElementWasInserted() {
		final TableCollection tables = new TableCollection();
		final Table a = new Table("A");
		assertTrue(tables.add(a));
		assertFalse(tables.add(new Table("A")));
		assertEquals(1, tables.size());
		assertSame(a, tables.get("A"));
	}

	@Test
	public void addAllReportsWhetherCollectionChanged() {
		final TableCollection tables = new TableCollection();
		assertTrue(tables.addAll(List.of(new Table("A"), new Table("B"))));
		assertFalse(tables.addAll(List.of()));
		assertFalse(tables.addAll(List.of(new Table("A"))));
		assertFalse(tables.addAll(1, List.of()));
		assertEquals(List.of("A", "B"), names(tables));
	}

	@Test
	public void beforeAddVetoIsHonouredByEveryAddPath() {
		final VetoingRoleCollection roles = new VetoingRoleCollection("REJECTED");

		assertFalse(roles.add(new Role("REJECTED")));
		roles.add(0, new Role("REJECTED"));
		assertTrue(roles.add(new Role("A")));
		assertTrue(roles.addAll(List.of(new Role("REJECTED"), new Role("B"))));
		assertTrue(roles.addAll(1, List.of(new Role("REJECTED"), new Role("C"))));

		assertEquals(List.of("A", "C", "B"), names(roles));
		assertNull(roles.get("REJECTED"));
		assertFalse(roles.afterAddNames.contains("REJECTED"));
	}

	private static List<String> names(final AbstractNamedObjectCollection<?> collection) {
		return collection.stream().map(AbstractNamedObject::getName).toList();
	}

	private static final class VetoingRoleCollection extends AbstractNamedObjectCollection<Role> {
		private static final long serialVersionUID = 1L;
		private final Set<String> rejected;
		private final java.util.List<String> afterAddNames = new java.util.ArrayList<>();

		private VetoingRoleCollection(final String... rejected) {
			this.rejected = Set.of(rejected);
		}

		@Override
		protected boolean beforeAdd(final Role role) {
			return !rejected.contains(role.getName());
		}

		@Override
		protected void setElementParent(final Role role) {
			// Role.getParent() expects a RoleCollection; this test collection keeps
			// roles detached so only the add path is exercised.
		}

		@Override
		protected void afterAdd(final Role role) {
			afterAddNames.add(role.getName());
		}

		@Override
		protected Supplier<Role> getElementSupplier() {
			return Role::new;
		}

		@Override
		protected Supplier<VetoingRoleCollection> newInstance() {
			return VetoingRoleCollection::new;
		}
	}
}
