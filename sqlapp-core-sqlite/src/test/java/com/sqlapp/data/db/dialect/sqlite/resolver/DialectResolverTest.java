/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlite.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;

public class DialectResolverTest {

	@Test
	public void testGetDialect() {
		Dialect dialect = DialectResolver.getInstance().getDialect("SQLite", 0, 0, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Sqlite);
	}

	@Test
	public void testServiceLoader() {
		ServiceLoader<ProductNameDialectResolver> loader = ServiceLoader.load(ProductNameDialectResolver.class);
		boolean find = false;
		for (ProductNameDialectResolver resolver : loader) {
			if (resolver instanceof SqliteDialectResolver) {
				find = true;
			}
		}
		assertTrue(find);
	}

}
