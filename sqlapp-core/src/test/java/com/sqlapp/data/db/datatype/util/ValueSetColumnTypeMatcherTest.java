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

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ValueSetColumnTypeMatcherTest {

	@Test
	void test() {
		ColumnTypeMatcher columnTypeNameMatcher = new ValueSetColumnTypeMatcher("ENUM");
		TypeInformation column = createCoumn("ENUM(1,2,3,4)", columnTypeNameMatcher);
		assertEquals(4, column.getValues().get().size());
		int i = 0;
		assertEquals("1", column.getValues().get().get(i++));
		assertEquals("2", column.getValues().get().get(i++));
		assertEquals("3", column.getValues().get().get(i++));
		assertEquals("4", column.getValues().get().get(i++));
		assertEquals("ENUM", column.getDataTypeName().get());
		//
		column = createCoumn("ENUM('1','2')", columnTypeNameMatcher);
		assertEquals(2, column.getValues().get().size());
		i = 0;
		assertEquals("'1'", column.getValues().get().get(i++));
		assertEquals("'2'", column.getValues().get().get(i++));
		assertEquals("ENUM", column.getDataTypeName().get());
	}

	@Test
	void testSet() {
		ColumnTypeMatcher columnTypeNameMatcher = new ValueSetColumnTypeMatcher("SET");
		TypeInformation column = createCoumn("SET(1,2,3,4)", columnTypeNameMatcher);
		assertEquals(4, column.getValues().get().size());
		int i = 0;
		assertEquals("1", column.getValues().get().get(i++));
		assertEquals("2", column.getValues().get().get(i++));
		assertEquals("3", column.getValues().get().get(i++));
		assertEquals("4", column.getValues().get().get(i++));
		assertEquals("SET", column.getDataTypeName().get());
		//
		column = createCoumn("SET('1','2')", columnTypeNameMatcher);
		assertEquals(2, column.getValues().get().size());
		i = 0;
		assertEquals("'1'", column.getValues().get().get(i++));
		assertEquals("'2'", column.getValues().get().get(i++));
		assertEquals("SET", column.getDataTypeName().get());
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

}
