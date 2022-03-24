/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

public class MetadataReaderUtilsTest {
	Dialect dialect = DialectResolver.getInstance().getDialect("oracle", 11, 0);

	@Test
	public void testGetMetaClass() {

		System.out.println(MetadataReaderUtils.getTypeParameterClass(dialect
				.getCatalogReader().getClass()));
		System.out.println(MetadataReaderUtils.getMetaClass(dialect
				.getCatalogReader().getClass()));
		assertEquals(Catalog.class, MetadataReaderUtils.getMetaClass(dialect
				.getCatalogReader().getClass()));
		assertEquals(
				Schema.class,
				MetadataReaderUtils.getMetaClass(dialect.getCatalogReader()
						.getSchemaReader().getClass()));
		assertEquals(
				Table.class,
				MetadataReaderUtils.getMetaClass(dialect.getCatalogReader()
						.getSchemaReader().getTableReader().getClass()));
		assertEquals(
				View.class,
				MetadataReaderUtils.getMetaClass(dialect.getCatalogReader()
						.getSchemaReader().getViewReader().getClass()));
		assertEquals(
				Mview.class,
				MetadataReaderUtils.getMetaClass(dialect.getCatalogReader()
						.getSchemaReader().getMviewReader().getClass()));
	}

	@Test
	public void testOracleGetTypes() {
		Dialect dialect = DialectResolver.getInstance().getDialect("oracle",
				11, 0);
		Set<Class<?>> types = MetadataReaderUtils.supportedSchemaTypes(dialect
				.getCatalogReader());
		System.out.println(types);
		assertTrue(types.contains(Mview.class));
		assertTrue(types.contains(View.class));
		assertTrue(types.contains(Package.class));
		assertTrue(types.contains(PackageBody.class));
		assertTrue(types.contains(Sequence.class));
		assertFalse(types.contains(Assembly.class));
		assertTrue(types.contains(ForeignKeyConstraint.class));
	}
}
