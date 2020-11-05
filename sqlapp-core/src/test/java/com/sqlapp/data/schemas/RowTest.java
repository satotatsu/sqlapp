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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.DateUtils;

public class RowTest {

	/**
	 * 値比較テスト
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testCompare1() throws ParseException {
		Table table1 = getTable("table1");
		Table table2 = getTable("table2");
		Row row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-08-30"));
		Row row2 = addRow(table2, "" + 2, 1, DateUtils.parse("2013-09-30"));
		table1.getConstraints().addPrimaryKeyConstraint("pk",
				table1.getColumns().get(0));
		assertTrue(row1.compareTo(row2) < 0);
		assertTrue(row2.compareTo(row1) > 0);
		//
		row1 = addRow(table1, "" + 2, 2, DateUtils.parse("2013-08-30"));
		row2 = addRow(table2, "" + 1, 1, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) > 0);
		assertTrue(row2.compareTo(row1) < 0);
		//
		row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-08-30"));
		row2 = addRow(table2, "" + 1, 1, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) == 0);
		assertTrue(row2.compareTo(row1) == 0);
	}

	/**
	 * 値比較テスト
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testCompare2() throws ParseException {
		Table table1 = getTable("table1");
		Table table2 = getTable("table2");
		Row row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-08-30"));
		Row row2 = addRow(table2, "" + 2, 1, DateUtils.parse("2013-09-30"));
		table1.getConstraints().addPrimaryKeyConstraint("pk",
				table1.getColumns().get(1));
		assertTrue(row1.compareTo(row2) > 0);
		assertTrue(row2.compareTo(row1) < 0);
		//
		row1 = addRow(table1, "" + 2, 1, DateUtils.parse("2013-08-30"));
		row2 = addRow(table2, "" + 1, 2, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) < 0);
		assertTrue(row2.compareTo(row1) > 0);
		//
		row1 = addRow(table1, "" + 2, 10, DateUtils.parse("2013-08-30"));
		row2 = addRow(table2, "" + 1, 10, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) == 0);
		assertTrue(row2.compareTo(row1) == 0);
	}

	/**
	 * 値比較テスト
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testCompare3() throws ParseException {
		Table table1 = getTable("table1");
		Table table2 = getTable("table2");
		Row row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-08-30"));
		Row row2 = addRow(table2, "" + 2, 1, DateUtils.parse("2013-09-30"));
		table1.getConstraints().addPrimaryKeyConstraint("pk",
				table1.getColumns().get(2));
		assertTrue(row1.compareTo(row2) < 0);
		assertTrue(row2.compareTo(row1) > 0);
		//
		row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 2, 1, DateUtils.parse("2013-08-30"));
		assertTrue(row1.compareTo(row2) > 0);
		assertTrue(row2.compareTo(row1) < 0);
		//
		row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 2, 1, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) == 0);
		assertTrue(row2.compareTo(row1) == 0);
	}

	/**
	 * 値比較テスト
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testCompare4() throws ParseException {
		Table table1 = getTable("table1");
		Table table2 = getTable("table2");
		Row row1 = addRow(table1, "" + 2, 1, DateUtils.parse("2013-08-30"));
		Row row2 = addRow(table2, "" + 2, 1, DateUtils.parse("2013-09-30"));
		table1.getConstraints().addPrimaryKeyConstraint("pk",
				table1.getColumns().get(0), table1.getColumns().get(1));
		assertTrue(row1.compareTo(row2) == 0);
		assertTrue(row2.compareTo(row1) == 0);
		//
		row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 1, 2, DateUtils.parse("2013-08-30"));
		assertTrue(row1.compareTo(row2) == 0);
		assertTrue(row2.compareTo(row1) == 0);
		//
		row1 = addRow(table1, "" + 1, 1, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 1, 2, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) < 0);
		assertTrue(row2.compareTo(row1) > 0);
		//
		row1 = addRow(table1, "" + 2, 2, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 1, 2, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) > 0);
		assertTrue(row2.compareTo(row1) < 0);
	}

	/**
	 * 値比較テスト
	 * 
	 * @throws ParseException
	 */
	@Test
	public void testCompare5() throws ParseException {
		Table table1 = getTable("table1");
		Table table2 = getTable("table2");
		Row row1 = addRow(table1, "" + 2, 1, DateUtils.parse("2013-08-30"));
		Row row2 = addRow(table2, "" + 2, 1, DateUtils.parse("2013-08-30"));
		table1.getConstraints().addPrimaryKeyConstraint("pk",
				table1.getColumns().get(0), table1.getColumns().get(1),
				table1.getColumns().get(2));
		assertTrue(row1.compareTo(row2) == 0);
		assertTrue(row2.compareTo(row1) == 0);
		//
		row1 = addRow(table1, "" + 1, 2, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 1, 2, DateUtils.parse("2013-08-30"));
		assertTrue(row1.compareTo(row2) > 0);
		assertTrue(row2.compareTo(row1) < 0);
		//
		row1 = addRow(table1, "" + 1, 1, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 1, 2, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) < 0);
		assertTrue(row2.compareTo(row1) > 0);
		//
		row1 = addRow(table1, "" + 2, 2, DateUtils.parse("2013-09-30"));
		row2 = addRow(table2, "" + 1, 2, DateUtils.parse("2013-09-30"));
		assertTrue(row1.compareTo(row2) > 0);
		assertTrue(row2.compareTo(row1) < 0);
	}

	private Row addRow(Table table, Object... args) {
		Row row = table.newRow();
		int i = 0;
		for (Object arg : args) {
			row.put(i++, arg);
		}
		table.getRows().add(row);
		return row;
	}

	private Table getTable(String name) {
		Table table = new Table(name);
		Column column = table.newColumn().setName("col1");
		column.setDataType(DataType.VARCHAR).setLength(255);
		table.getColumns().add(column);
		//
		column = table.newColumn().setName("col2");
		column.setDataType(DataType.INT);
		table.getColumns().add(column);
		//
		column = table.newColumn().setName("col3");
		column.setDataType(DataType.DATETIME);
		table.getColumns().add(column);
		return table;
	}

}
