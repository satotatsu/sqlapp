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

package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Beanユーティリティのテスト
 * 
 * @author tatsuo satoh
 * 
 */
public class SimpleBeanUtilsTest4 {

	@Test
	public void testToTable1() {
		final Table table = SimpleBeanUtils.toTable(DummyClass.class, col -> CommonUtils.eq("id", col.getName()),
				false);
		assertEquals("DummyClass", table.getName());
		assertEquals(2, table.getColumns().size());
		int i = 0;
		Column column = table.getColumns().get(i++);
		assertEquals("id", column.getName());
		assertEquals(true, column.isNotNull());
		assertEquals(false, column.isIdentity());
		assertEquals(DataType.INT, column.getDataType());
		column = table.getColumns().get(i++);
		assertEquals("text", column.getName());
		assertEquals(false, column.isNotNull());
		assertEquals(false, column.isIdentity());
		assertEquals(DataType.VARCHAR, column.getDataType());
	}

	static record DummyClass(int id, String text) {
	}

}
