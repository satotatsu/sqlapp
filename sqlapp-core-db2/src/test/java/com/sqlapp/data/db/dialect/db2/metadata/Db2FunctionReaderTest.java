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

package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.sql.AbstractDb2SqlFactoryTest;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;

public class Db2FunctionReaderTest extends AbstractDb2SqlFactoryTest {
	@Test
	void quotedReturnColumnsAndBodyParenthesesAreHandled() {
		var reader = new Db2FunctionReader(dialect);
		Function function = new Function("F").setDialect(dialect).setFunctionType(FunctionType.Table);
		reader.setRowTableDefinition(function,
				"CREATE FUNCTION F() RETURNS TABLE (\"amount, net\" DECIMAL(12, 4), \"a\"\" b\" VARCHAR(20)) "
						+ "LANGUAGE SQL RETURN SELECT DECIMAL(1, 12, 4), VARCHAR('a,b)', 20) FROM SYSIBM.SYSDUMMY1");
		var columns = function.getReturning().getTable().getColumns();
		assertEquals(2, columns.size());
		assertEquals("amount, net", columns.get(0).getName());
		assertEquals(DataType.DECIMAL, columns.get(0).getDataType());
		assertEquals(Integer.valueOf(4), columns.get(0).getScale());
		assertEquals("a\" b", columns.get(1).getName());
		assertEquals(DataType.VARCHAR, columns.get(1).getDataType());
		reader.setRowTableDefinition(new Function("EXTERNAL").setFunctionType(FunctionType.Table), null);
	}

	@Test
	public void testReturningType() {
		final Db2FunctionReader functionReader = (Db2FunctionReader) dialect.getCatalogReader().getSchemaReader()
				.getFunctionReader();
		final Function obj = new Function("func");
		obj.setDialect(dialect);
		obj.setFunctionType(FunctionType.Table);
		functionReader.setRowTableDefinition(obj, this.getResource("create_function1.sql"));
		int i = 0;
		Column column = obj.getReturning().getTable().getColumns().get(i++);
		assertEquals("EMPNO", column.getName());
		assertEquals(DataType.CHAR, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
		//
		column = obj.getReturning().getTable().getColumns().get(i++);
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(15), column.getLength());
		//
		//
		column = obj.getReturning().getTable().getColumns().get(i++);
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(12), column.getLength());
		//
		column = obj.getReturning().getTable().getColumns().get(i++);
		assertEquals(DataType.DECIMAL, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
		assertEquals(Integer.valueOf(6), column.getScale());
		//
	}
}
