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
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.sqlapp.util.CommonUtils;

/**
 * Abstract Array Converter
 * 
 * @author tatsuo satoh
 * 
 * @param <T>
 * @param <S>
 */
@SuppressWarnings("serial")
public abstract class AbstractArrayConverter<T, S> implements Converter<T> {

	private final Converter<S> unitConverter;

	private T defaultObject = null;

	protected AbstractArrayConverter(Converter<S> unitConverter) {
		this.unitConverter = unitConverter;
	}

	@Override
	public T convertObject(Object value) {
		return convertObject(value, null);
	}

	@Override
	public T convertObject(Object value, Connection conn) {
		if (value==null){
			return null;
		}else if (value.getClass().isArray()) {
			int size = Array.getLength(value);
			T array = newArrayInstance(size);
			for (int i = 0; i < size; i++) {
				Object obj = Array.get(value, i);
				setArray(array, i, unitConverter.convertObject(obj, conn));
			}
			return array;
		} else if (value instanceof Collection) {
			Collection<?> c = (Collection<?>) value;
			T array = newArrayInstance(c.size());
			int i = 0;
			for (Object obj : c) {
				setArray(array, i, unitConverter.convertObject(obj, conn));
				i++;
			}
			return array;
		} else if (value instanceof Iterable) {
			Iterable<?> c = (Iterable<?>) value;
			List<S> list=CommonUtils.list();
			for (Object obj : c) {
				list.add(unitConverter.convertObject(obj, conn));
			}
			T array = newArrayInstance(list.size());
			System.arraycopy(list.toArray(), 0, array, 0, list.size());
			return array;
		}
		T array = newArrayInstance(1);
		setArray(array, 0, unitConverter.convertObject(value, conn));
		return array;
	}

	protected void setArray(T array, int i, S value) {
		Array.set(array, i, value);
	}

	/**
	 * @return the unitConverter
	 */
	public Converter<S> getUnitConverter() {
		return unitConverter;
	}

	@Override
	public T getDefaultValue() {
		return copy(defaultObject);
	}

	@Override
	public Converter<T> setDefaultValue(T defaultObject) {
		this.defaultObject = defaultObject;
		return this;
	}

	@Override
	public String convertString(T value) {
		if (value == null) {
			return null;
		}
		if (!value.getClass().isArray()) {
			return null;
		}
		Object[] arr = (Object[]) value;
		return Arrays.deepToString(arr);
	}

	@Override
	public T copy(Object value) {
		return convertObject(value);
	}

	protected abstract T newArrayInstance(int size);

}
