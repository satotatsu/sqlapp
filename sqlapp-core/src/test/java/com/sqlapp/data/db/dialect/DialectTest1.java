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

package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

public class DialectTest1 {

	Dialect dialect=DialectResolver.getInstance().getDialect("default", 1, 1);
	
	@Test
	public void testNvarchar() {
		Column column=new Column();
		column.setLength(2000);
		column.setDataType(DataType.NVARCHAR);
		DbDataType<?> dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.NVARCHAR, dbDataType.getDataType());
		//
		column.setLength(2001);
		dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.NCLOB, dbDataType.getDataType());
	}
	
	@Test
	public void testVarchar() {
		Column column=new Column();
		column.setLength(2000);
		column.setDataType(DataType.VARCHAR);
		DbDataType<?> dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.VARCHAR, dbDataType.getDataType());
		//
		column.setLength(2001);
		dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.CLOB, dbDataType.getDataType());
	}
	
	@Test
	public void testChar() {
		Column column=new Column();
		column.setLength(254);
		column.setDataType(DataType.CHAR);
		DbDataType<?> dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.CHAR, dbDataType.getDataType());
		//
		column.setLength(255);
		dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.VARCHAR, dbDataType.getDataType());
	}

}
