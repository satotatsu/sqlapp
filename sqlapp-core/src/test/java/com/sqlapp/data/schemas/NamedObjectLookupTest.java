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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Name lookups of named collections follow renames, specific names, index
 * insertion and copies.
 */
public class NamedObjectLookupTest {

	@Test
	public void renameUpdatesLookup() {
		final TableCollection tables = new TableCollection();
		final Table table = new Table("A");
		tables.add(table);
		table.setName("Z");
		assertNull(tables.get("A"));
		assertSame(table, tables.get("Z"));
		table.setName(null);
		assertNull(tables.get("Z"));
	}

	@Test
	public void specificNameChangeUpdatesLookup() {
		final FunctionCollection functions = new FunctionCollection();
		final Function function = new Function("F");
		functions.add(function);
		function.setSpecificName("F(int)");
		assertSame(function, functions.get("F(int)"));
		function.setSpecificNameLiteral("F_2");
		assertSame(function, functions.get("F_2"));
		assertNull(functions.get("F(int)"));
	}

	@Test
	public void containsRejectsForeignInputWithoutException() {
		final TableCollection tables = new TableCollection();
		tables.add(new Table("A"));
		assertFalse(tables.contains((Object) Integer.valueOf(1)));
		assertFalse(tables.contains((Object) null));
		assertTrue(tables.contains((Object) "A"));
		assertTrue(tables.containsAll("A"));
		assertFalse(tables.containsAll("A", "B"));
	}

	@Test
	public void containsAllFindsSpecificNames() {
		final FunctionCollection functions = new FunctionCollection();
		final Function function = new Function("F");
		function.setSpecificName("F(int)");
		functions.add(function);
		assertTrue(functions.contains("F(int)"));
		assertTrue(functions.containsAll("F(int)"));
	}

	@Test
	public void indexInsertionMergesOnlySameObject() {
		final TableCollection tables = new TableCollection();
		final Table a = new Table("A");
		tables.add(a);
		tables.add(0, new Table("A").setRemarks("same object"));
		assertEquals(1, tables.size());
		assertEquals("same object", a.getRemarks());

		final Table other = new Table("A");
		other.setId("other");
		tables.add(0, other);
		assertEquals(2, tables.size());
		assertSame(other, tables.get(0));
	}

	@Test
	public void indexInsertionWithoutValidationAtChangeIsFoundByName() {
		final TableCollection tables = new TableCollection();
		tables.add(new Table("A"));
		tables.get("A");
		tables.setValidateAtChange(false);
		final Table b = new Table("B");
		tables.add(0, b);
		assertSame(b, tables.get("B"));
		tables.setValidateAtChange(true);
	}

	@Test
	public void cloneKeepsCaseSensitivity() {
		final TableCollection tables = new TableCollection();
		tables.setCaseSensitive(false);
		tables.add(new Table("ORDERS"));
		final TableCollection clone = tables.clone();
		assertFalse(clone.isCaseSensitive());
		assertNotNull(clone.get("orders"));
	}
}
