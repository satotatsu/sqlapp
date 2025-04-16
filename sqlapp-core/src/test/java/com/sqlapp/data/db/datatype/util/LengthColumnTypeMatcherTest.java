/**
 * Copyright (C) 2025-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

class LengthColumnTypeMatcherTest {

	@Test
	void test() {
		LengthColumnTypeMatcher columnTypeNameMatcher = new LengthColumnTypeMatcher("CHAR");
		TypeInformation column = createCoumn("CHAR(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		//
		column = createCoumn("CHAR(30 )", columnTypeNameMatcher);
		assertEquals(30, column.getLength().get().intValue());
		//
		column = createCoumn("CHAR( 41 )", columnTypeNameMatcher);
		assertEquals(41, column.getLength().get().intValue());
		//
		column = createCoumn("CHAR", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		//
		columnTypeNameMatcher.setDefaultLength(() -> 12L);
		column = createCoumn("CHAR", columnTypeNameMatcher);
		assertEquals(12, column.getLength().get().intValue());
		//
		columnTypeNameMatcher.setDefaultLength(() -> 15L);
		column = createCoumn("CHAR", columnTypeNameMatcher);
		assertEquals(15, column.getLength().get().intValue());
		//
		column = createCoumn("CHAR(1K)", columnTypeNameMatcher);
		assertEquals(1024, column.getLength().get().intValue());
		//
		column = createCoumn("CHAR(1M)", columnTypeNameMatcher);
		assertEquals(1048576, column.getLength().get().intValue());
		//
		column = createCoumn("CHAR(1G)", columnTypeNameMatcher);
		assertEquals(1073741824, column.getLength().get().intValue());
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

	@Test
	void test2() {
		LengthColumnTypeMatcher columnTypeNameMatcher = new LengthColumnTypeMatcher("CHAR(ACTER)?",
				"FOR\\s+BIT\\s+DATA");
		TypeInformation column = createCoumn("char(4) for bit data", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		column = createCoumn("character(5) for bit data", columnTypeNameMatcher);
		assertEquals(5, column.getLength().get().intValue());
		column = createCoumn("character for bit data", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
	}
}
