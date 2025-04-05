/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.SimpleColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

class SpannerArrayColumnTypeMatcherTest {

	@Test
	void testBoolean() {
		ColumnTypeMatcher columnTypeNameMatcher = createMatcher("BOOL", "BOOLEAN");
		TypeInformation column = createCoumn("BOOL", columnTypeNameMatcher);
		assertTrue(column.getDataTypeName().isEmpty());
		assertTrue(column.getArrayDimension().isEmpty());
		//
		column = createCoumn("Array<BOOL>", columnTypeNameMatcher);
		assertTrue(column.getDataTypeName().isEmpty());
		assertEquals(1, column.getArrayDimension().getAsInt());
	}

	private ColumnTypeMatcher createMatcher(String... dataTypeName) {
		ColumnTypeMatcher internalMatcher = new SimpleColumnTypeMatcher(dataTypeName);
		return new SpannerArrayColumnTypeMatcher(internalMatcher);
	}

	private TypeInformation createCoumn(String value, ColumnTypeMatcher columnTypeMatcher) {
		Optional<TypeInformation> columnOp = columnTypeMatcher.match(value);
		return columnOp.get();
	}

}
