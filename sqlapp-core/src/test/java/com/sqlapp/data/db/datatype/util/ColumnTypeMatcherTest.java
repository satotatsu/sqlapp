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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ColumnTypeMatcherTest {

	@Test
	void test() {
		SimpleColumnTypeMatcher columnTypeNameMatcher = new SimpleColumnTypeMatcher("DOUBLE", "DOUBLE PRECISION");
		Optional<TypeInformation> columnOp = columnTypeNameMatcher.match("DOUBLE");
		assertTrue(columnOp.isPresent());
		columnOp = columnTypeNameMatcher.match("DOUBLE PRECISION");
		assertTrue(columnOp.isPresent());
		columnOp = columnTypeNameMatcher.match("AAAA PRECISION");
		assertTrue(columnOp.isEmpty());
	}

}
