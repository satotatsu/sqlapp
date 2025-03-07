/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectUtils;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

public class DB2Test {

	Dialect dialect = DialectUtils.getInstance(Db2.class);

	@Test
	public void testToType() {
		// VARCHAR
		DbDataType<?> dbType = dialect.getDbDataTypes().getDbType(DataType.VARCHAR);
		assertEquals(DataType.VARCHAR, dbType.getDataType());
		dbType = dialect.getDbDataTypes().getDbType(DataType.VARCHAR, 32700);
		assertEquals(DataType.VARCHAR, dbType.getDataType());
		dbType = dialect.getDbDataTypes().getDbType(DataType.VARCHAR, 40000);
		assertEquals(DataType.CLOB, dbType.getDataType());
		// VARCHAR
		dbType = dialect.getDbDataTypes().getDbType(DataType.NCLOB);
		assertEquals(DataType.NCLOB, dbType.getDataType());
		// LONGVARCHAR
		dbType = dialect.getDbDataTypes().getDbTypeStrict(DataType.LONGVARCHAR);
		assertEquals(DataType.LONGVARCHAR, dbType.getDataType());
		dbType = dialect.getDbDataTypes().getDbType(DataType.LONGVARCHAR);
		assertEquals(DataType.VARCHAR, dbType.getDataType());
		// XML
		dbType = dialect.getDbDataTypes().getDbTypeStrict(DataType.SQLXML);
		assertEquals(DataType.SQLXML, dbType.getDataType());
		dbType = dialect.getDbDataTypes().getDbType(DataType.SQLXML, 30000);
		assertEquals(DataType.SQLXML, dbType.getDataType());
		assertEquals("XMLVARCHAR", dbType.getTypeName());
		dbType = dialect.getDbDataTypes().getDbType(DataType.SQLXML, CommonUtils.LEN_2GB);
		assertEquals(DataType.SQLXML, dbType.getDataType());
		assertEquals("XMLCLOB", dbType.getTypeName());
	}

	@Test
	public void testDecimal() {
		Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("DECIMAL(6)");
		assertEquals(DataType.DECIMAL, column.getDataType());
	}

	@Test
	public void testNvharchar() {
		Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("VARGRAPHIC(6)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
	}

	@Test
	public void testNvharchar4000() {
		Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("VARGRAPHIC(4000)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals("VARGRAPHIC", column.getDataTypeName());
		assertEquals(Long.valueOf(4000), column.getLength());
	}

	@Test
	public void testNvharchar4001() {
		Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("VARGRAPHIC(4001)");
		assertEquals(DataType.NVARCHAR, column.getDataType());
		assertEquals("VARGRAPHIC", column.getDataTypeName());
		assertEquals(Long.valueOf(4001), column.getLength());
	}

	@Test
	public void testClob() {
		Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("CLOB(4001)");
		assertEquals(DataType.CLOB, column.getDataType());
		assertEquals(null, column.getDataTypeName());
		assertEquals(Long.valueOf(4001), column.getLength());
	}

	@Test
	public void testDbClob() {
		Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName("DBCLOB(4001)");
		assertEquals(DataType.NCLOB, column.getDataType());
		assertEquals("DBCLOB", column.getDataTypeName());
		assertEquals(Long.valueOf(4001), column.getLength());
	}

}
