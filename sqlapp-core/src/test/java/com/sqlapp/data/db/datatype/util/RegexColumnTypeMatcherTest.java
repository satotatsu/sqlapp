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

class RegexColumnTypeMatcherTest {

	@Test
	void testChar() {
		RegexColumnTypeMatcher columnTypeNameMatcher = new RegexColumnTypeMatcher(
				"(?<dataTypeName>char(acter)?|bpchar(acter)?)\\s*(\\((?<length>\\s*[0-9]+\\s*)\\))?", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("length");
					if (value != null) {
						c.setLength(m.group("length"));
					}
				});
		TypeInformation column = createCoumn("BPCHAR(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertEquals("BPCHAR", column.getDataTypeName().get());
		//
		column = createCoumn("BPCHARACTER(30 )", columnTypeNameMatcher);
		assertEquals(30, column.getLength().get().intValue());
		assertEquals("BPCHARACTER", column.getDataTypeName().get());
		//
		column = createCoumn("CHARACTER( 41 )", columnTypeNameMatcher);
		assertEquals(41, column.getLength().get().intValue());
		assertEquals("CHARACTER", column.getDataTypeName().get());
		//
		column = createCoumn("CHAR", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("CHAR", column.getDataTypeName().get());
	}

	private TypeInformation createCoumn(String value, RegexColumnTypeMatcher columnTypeNameMatcher) {
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match(value);
		return columnOp.get();
	}

	@Test
	void testVarChar1() {
		RegexColumnTypeMatcher columnTypeNameMatcher = new RegexColumnTypeMatcher(
				"(VARCHAR(ACTER)?)\\s*(\\((?<length>\\s*[0-9]+\\s*)\\))?", (m, c) -> {
					c.setDataTypeName("VARCHAR");
					String value = m.group("length");
					if (value != null) {
						c.setLength(m.group("length"));
					}
				});
		TypeInformation column = createCoumn("VARCHAR( 41 )", columnTypeNameMatcher);
		assertEquals(41, column.getLength().get().intValue());
		assertEquals("VARCHAR", column.getDataTypeName().get());
		//
		column = createCoumn("VARCHAR", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("VARCHAR", column.getDataTypeName().get());
		//
		column = createCoumn("VARCHAR(4)", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertEquals("VARCHAR", column.getDataTypeName().get());
	}

	@Test
	void testVarChar2() {
		RegexColumnTypeMatcher columnTypeNameMatcher = new RegexColumnTypeMatcher(
				"(CHAR(ACTER)?)\\s*(\\((?<length>\\s*[0-9]+\\s*)\\))?(\\s+VARYING)?", (m, c) -> {
					c.setDataTypeName("VARCHAR");
					String value = m.group("length");
					if (value != null) {
						c.setLength(m.group("length"));
					}
				});
		TypeInformation column = createCoumn("CHARACTER( 41 ) VARYING", columnTypeNameMatcher);
		assertEquals(41, column.getLength().get().intValue());
		assertEquals("VARCHAR", column.getDataTypeName().get());
		//
		column = createCoumn("CHAR VARYING", columnTypeNameMatcher);
		assertTrue(column.getLength().isEmpty());
		assertEquals("VARCHAR", column.getDataTypeName().get());
		//
		column = createCoumn("CHAR(4) VARYING", columnTypeNameMatcher);
		assertEquals(4, column.getLength().get().intValue());
		assertEquals("VARCHAR", column.getDataTypeName().get());
	}

}
