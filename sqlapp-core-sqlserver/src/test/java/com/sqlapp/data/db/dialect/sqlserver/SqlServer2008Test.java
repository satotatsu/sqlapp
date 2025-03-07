/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver;


import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2008;
import com.sqlapp.data.schemas.Column;


public class SqlServer2008Test {

	private final Dialect dialect=DialectUtils.getInstance(SqlServer2008.class);

	@Test
	public void testDatetime2_1() {
		final Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("DATETIME2(7)");
		assertEquals(DataType.TIMESTAMP, column.getDataType());
		assertEquals(7L, column.getLength());
	}

	@Test
	public void testDatetime2_2() {
		final Column column=new Column();
		column.setDialect(dialect);
		column.setLength(14);
		column.setDataTypeName("DATETIME2");
		assertEquals(DataType.TIMESTAMP, column.getDataType());
		assertEquals(7L, column.getLength());
	}

	@Test
	public void testDate() {
		final Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("date");
		assertEquals(DataType.DATE, column.getDataType());
		assertEquals(null, column.getDataTypeName());
	}

	@Test
	public void testTime() {
		final Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("time");
		assertEquals(DataType.TIME, column.getDataType());
		assertEquals(null, column.getDataTypeName());
	}

}
