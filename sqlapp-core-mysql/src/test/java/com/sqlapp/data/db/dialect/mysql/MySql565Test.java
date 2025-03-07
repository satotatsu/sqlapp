/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

public class MySql565Test {

	Dialect dialect = DialectResolver.getInstance().getDialect("mysql", 5,
			6,5);
	@Test
	public void testToType() {
		Column column = createColumn();
		column.setDataTypeName("enum('a','b','c')");
		assertEquals(DataType.ENUM, column.getDataType());
		Set<String> set = new LinkedHashSet<String>();
		set.add("'a'");
		set.add("'b'");
		set.add("'c'");
		assertEquals(set, column.getValues());
	}
	
	@Test
	public void testToType2() {
		Column column = createColumn();
		column.setDataTypeName("tinyint(3) unsigned");
		assertEquals(DataType.UTINYINT, column.getDataType());
		assertEquals("3", column.getSpecifics().get("width"));
	}

	@Test
	public void testDatetime() {
		Column column = createColumn();
		column.setDataTypeName("datetime");
		assertEquals(Long.valueOf(0), column.getLength());
		assertEquals("DATETIME", column.getDataTypeName());
		assertEquals(DataType.TIMESTAMP, column.getDataType());
	}

	@Test
	public void testTimestamp() {
		Column column = createColumn();
		column.setLength(0);
		column.setDataTypeName("timestamp");
		assertEquals(Long.valueOf(0), column.getLength());
		assertEquals("TIMESTAMP", column.getDataTypeName());
		assertEquals(DataType.TIMESTAMPVERSION, column.getDataType());
	}

	@Test
	public void testTimestamp2() {
		Column column = createColumn();
		column.setDataTypeName("timestamp");
		assertEquals(Long.valueOf(0), column.getLength());
		assertEquals("TIMESTAMP", column.getDataTypeName());
		assertEquals(DataType.TIMESTAMPVERSION, column.getDataType());
	}

	@Test
	public void testTimestamp3() {
		Column column = createColumn();
		column.setDataTypeName("timestamp");
		assertEquals(DataType.TIMESTAMPVERSION, column.getDataType());
	}
	
	@Test
	public void testBigint() {
		Column column = createColumn();
		column.setLength(0);
		column.setDataTypeName("bigint");
		assertEquals(null, column.getDataTypeName());
		assertEquals(null, column.getLength());
		assertEquals(DataType.BIGINT, column.getDataType());
	}
	
	@Test
	public void testUbigint() {
		Column column = createColumn();
		column.setLength(0);
		column.setDataTypeName("ubigint");
		assertEquals(null, column.getDataTypeName());
		assertEquals(null, column.getLength());
		assertEquals(DataType.UBIGINT, column.getDataType());
	}
	
	@Test
	public void testUbigint2() {
		Column column = createColumn();
		column.setLength(0);
		column.setDataTypeName("BIGINT UNSIGNED");
		assertEquals(null, column.getDataTypeName());
		assertEquals(null, column.getLength());
		assertEquals(DataType.UBIGINT, column.getDataType());
	}
	
	
	@Test
	public void testTinyint() {
		Column column = createColumn();
		column.setDataTypeName("tinyint");
		assertEquals(null, column.getDataTypeName());
		assertEquals(null, column.getLength());
		assertEquals(DataType.TINYINT, column.getDataType());
	}

	@Test
	public void testDate() {
		Column column = createColumn();
		dialect.setDbType("date", 0L, null, column);
		assertEquals(null, column.getDataTypeName());
		assertEquals(null, column.getLength());
		assertEquals(DataType.DATE, column.getDataType());
	}
	
	protected Column createColumn() {
		Column column = new Column();
		column.setDialect(dialect);
		return column;
	}
}
