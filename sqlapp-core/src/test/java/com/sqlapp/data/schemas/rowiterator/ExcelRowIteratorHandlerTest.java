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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;

public class ExcelRowIteratorHandlerTest extends AbstractRowIteratorHandlerTest {

	@Override
	protected RowIteratorHandler getRowIteratorHandler() {
		return new ExcelRowIteratorHandler(new File("src/test/resources/test.xlsx"));
	}

	@Test
	public void testColumns() {
		Table table = getTable();
		table.setRowIteratorHandler(new CombinedRowIteratorHandler(getRowIteratorHandler(), getRowIteratorHandler()));
		int i = 0;
		int count = 0;
		for (Row row : table.getRows()) {
			assertNotNull(row.get(0));
			count++;
		}
		assertEquals(46, count);
		Column column = table.getColumns().get(i++);
		assertEquals("id", column.getName());
	}

}
