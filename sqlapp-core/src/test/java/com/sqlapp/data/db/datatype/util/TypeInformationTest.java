/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */
package com.sqlapp.data.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.data.schemas.OperatorArgument;
import com.sqlapp.data.schemas.OperatorBinding;
import com.sqlapp.data.schemas.OperatorBindingArgument;
import com.sqlapp.data.schemas.OperatorClass;
import com.sqlapp.data.schemas.PartitionFunction;
import com.sqlapp.data.schemas.Sequence;

class TypeInformationTest {

	@Test
	void testDataType1() {
		// AbstractColumn
		Column column = new Column();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataType());
		typeInfomation.setDataType(DataType.NCHAR);
		typeInfomation.set(column);
		assertEquals(DataType.NCHAR, column.getDataType());
	}

	@Test
	void testDataTypeName1() {
		// AbstractColumn
		Column column = new Column();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testDataTypeName2() {
		// AbstractColumn
		OperatorArgument column = new OperatorArgument();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testDataTypeName3() {
		// AbstractColumn
		OperatorBinding column = new OperatorBinding();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testDataTypeName4() {
		// AbstractColumn
		OperatorBindingArgument column = new OperatorBindingArgument();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testDataTypeName5() {
		// AbstractColumn
		OperatorClass column = new OperatorClass("aaa");
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testDataTypeName6() {
		// AbstractColumn
		Sequence column = new Sequence();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testDataTypeName7() {
		// AbstractColumn
		PartitionFunction column = new PartitionFunction();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testDataTypeName8() {
		// AbstractColumn
		FunctionReturning column = new FunctionReturning(new Function());
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getDataTypeName());
		typeInfomation.setDataTypeName("typea");
		typeInfomation.set(column);
		assertEquals("typea", column.getDataTypeName());
	}

	@Test
	void testLength() {
		// AbstractColumn
		Column column = new Column();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getLength());
		typeInfomation.setLength(10);
		typeInfomation.set(column);
		assertEquals(10L, column.getLength());
	}

	@Test
	void testOctetLength() {
		// AbstractColumn
		Column column = new Column();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getOctetLength());
		typeInfomation.setOctetLength(12);
		typeInfomation.set(column);
		assertEquals(12L, column.getOctetLength());
	}

	@Test
	void testScale() {
		// AbstractColumn
		Column column = new Column();
		TypeInformation typeInfomation = new TypeInformation();
		assertNull(column.getScale());
		typeInfomation.setScale(11);
		typeInfomation.set(column);
		assertEquals(11, column.getScale());
	}

	@Test
	void testArrayDimansion() {
		// AbstractColumn
		Column column = new Column();
		TypeInformation typeInfomation = new TypeInformation();
		assertEquals(0, column.getArrayDimension());
		typeInfomation.setArrayDimension(3);
		typeInfomation.set(column);
		assertEquals(3, column.getArrayDimension());
	}

	@Test
	void testValues() {
		// AbstractColumn
		List<String> list = List.of("1", "2");
		Column column = new Column();
		TypeInformation typeInfomation = new TypeInformation();
		assertEquals(0, column.getValues().size());
		typeInfomation.setValues(list);
		typeInfomation.set(column);
		assertEquals(2, column.getValues().size());
		assertEquals("[1, 2]", column.getValues().toString());
	}
}
