/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.Db2_1150;
import com.sqlapp.data.db.dialect.db2.Db2_1210;
import com.sqlapp.data.db.dialect.db2.Db2_1212;
import com.sqlapp.data.db.dialect.db2.Db2_1215;
import com.sqlapp.data.db.dialect.db2.Db2_970;
import com.sqlapp.data.db.dialect.db2.Db2_980;
import com.sqlapp.data.db.dialect.resolver.ProductNameDialectResolver;

public class DialectResolverTest {

	@Test
	public void testGetDialect() {
		Dialect dialect = DialectResolver.getInstance().getDialect("DB2", 9, 7, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Db2_970);
		dialect = DialectResolver.getInstance().getDialect("DB2", 9, 9, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Db2_980);
		dialect = DialectResolver.getInstance().getDialect("DB2", 11, 1, 0);
		System.out.println(dialect);
		assertTrue(dialect instanceof Db2_1110);
		dialect = DialectResolver.getInstance().getDialect("DB2", 11, 5, 0);
		assertTrue(dialect instanceof Db2_1150);
		dialect = DialectResolver.getInstance().getDialect("DB2", 12, 1, 0);
		assertTrue(dialect instanceof Db2_1210);
		assertTrue(dialect.getDbDataTypes().getDbTypeStrict(DataType.VECTOR) == null);
		dialect = DialectResolver.getInstance().getDialect("DB2", 12, 1, 2);
		assertTrue(dialect instanceof Db2_1212);
		assertTrue(dialect.getDbDataTypes().getDbTypeStrict(DataType.VECTOR) != null);
		dialect = DialectResolver.getInstance().getDialect("DB2", 12, 1, 5);
		assertTrue(dialect instanceof Db2_1215);
	}

	@Test
	public void testServiceLoader() {
		ServiceLoader<ProductNameDialectResolver> loader = ServiceLoader.load(ProductNameDialectResolver.class);
		boolean find = false;
		for (ProductNameDialectResolver resolver : loader) {
			if (resolver instanceof Db2DialectResolver) {
				find = true;
			}
		}
		assertTrue(find);
	}

}
