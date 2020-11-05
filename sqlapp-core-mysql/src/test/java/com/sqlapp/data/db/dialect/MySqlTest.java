/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

public class MySqlTest {

	Dialect dialect = DialectResolver.getInstance().getDialect("mysql", 5,
			1);
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
		column.setDataTypeName("tinyint(3) unsigned zerofill");
		assertEquals(DataType.UTINYINT, column.getDataType());
	}

	@Test
	public void testDatetime() {
		Column column = createColumn();
		column.setDataTypeName("datetime");
		assertEquals(null, column.getLength());
		assertEquals("DATETIME", column.getDataTypeName());
		assertEquals(DataType.TIMESTAMP, column.getDataType());
	}

	@Test
	public void testTimestamp() {
		Column column = createColumn();
		column.setLength(0);
		column.setDataTypeName("timestamp");
		assertEquals(null, column.getLength());
		assertEquals("TIMESTAMP", column.getDataTypeName());
		assertEquals(DataType.TIMESTAMPVERSION, column.getDataType());
	}

	@Test
	public void testTimestamp2() {
		Column column = createColumn();
		column.setDataTypeName("timestamp");
		assertEquals(null, column.getLength());
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
		column.setLength(0);
		column.setDataTypeName("tinyint");
		assertEquals(null, column.getDataTypeName());
		assertEquals(null, column.getLength());
		assertEquals(DataType.TINYINT, column.getDataType());
	}

	@Test
	public void testDate() {
		Column column = createColumn();
		column.setLength(0);
		column.setDataTypeName("date");
		assertEquals(null, column.getDataTypeName());
		assertEquals(null, column.getLength());
		assertEquals(DataType.DATE, column.getDataType());
	}

	@Test
	public void testInt4ZeroFill() {
		Column column = createColumn();
		column.setDataTypeName("int(4) unsigned zerofill");
		assertEquals(DataType.UINT, column.getDataType());
		assertEquals(null, column.getDataTypeName());
		assertEquals("4", column.getSpecifics().get("width"));
		assertEquals("true", column.getSpecifics().get("zerofill"));
	}

	protected Column createColumn() {
		Column column = new Column();
		column.setDialect(dialect);
		return column;
	}
	
	
}
