/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;


public class OracleTest {

	private Dialect dialect=DialectUtils.getInstance(Oracle.class);;

	@Test
	public void testTimestampWithTimezone() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("TIMESTAMP(6) WITH TIME ZONE");
		assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE, column.getDataType());
	}

	@Test
	public void testAnyData() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("ANYDATA");
		assertEquals(DataType.ANY_DATA, column.getDataType());
	}

	@Test
	public void testNumber() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("NUMBER(6)");
		assertEquals(DataType.DECIMAL, column.getDataType());
	}

	@Test
	public void testDecimal() {
		Column column=new Column();
		column.setDialect(dialect);
		column.setDataTypeName("NUMBER(6)");
		assertEquals(DataType.DECIMAL, column.getDataType());
	}

}
