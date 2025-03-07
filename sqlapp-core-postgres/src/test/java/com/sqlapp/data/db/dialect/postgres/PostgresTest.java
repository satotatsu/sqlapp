/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.db.dialect.postgres.Postgres;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

public class PostgresTest {

	Dialect dialect = DialectUtils.getInstance(Postgres.class);

	@Test
	public void testToType() {
		Column column=getColumn("TIMESTAMP(6) WITH TIME ZONE");
		assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
		column=getColumn("INTERVAL HOUR");
		assertEquals(DataType.INTERVAL_HOUR, column.getDataType());
		column=getColumn("POINT");
		assertEquals(DataType.POINT, column.getDataType());
		column=getColumn("CIRCLE");
		assertEquals(DataType.CIRCLE, column.getDataType());
	}
	
	@Test
	public void testDbClob() {
		Column column=getColumn("TEXT");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals("TEXT", column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB), column.getLength());
	}

	@Test
	public void testDbClob2() {
		Column column=new Column();
		dialect.setDbType("TEXT", null
				, null, column);
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals("TEXT", column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB), column.getLength());
	}
	
	protected Column getColumn(String dataTypeName) {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName(dataTypeName);
		return column;
	}
}
