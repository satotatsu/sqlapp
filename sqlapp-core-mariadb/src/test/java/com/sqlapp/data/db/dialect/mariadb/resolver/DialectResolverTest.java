/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mariadb.
 *
 * sqlapp-core-mariadb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mariadb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mariadb.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mariadb.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_20;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_27;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_30;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_50;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_40;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_50;
import com.sqlapp.data.db.dialect.mariadb.Mariadb11_80;
import com.sqlapp.data.db.dialect.mariadb.Mariadb12_10;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

public class DialectResolverTest {

	@Test
	public void testGetDialect() {
		Dialect dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 2, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Mariadb10_20);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 2, 7);
		System.out.println(dialect);
		assertTrue(dialect instanceof Mariadb10_27);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 4, 8);
		assertTrue(dialect instanceof Mariadb10_30);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 5, 0);
		assertTrue(dialect instanceof Mariadb10_50);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 11, 8);
		assertTrue(dialect instanceof Mariadb10_50);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 11, 4, 5);
		assertTrue(dialect instanceof Mariadb11_40);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 11, 5, 2);
		assertTrue(dialect instanceof Mariadb11_50);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 11, 8, 2);
		assertTrue(dialect instanceof Mariadb11_80);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 12, 1, 2);
		assertTrue(dialect instanceof Mariadb12_10);
	}

	@Test
	public void testMetadataReaderVersionBoundaries() {
		assertCatalogReader("MariadbCatalog10_00Reader", 10, 0, 0);
		assertCatalogReader("MariadbCatalog10_00Reader", 10, 2, 4);
		assertCatalogReader("MariadbCatalog10_27Reader", 10, 2, 7);
		assertCatalogReader("MariadbCatalog10_27Reader", 10, 11, 8);
		assertCatalogReader("MariadbCatalog11_40Reader", 11, 4, 9);
		assertCatalogReader("MariadbCatalog11_50Reader", 11, 5, 0);
		assertCatalogReader("MariadbCatalog11_50Reader", 11, 8, 0);
		assertCatalogReader("MariadbCatalog11_50Reader", 12, 1, 0);
	}

	private void assertCatalogReader(String className, int major, int minor, int revision) {
		Dialect dialect = DialectResolver.getInstance().getDialect("MariaDB", major, minor, revision);
		assertEquals(className, dialect.getCatalogReader().getClass().getSimpleName());
	}

	@Test
	public void testServiceLoader() {
		ServiceLoader<ProductNameDialectResolver> loader = ServiceLoader.load(ProductNameDialectResolver.class);
		boolean find = false;
		for (ProductNameDialectResolver resolver : loader) {
			if (resolver instanceof MariadbDialectResolver) {
				find = true;
			}
		}
		assertTrue(find);
	}

}
