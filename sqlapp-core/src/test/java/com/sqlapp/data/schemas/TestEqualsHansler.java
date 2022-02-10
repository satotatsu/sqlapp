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

public class TestEqualsHansler extends EqualsHandler{

	private EqualsPredicate valueEqualsPredicate=(propertyName, eq, object1,
			object2, value1, value2)->{
		if(!eq){
			throw new RuntimeException(getClass(object1, object1).getSimpleName()+" unmatch property="+propertyName+", value1="+value1+", value2="+value2+", object1="+object1+", object2="+object2);
		}
		return eq;
	};
	
	private Class<?> getClass(Object...args){
		for(Object arg:args){
			if (arg!=null){
				return arg.getClass();
			}
		}
		return null;
	}
	
	public TestEqualsHansler(){
		this.setValueEqualsPredicate(valueEqualsPredicate);
	}
}
