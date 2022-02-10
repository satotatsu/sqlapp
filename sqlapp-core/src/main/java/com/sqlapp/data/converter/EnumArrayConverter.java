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

package com.sqlapp.data.converter;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;

/**
 * Enum配列のコンバーター
 * 
 * @author tatsuo satoh
 * 
 */
public class EnumArrayConverter extends
		AbstractArrayConverter<Enum<?>[], Enum<?>> {

	private Class<?> clazz;
	Constructor<?> constructor;

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1270901769247762247L;

	@SuppressWarnings("unchecked")
	protected EnumArrayConverter(Class<?> clazz,
			@SuppressWarnings("rawtypes") EnumConverter unitConverter) {
		super(unitConverter);
		this.clazz = unitConverter.getEnumClass();
	}

	@Override
	protected Enum<?>[] newArrayInstance(int size) {
		return (Enum<?>[]) Array.newInstance(clazz, size);
	}

	@Override
	protected void setArray(Enum<?>[] array, int i, Enum<?> value) {
		array[i] = value;
	}
}
