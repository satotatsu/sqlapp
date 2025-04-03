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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PrecisionScaleColumnTypeMatcherTest {

	@Test
	void test() {
		PrecisionScaleColumnTypeMatcher columnTypeNameMatcher = new PrecisionScaleColumnTypeMatcher("DECIMAL");
		TypeInformation column = createCoumn("DECIMAL(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		column = createCoumn("DECIMAL(30 )", columnTypeNameMatcher);
		assertEquals(30, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		column = createCoumn("DECIMAL( 41,2 )", columnTypeNameMatcher);
		assertEquals(41, column.getLength().get().intValue());
		assertEquals(2, column.getScale().get().intValue());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		columnTypeNameMatcher.setDefaultPrecision(() -> 15);
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertEquals(15, column.getLength().get().intValue());
		assertTrue(column.getScale().isEmpty());
		assertEquals("DECIMAL", column.getDataTypeName().get());
		//
		columnTypeNameMatcher.setDefaultPrecision(() -> 16);
		columnTypeNameMatcher.setDefaultScale(() -> 2);
		column = createCoumn("DECIMAL", columnTypeNameMatcher);
		assertEquals(16, column.getLength().get().intValue());
		assertEquals(2, column.getScale().get().intValue());
		assertEquals("DECIMAL", column.getDataTypeName().get());
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

}
