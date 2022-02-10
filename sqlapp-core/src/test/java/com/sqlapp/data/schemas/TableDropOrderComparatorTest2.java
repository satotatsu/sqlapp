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
import java.util.List;

import org.junit.jupiter.api.Test;

public class TableDropOrderComparatorTest2 {

	protected Comparator<Table> getComparator(){
		return new TableDropOrderComparator();
	}
	
	@Test
	public void test() {
		Comparator<Table> comparator = getComparator();
		Schema schema = new Schema();
		int relcount=0;
		Table table1 = getTable(schema, "table1");
		Table table1_1 = getTable(schema, "table1_1");
		Table table1_2 = getTable(schema, "table1_2");
		createRelation("rel" + relcount++, table1, table1_1, 2);
		createRelation("rel" + relcount++, table1, table1_2, 2);
		Table table2 = getTable(schema, "table2");
		//
		createRelation("rel" + relcount++, table1, table2, 2);
		Table table2_1 = getTable(schema, "table2_1");
		Table table2_1_1 = getTable(schema, "table2_1_1");
		createRelation("rel" + relcount++, table2, table2_1, 2);
		createRelation("rel" + relcount++, table2_1, table2_1_1, 2);
		//
		createRelation("rel" + relcount++, table1, table2, 2);
		Table table3 = getTable(schema, "table3");
		createRelation("rel" + relcount++, table2, table3, 2);
		Table table4 = getTable(schema, "table4");
		createRelation("rel" + relcount++, table3, table4, 2);
		Table table5 = getTable(schema, "table5");
		createRelation("rel" + relcount++, table4, table5, 2);
		//
		List<Table> tables=SchemaUtils.getNewSortedTableList(schema.getTables(), comparator);
		tables.forEach(t->{
			System.out.println(t.getName());
		});
		int i=0;
		assertEquals("table1", tables.get(i++).getName());
		assertEquals("table2", tables.get(i++).getName());
		assertEquals("table3", tables.get(i++).getName());
		assertEquals("table4", tables.get(i++).getName());
		assertEquals("table2_1", tables.get(i++).getName());
		assertEquals("table5", tables.get(i++).getName());
		assertEquals("table2_1_1", tables.get(i++).getName());
		assertEquals("table1_2", tables.get(i++).getName());
		assertEquals("table1_1", tables.get(i++).getName());
	}

	private Table getTable(Schema schema, String name) {
		Table table = new Table(name);
		Column column = new Column("colA");
		table.getColumns().add(column);
		column = new Column("colB");
		table.getColumns().add(column);
		column = new Column("colC");
		table.getColumns().add(column);
		table.getConstraints().addPrimaryKeyConstraint("PK", table.getColumns().get(0));
		schema.getTables().add(table);
		return table;
	}

	private void createRelation(String constraintName, Table table1,
			Table table2, int colNo) {
		table1.getConstraints().addForeignKeyConstraint(constraintName,
				table1.getColumns().get(colNo), table2.getColumns().get(0));
	}

}
