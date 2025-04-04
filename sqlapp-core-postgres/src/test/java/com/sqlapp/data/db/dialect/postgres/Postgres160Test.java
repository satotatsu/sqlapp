/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class Postgres160Test {

	Dialect dialect = DialectResolver.getInstance().getDialect("Postgres", 16, 0, 0);

	@Test
	void testChar() {
		Column column = createColumn("colName");
		column.setDataTypeName("character");
		column.setLength(100);
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("char");
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("char(10)");
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("bpchar(15)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(15, column.getLength().intValue());
		//
		column = createColumn("colName");
		column.setDataTypeName("bpchar");
		assertEquals(DataType.CHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("char(20)");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(20, column.getLength().intValue());
		//
		column = createColumn("colName");
		column.setDataTypeName("char ( 15 ) []");
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(15, column.getLength().intValue());
		assertEquals(1, column.getArrayDimension());
	}

	@Test
	void testVarChar() {
		Column column = createColumn("colName");
		column.setDataTypeName("varchar(100)");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(100, column.getLength().intValue());
		//
		column = createColumn("colName");
		column.setDataTypeName("VARCHAR");
		column.setLength(10);
		assertEquals(DataType.VARCHAR, column.getDataType());
		//
		column = createColumn("colName");
		column.setDataTypeName("character(10) varying");
		column.setLength(10);
		assertEquals(DataType.VARCHAR, column.getDataType());
	}

	@Test
	void testJson() {
		Column column = createColumn("colName");
		column.setDataTypeName("json");
		assertEquals(DataType.JSON, column.getDataType());
	}

	@Test
	void testJsonb() {
		Column column = createColumn("colName");
		column.setDataTypeName("jsonb");
		assertEquals(DataType.JSONB, column.getDataType());
	}

	private Column createColumn(String name) {
		Column column = new Column("colName");
		column.setDialect(dialect);
		return column;
	}

}
