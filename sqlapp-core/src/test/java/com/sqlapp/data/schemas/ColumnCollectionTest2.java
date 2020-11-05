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

import com.sqlapp.util.FileUtils;

public class ColumnCollectionTest2 {

	@Test
	public void testGetOrdinal() {
		Table table = getEmptyFileDataTable();
		int i = 0;
		assertEquals(table.getColumns().get("name").getOrdinal(), i++);
		assertEquals(table.getColumns().get("extension").getOrdinal(), i++);
		assertEquals(table.getColumns().get("absolutePath").getOrdinal(), i++);
	}

	public static Table getEmptyFileDataTable() {
		return FileUtils.getEmptyFileDataTable();
	}

	public static Table getFileDataTable() {
		Table table = getEmptyFileDataTable();
		for (int i = 0; i < 10; i++) {
			Row row = table.newRow();
			row.put("name", "name" + i);
			row.put("extension", "extension" + i);
			row.put("absolutePath", "absolutePath" + i);
			table.getRows().add(row);
		}
		return table;
	}

	@Test
	public void testRemove() {
		Table table = getFileDataTable();
		table.getColumns().remove("name");
		for (int i = 0; i < table.getColumns().size(); i++) {
			Column column = table.getColumns().get(i);
			assertEquals(i, column.getOrdinal());
		}
		for (int i = 0; i < 10; i++) {
			Row row = table.getRows().get(i);
			Object[] values = row.getValues();
			assertEquals(values[0], "extension" + i);
			assertEquals(values[1], "absolutePath" + i);
		}
	}

	@Test
	public void testAdd() {
		Table table = getFileDataTable();
		Column column = new Column("last");
		ColumnCollection columns = table.getColumns();
		columns.add(column);
		assertEquals(columns.get(columns.size() - 1).getName(), "last");
		for (int i = 0; i < 10; i++) {
			Row row = table.getRows().get(i);
			Object[] values = row.getValues();
			int j = 0;
			assertEquals(values[j++], "name" + i);
			assertEquals(values[j++], "extension" + i);
			assertEquals(values[j++], "absolutePath" + i);
			assertEquals(values[columns.size() - 1], null);
		}
	}
}
