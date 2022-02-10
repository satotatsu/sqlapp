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

package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

public class DialectTest {

	Dialect dialect=DialectResolver.getInstance().getDialect("default", 1, 1);
	
	@Test
	public void testNvarchar() {
		Column column=createColumn();
		column.setDataTypeName("nvarchar(10)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
	}
	
	@Test
	public void testVarchar() {
		Column column=createColumn();
		column.setDataTypeName("varchar(10)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
	}
	
	@Test
	public void testChar() {
		Column column=createColumn();
		column.setDataTypeName("char(10)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
	}
	
	@Test
	public void testDecimal() {
		Column column=createColumn();
		column.setDataTypeName("decimal(10,1)");
		assertEquals(DataType.DECIMAL, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
		assertEquals(Integer.valueOf(1), column.getScale());
	}
	
	protected Column createColumn(){
		Column column=new Column();
		column.setDialect(dialect);
		return column;
	}

}
