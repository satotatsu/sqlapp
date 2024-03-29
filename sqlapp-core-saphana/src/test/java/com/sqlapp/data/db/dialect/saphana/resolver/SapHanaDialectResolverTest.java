/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.saphana.resolver.SapHanaDialectResolver;

public class SapHanaDialectResolverTest {

	private SapHanaDialectResolver resolver=new SapHanaDialectResolver();
	
	@Test
	public void testGetDialectStringIntInt() {
		assertEquals("SAP HANA",resolver.getDialect("SAP HANA", 0, 0).getProductName());
	}

}
