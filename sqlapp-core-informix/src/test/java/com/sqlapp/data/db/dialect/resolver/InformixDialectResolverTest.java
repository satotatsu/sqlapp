/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 *
 * sqlapp-core-informix is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-informix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-informix.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.resolver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class InformixDialectResolverTest {

	private InformixDialectResolver resolver=new InformixDialectResolver();
	
	@Test
	public void testGetDialectStringIntInt() {
		assertEquals("Informix Dynamic Server",resolver.getDialect("Informix", 0, 0).getProductName());
	}

}
