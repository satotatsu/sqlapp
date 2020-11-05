/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ReferenceColumnCollectionTest {

	@Test
	public void testGetString() {
		ReferenceColumnCollection columns = new ReferenceColumnCollection();
		Column column1 = new Column("A");
		ReferenceColumn refColumn = new ReferenceColumn(column1);
		refColumn.setNullsOrder(NullsOrder.NullsLast);
		columns.add(refColumn);
		columns.add(new ReferenceColumn("B"));
		Column column2 = new Column("C");
		columns.add(new ReferenceColumn(column2));
		columns.add(new ReferenceColumn("D"));
		int i = 0;
		assertEquals(columns.get(i++).getName(), "A");
		assertEquals(columns.get(i++).getName(), "B");
		assertEquals(columns.get(i++).getName(), "C");
		assertEquals(columns.get(i++).getName(), "D");
		//
		i = 0;
		column1.setName("A1");
		i++;
		// columns.get(i++).setName("A1");
		columns.get(i++).setName("B1");
		columns.get(i++).setName("C1");
		columns.get(i++).setName("D1");
		i = 0;
		assertEquals(columns.get(i++).getName(), "A1");
		assertEquals(columns.get(i++).getName(), "B1");
		assertEquals(columns.get(i++).getName(), "C1");
		assertEquals(columns.get(i++).getName(), "D1");
	}

	@Test
	public void testType() {
		ReferenceColumnCollection columns = new ReferenceColumnCollection();
		assertEquals(ReferenceColumn.class, columns.getType());
	}
}
