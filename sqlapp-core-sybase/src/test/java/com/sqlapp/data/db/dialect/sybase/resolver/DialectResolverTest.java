/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.sybase.Sybase;

public class DialectResolverTest {

	@Test
	public void testGetDialect() {
		Dialect dialect = DialectResolver.getInstance().getDialect("Sybase", 0, 0, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Sybase);
		dialect = DialectResolver.getInstance().getDialect("Adaptive Server", 0, 0, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Sybase);
		dialect = DialectResolver.getInstance().getDialect("ACE", 0, 0, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Sybase);
	}

	@Test
	public void testServiceLoader() {
		ServiceLoader<ProductNameDialectResolver> loader = ServiceLoader.load(ProductNameDialectResolver.class);
		boolean find = false;
		for (ProductNameDialectResolver resolver : loader) {
			if (resolver instanceof SybaseDialectResolver) {
				find = true;
			}
		}
		assertTrue(find);
	}

}
