/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.db.datatype.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

class FirebirdArrayRegexColumnTypeNameMatcherTest {

	@Test
	void testChar() {
		RegexColumnTypeMatcher columnTypeNameMatcher = new FirebirdArrayRegexColumnTypeNameMatcher(
				"(?<dataTypeName>char(acter)?)\\s*(\\((?<length>\\s*[0-9]+\\s*)\\))?", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("length");
					if (value != null) {
						c.setLength(m.group("length"));
					}
				});
		TypeInformation column = createCoumn("CHAR(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertEquals("CHAR", column.getDataTypeName().get());
		assertFalse(column.getArrayDimension().isPresent());
		//
		column = createCoumn("CHAR [1:2]", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
		assertEquals(1, column.getArrayDimension().getAsInt());
		//
		column = createCoumn("CHAR [0:10,0:20]", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
		assertEquals(2, column.getArrayDimension().getAsInt());
	}

	private TypeInformation createCoumn(String value, RegexColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

}
