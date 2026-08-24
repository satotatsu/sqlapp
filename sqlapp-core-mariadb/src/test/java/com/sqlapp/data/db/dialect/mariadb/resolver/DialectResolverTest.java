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
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_00;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_20;
import com.sqlapp.data.db.dialect.mariadb.Mariadb10_05;
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
		Dialect dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 0, 4);
		assertTrue(dialect instanceof Mariadb10_00);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 0, 5);
		assertTrue(dialect instanceof Mariadb10_05);
		dialect = DialectResolver.getInstance().getDialect("MariaDB", 10, 2, 0);
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
		assertMetadataReaders("MariadbCatalog10_00Reader", "MariadbSchema10_00Reader",
				"MySqlTable564Reader", "MySqlColumn564Reader", 10, 0, 0);
		assertMetadataReaders("MariadbCatalog10_00Reader", "MariadbSchema10_00Reader",
				"MySqlTable564Reader", "MySqlColumn564Reader", 10, 0, 4);
		assertMetadataReaders("MariadbCatalog10_05Reader", "MariadbSchema10_00Reader",
				"MySqlTable564Reader", "MySqlColumn564Reader", 10, 0, 5);
		assertMetadataReaders("MariadbCatalog10_05Reader", "MariadbSchema10_00Reader",
				"MySqlTable564Reader", "MySqlColumn564Reader", 10, 2, 4);
		assertMetadataReaders("MariadbCatalog10_27Reader", "MariadbSchema10_27Reader",
				"MariadbTable10_27Reader", "MariadbColumn10_27Reader", 10, 2, 7);
		assertMetadataReaders("MariadbCatalog10_27Reader", "MariadbSchema10_27Reader",
				"MariadbTable10_27Reader", "MariadbColumn10_27Reader", 10, 11, 8);
		assertMetadataReaders("MariadbCatalog11_40Reader", "MariadbSchema11_40Reader",
				"MariadbTable11_40Reader", "MariadbColumn11_40Reader", 11, 4, 9);
		assertMetadataReaders("MariadbCatalog11_50Reader", "MariadbSchema11_50Reader",
				"MariadbTable11_40Reader", "MariadbColumn11_40Reader", 11, 5, 0);
		assertMetadataReaders("MariadbCatalog11_50Reader", "MariadbSchema11_50Reader",
				"MariadbTable11_40Reader", "MariadbColumn11_40Reader", 11, 8, 0);
		assertMetadataReaders("MariadbCatalog11_50Reader", "MariadbSchema11_50Reader",
				"MariadbTable11_40Reader", "MariadbColumn11_40Reader", 12, 1, 0);
	}

	private void assertMetadataReaders(String catalogClass, String schemaClass,
			String tableClass, String columnClass, int major, int minor, int revision) {
		Dialect dialect = DialectResolver.getInstance().getDialect("MariaDB", major, minor, revision);
		var catalogReader = dialect.getCatalogReader();
		assertEquals(catalogClass, catalogReader.getClass().getSimpleName());
		var schemaReader = catalogReader.getSchemaReader();
		assertEquals(schemaClass, schemaReader.getClass().getSimpleName());
		var tableReader = schemaReader.getTableReader();
		assertEquals(tableClass, tableReader.getClass().getSimpleName());
		assertEquals(columnClass,
				tableReader.getColumnReader().getClass().getSimpleName());
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
