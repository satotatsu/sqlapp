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
package com.sqlapp.data.schemas;

import java.text.ParseException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.SimpleBeanUtils;
/**
 *
 */
public class SchemasAutoTest {

	@Test
	public void testAll() throws ParseException {
		Set<Class<?>> classes=SchemaUtils.getDbObjectClasses();
		for(Class<?> clazz:classes){
			DbObject<?> obj=SchemaUtils.createInstance(clazz.getSimpleName());
			System.out.println(clazz.getSimpleName());
			Map<String,Object> map=SimpleBeanUtils.toMap(obj);
			System.out.println(map);
		}
	}
	
}
