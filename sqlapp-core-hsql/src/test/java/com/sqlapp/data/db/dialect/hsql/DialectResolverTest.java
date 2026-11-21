/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

public class DialectResolverTest {
	@Test
	public void testVersionBoundaries() {
		var resolver = DialectResolver.getInstance();
		assertEquals(resolver.getDialect("hsql", 2, 4), resolver.getDialect("hsql", 3, 0));
		assertEquals(resolver.getDialect("hsql", 2, 4), resolver.getDialect("hsql", 2, 7));
		org.junit.jupiter.api.Assertions.assertTrue(resolver.getDialect("hsql", 2, 0).supportsColumnFormula());
		assertEquals(DialectHolder.defaultDialect2_0_0, resolver.getDialect("hsql", 2, 0));
	}

	@Test
	public void testHsqldb1xIsRejected() {
		var resolver = DialectResolver.getInstance();
		for (int[] version : new int[][] { { 1, 8 }, { 1, 7 }, { 1, 0 } }) {
			var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
					() -> resolver.getDialect("HSQL Database Engine", version[0], version[1], null));
			org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("HSQLDB 2.0 or later"),
					failure.getMessage());
		}
	}

	@Test
	public void testUnknownVersionUsesOldestSupportedDialect() {
		assertEquals(DialectHolder.defaultDialect2_0_0, DialectResolver.getInstance().getDialect("HSQL", 0, 0, null));
	}

	@Test
	public void testCompareTo() {
		Dialect dialect1 = DialectResolver.getInstance().getDialect("hsql", 2, 0);
		Dialect dialect2 = DialectResolver.getInstance().getDialect("hsql", 2, 1);
		Dialect dialect3 = DialectResolver.getInstance().getDialect("hsql", 2, 0);
		assertEquals(-1, dialect1.compareTo(dialect2));
		assertEquals(1, dialect2.compareTo(dialect1));
		assertEquals(0, dialect1.compareTo(dialect3));
	}

}
