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

package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

public abstract class AbstractRowExcelIteratorHandlerTest extends AbstractRowIteratorHandlerTest {

	@Test
	public void testIterator() {
		Table table = getTable();
		initializeTable(table);
		int i = 0;
		for (Row row : table.getRows()) {
			assertEquals("name" + (i + 1), row.get("name"));
			i++;
		}
		assertEquals(count(), i);
		i = 0;
		ColumnCollection columns = table.getColumns();
		Column column = columns.get(i++);
		assertEquals("id", column.getName());
		assertEquals(DataType.BIGINT, column.getDataType());
		//
		column = table.getColumns().get(i++);
		assertEquals("created_at", column.getName());
		assertEquals(DataType.NVARCHAR, column.getDataType());
	}
}
