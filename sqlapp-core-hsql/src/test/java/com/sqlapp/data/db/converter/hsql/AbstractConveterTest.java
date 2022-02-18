/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.converter.hsql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.converter.Converter;

public abstract class AbstractConveterTest<U> {

	@Test
	public void testToConvert() {
		Converter<?> toConverter=createToConverter();
		Converter<?> fromConverter=createFromConverter();
		Object obj1=newInstance();
		Object toObj=toConverter.convertObject(obj1);
		assertFalse(obj1.getClass().equals(toObj.getClass()));
		Object obj2=fromConverter.convertObject(toObj);
		assertEquals(obj1, obj2);
	}

	protected abstract U newInstance(); 

	protected abstract Converter<?> createFromConverter(); 

	protected abstract Converter<?> createToConverter(); 

}
