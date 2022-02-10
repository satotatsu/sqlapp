/*
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SqlBuilderTestForGroovy {

	@Test
	void testPlusObject() {
		SqlBuilder expect=new SqlBuilder();
		expect.plus("select");
		SqlBuilder builder=new SqlBuilder();
		assertEquals(expect.toString(), (builder+"select").toString());
		assertTrue((builder+"") instanceof SqlBuilder);
	}

	
}
