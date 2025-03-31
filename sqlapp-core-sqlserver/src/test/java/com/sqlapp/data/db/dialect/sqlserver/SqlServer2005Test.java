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
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

public class SqlServer2005Test {

	private final Dialect dialect = DialectUtils.getInstance(SqlServer2005.class);

	@Test
	public void testDecimal() {
		final Column column = createColumn();
		column.setDataTypeName("DECIMAL(6)");
		assertEquals(DataType.DECIMAL, column.getDataType());
	}

	@Test
	public void testNvharchar() {
		final Column column = createColumn();
		column.setDataTypeName("nvarchar(6)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
	}

	@Test
	public void testNchar() {
		Column column = createColumn();
		column.setDataTypeName("nchar(6)");
		assertEquals(DataType.NCHAR, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
		//
		column = createColumn();
		column.setDataTypeName("national char(5)");
		assertEquals(DataType.NCHAR, column.getDataType());
		assertEquals(Long.valueOf(5), column.getLength());
		//
		column = createColumn();
		column.setDataTypeName("national character(4)");
		assertEquals(DataType.NCHAR, column.getDataType());
		assertEquals(Long.valueOf(4), column.getLength());
	}

	@Test
	public void testChar() {
		Column column = createColumn();
		column.setDataTypeName("char(6)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
		//
		column = createColumn();
		column.setDataTypeName("character(4)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(Long.valueOf(4), column.getLength());
	}

	@Test
	public void testNvharchar4000() {
		final Column column = createColumn();
		column.setDataTypeName("nvarchar(4000)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(4000), column.getLength());
	}

	@Test
	public void testNvharchar4001() {
		final Column column = createColumn();
		column.setDataTypeName("nvarchar(4001)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(null, column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB), column.getLength());
	}

	@Test
	public void testNvharcharMAX() {
		final Column column = createColumn();
		column.setDataTypeName("nvarchar( max )");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(null, column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB), column.getLength());
	}

	@Test
	public void testVharchar8000() {
		final Column column = createColumn();
		column.setDataTypeName("varchar(8000)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(8000), column.getLength());
	}

	@Test
	public void testVarcharVarying() {
		Column column = createColumn();
		column.setDataTypeName("char(8000) varying");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(8000), column.getLength());
		//
		column = createColumn();
		column.setDataTypeName("character(111)varying");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(111), column.getLength());
	}

	@Test
	public void testNvarcharVarying() {
		Column column = createColumn();
		column.setDataTypeName("national char(4000) varying");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(4000), column.getLength());
		//
		column = createColumn();
		column.setDataTypeName("national character(111)varying");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(111), column.getLength());
	}

	protected Column createColumn() {
		final Column column = new Column();
		column.setDialect(dialect);
		return column;
	}

	@Test
	public void testVharchar8001() {
		final Column column = createColumn();
		column.setDataTypeName("varchar(8001)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(null, column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_2GB), column.getLength());
	}

	@Test
	public void testVharcharMAX() {
		final Column column = createColumn();
		column.setDataTypeName("varchar( max )");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(null, column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_2GB), column.getLength());
	}

	@Test
	public void testText() {
		final Column column = createColumn();
		column.setDataTypeName("text");
		assertEquals(DataType.LONGVARCHAR, column.getDataType());
		assertEquals("TEXT", column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_2GB - 1), column.getLength());
	}

	@Test
	public void testNtext() {
		final Column column = createColumn();
		column.setDataTypeName("ntext");
		assertEquals(DataType.LONGNVARCHAR, column.getDataType());
		assertEquals("NTEXT", column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB - 1), column.getLength());
	}

	@Test
	public void testBoolean() {
		final Column column = createColumn();
		column.setDataTypeName("BIT");
		assertEquals(DataType.BOOLEAN, column.getDataType());
		assertEquals("BIT", column.getDataTypeName());
	}

}
