/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;
import com.sqlapp.data.db.dialect.virtica.Virtica;
import com.sqlapp.data.db.dialect.virtica.Virtica11_1_1;
import com.sqlapp.data.db.dialect.virtica.Virtica80;
import com.sqlapp.data.db.dialect.virtica.Virtica90;
import com.sqlapp.data.db.datatype.DataType;

public class DialectResolverTest {

	@Test
	public void testGetDialect() {
		Dialect dialect = DialectResolver.getInstance().getDialect("Virtica", 0, 0, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Virtica);
		dialect = DialectResolver.getInstance().getDialect("Vertica", 12, 0, 0);
		assertTrue(dialect instanceof Virtica90);
		dialect = DialectResolver.getInstance().getDialect("Vertica Analytic Database", 12, 0, 0);
		assertTrue(dialect instanceof Virtica11_1_1);
		assertTrue(dialect.supportsValues());
		dialect = DialectResolver.getInstance().getDialect("Vertica", 11, 1, 0);
		assertTrue(dialect instanceof Virtica90);
		assertFalse(dialect.supportsValues());
		dialect = DialectResolver.getInstance().getDialect("Vertica", 11, 1, 1);
		assertTrue(dialect instanceof Virtica11_1_1);
		assertTrue(dialect.supportsValues());
		dialect = DialectResolver.getInstance().getDialect("Vertica", 8, 1, 0);
		assertTrue(dialect instanceof Virtica80);
		assertFalse(dialect instanceof Virtica90);
		assertFalse("UUID".equals(dialect.getDbDataTypes()
				.getDbType(DataType.UUID).getTypeName()));
		dialect = DialectResolver.getInstance().getDialect("Vertica", 9, 0, 0);
		assertTrue(dialect instanceof Virtica90);
		assertTrue("UUID".equals(dialect.getDbDataTypes()
				.getDbType(DataType.UUID).getTypeName()));
	}

	@Test
	public void testMetadataReaderIsAvailableAcrossVersionBoundaries() {
		assertCatalogReader(7, 1, 0, false);
		assertCatalogReader(7, 2, 0, false);
		assertCatalogReader(8, 0, 0, false);
		assertCatalogReader(9, 0, 0, false);
		assertCatalogReader(11, 1, 0, false);
		assertCatalogReader(11, 1, 1, true);
		assertCatalogReader(25, 1, 0, true);
	}

	private void assertCatalogReader(int major, int minor, int revision,
			boolean supportsStoredProcedures) {
		Dialect dialect = DialectResolver.getInstance().getDialect("Vertica", major, minor, revision);
		assertEquals(supportsStoredProcedures ? "Virtica11_1_1CatalogReader" : "VirticaCatalogReader",
				dialect.getCatalogReader().getClass().getSimpleName());
		assertEquals("VirticaRoleMemberReader", dialect.getCatalogReader()
				.getRoleMemberReader().getClass().getSimpleName());
		assertEquals("VirticaObjectPrivilegeReader", dialect.getCatalogReader()
				.getObjectPrivilegeReader().getClass().getSimpleName());
		assertEquals("VirticaSettingReader", dialect.getCatalogReader()
				.getSettingReader().getClass().getSimpleName());
		if (supportsStoredProcedures) {
			assertNotNull(dialect.getCatalogReader().getSchemaReader().getProcedureReader());
		} else {
			assertNull(dialect.getCatalogReader().getSchemaReader().getProcedureReader());
		}
	}

	@Test
	public void testServiceLoader() {
		ServiceLoader<ProductNameDialectResolver> loader = ServiceLoader.load(ProductNameDialectResolver.class);
		boolean find = false;
		for (ProductNameDialectResolver resolver : loader) {
			if (resolver instanceof VirticaDialectResolver) {
				find = true;
			}
		}
		assertTrue(find);
	}

}
