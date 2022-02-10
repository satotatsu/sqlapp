/**
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

package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.ClassFinder;

public class PackageTest {

	@Test
	public void testAll() {
		ClassFinder classFinder=new ClassFinder();
		classFinder.setFilter(c->{
			return DbDataType.class.isAssignableFrom(c);
		});
		for(DataType types:DataType.values()){
			assertNotEquals(types.getSurrogate(), types);
			assertNotEquals(types.getUpperSurrogate(), types);
		}
	}


	@Test
	public void testGetTypeName() {
		assertEquals("INT", DataType.INT.getTypeName());
	}

	
}
