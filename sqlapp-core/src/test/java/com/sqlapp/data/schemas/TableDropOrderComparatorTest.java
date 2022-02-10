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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Comparator;

import org.junit.jupiter.api.Test;

public class TableDropOrderComparatorTest {

	protected Comparator<Table> getComparator(){
		return new TableDropOrderComparator();
	}
	
	@Test
	public void test() {
		Comparator<Table> comparator = getComparator();
		Table table1 = new Table("table1");
		Table table2 = new Table("table2");
		assertEquals(0, comparator.compare(table1, table1));
		assertEquals(1, comparator.compare(table1, table2));
		assertEquals(-1, comparator.compare(table2, table1));
	}

	@Test
	public void testRelation() {
		Comparator<Table> comparator = getComparator();
		Schema schema = new Schema();
		int size = 10;
		TableCollection tables = schema.getTables();
		for (int i = 0; i < size; i++) {
			tables.add(getTable("table" + i));
		}
		for (int i = 0; i < size - 1; i++) {
			Table table1 = tables.get(i);
			Table table2 = tables.get(i + 1);
			assertEquals(1, comparator.compare(table1, table2));
		}
		for (int i = 1; i < size - 2; i = i + 2) {
			Table table1 = tables.get(i);
			Table table2 = tables.get(i + 1);
			assertEquals(1, comparator.compare(table1, table2));
		}
		// for (int i = 0; i < size - 2; i = i + 2) {
		for (int i = size - 1; i >= 1; i--) {
			createRelation("rel" + i, schema.getTables().get(i), schema
					.getTables().get(i - 1));
		}
		for (int i = 1; i < size - 1; i++) {
			Table table1 = tables.get(i);
			Table table2 = tables.get(i + 1);
			assertTrue(comparator.compare(table1, table2)>0);
		}
		for (int i = 1; i < size - 2; i = i + 2) {
			Table table1 = tables.get(i);
			Table table2 = tables.get(i + 2);
			assertTrue(comparator.compare(table1, table2)>0);
		}
	}

	private Table getTable(String name) {
		Table table = new Table(name);
		Column column = new Column("colA");
		table.getColumns().add(column);
		column = new Column("colB");
		table.getColumns().add(column);
		column = new Column("colC");
		table.getColumns().add(column);
		return table;
	}

	private void createRelation(String constraintName, Table table1,
			Table table2) {
		table1.getConstraints().addForeignKeyConstraint(constraintName,
				table1.getColumns().get(0), table2.getColumns().get(0));
	}

}
