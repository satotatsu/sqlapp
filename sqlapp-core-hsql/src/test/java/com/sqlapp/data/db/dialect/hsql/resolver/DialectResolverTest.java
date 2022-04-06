/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.hsql.resolver;


import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.Hsql2_3_0;
import com.sqlapp.data.db.dialect.hsql.Hsql2_4_0;

public class DialectResolverTest {

	@Test
	public void testGetDialect() {
		Dialect dialect=DialectResolver.getInstance().getDialect("HSQL", 2, 3, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Hsql2_3_0);
		dialect=DialectResolver.getInstance().getDialect("HSQL", 2, 4, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Hsql2_4_0);
		dialect=DialectResolver.getInstance().getDialect("HSQL", 2, 5, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Hsql2_4_0);
	}

}
