/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;


public class SqlServer2016Test {

	private Dialect dialect=DialectUtils.getInstance(SqlServer2016.class);;

	@Test
	public void testTimestampWithTimezone() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("TIMESTAMP(6) WITH TIME ZONE");
		assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
	}

	@Test
	public void testDecimal() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("DECIMAL(6)");
		assertEquals(DataType.DECIMAL, column.getDataType());
	}

	@Test
	public void testNvharchar() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("nvarchar(6)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
	}

	@Test
	public void testNvharchar4000() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("nvarchar(4000)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(4000), column.getLength());
	}
	
	@Test
	public void testNvharchar4001() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("nvarchar(4001)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB), column.getLength());
	}

}
