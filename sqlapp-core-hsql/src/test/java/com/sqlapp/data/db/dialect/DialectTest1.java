/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

public class DialectTest1 {

	Dialect dialect = DialectResolver.getInstance().getDialect("hsql",
			2, 1);
	
	@Test
	public void testNvarchar() {
		Column column=new Column();
		column.setDataType(DataType.NVARCHAR);
		column.setLength(255);
		DbDataType<?> dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.VARCHAR, dbDataType.getDataType());
	}

	@Test
	public void testNchar() {
		Column column=new Column();
		column.setDataType(DataType.NCHAR);
		column.setLength(255);
		DbDataType<?> dbDataType=dialect.getDbDataType(column);
		assertEquals(DataType.CHAR, dbDataType.getDataType());
	}

}
