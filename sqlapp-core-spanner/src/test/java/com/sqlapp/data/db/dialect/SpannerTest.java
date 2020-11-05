/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

public class SpannerTest {

	Dialect dialect = DialectUtils.getInstance(Spanner.class);

	@Test
	public void testToType() {
		Column column=getColumn("BOOL");
		assertEquals(DataType.BOOLEAN, column.getDataType());
		column=getColumn("INT64");
		assertEquals(DataType.BIGINT, column.getDataType());
		column=getColumn("FLOAT64");
		assertEquals(DataType.DOUBLE, column.getDataType());
		column=getColumn("STRING(10)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		column=getColumn("STRING(MAX)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		column=getColumn("BYTES(10)");
		assertEquals(DataType.VARBINARY, column.getDataType());
		column=getColumn("BYTES(MAX)");
		assertEquals(DataType.VARBINARY, column.getDataType());
		column=getColumn("DATE");
		assertEquals(DataType.DATE, column.getDataType());
		column=getColumn("TIMESTAMP");
		assertEquals(DataType.TIMESTAMP, column.getDataType());
	}

	@Test
	public void testToTypeArray() {
		Column column=getColumn("Array<BOOL>");
		assertEquals(DataType.BOOLEAN, column.getDataType());
		assertEquals(1, column.getArrayDimension());
		//
		column=getColumn("Array<STRING(10)>");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(1, column.getArrayDimension());
	}

	private Column getColumn(String dataTypeName) {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName(dataTypeName);
		return column;
	}
	
	
	@Test
	public void testString() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("STRING(10)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals("STRING", column.getDataTypeName());
		assertEquals(Long.valueOf(10), column.getLength());
	}
}
