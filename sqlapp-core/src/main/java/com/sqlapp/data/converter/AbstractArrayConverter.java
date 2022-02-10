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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.sqlapp.exceptions.SqlappDataConversionException;
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

	protected AbstractArrayConverter(final Converter<S> unitConverter) {
		this.unitConverter = unitConverter;
	}

	@Override
	public T convertObject(final Object value) {
		return convertObject(value, null);
	}

	@Override
	public T convertObject(final Object value, final Connection conn) {
		if (value==null){
			return null;
		}else if (value.getClass().isArray()) {
			final int size = Array.getLength(value);
			final T array = newArrayInstance(size);
			for (int i = 0; i < size; i++) {
				final Object obj = Array.get(value, i);
				setArray(array, i, unitConverter.convertObject(obj, conn));
			}
			return array;
		} else if (value instanceof Collection) {
			final Collection<?> c = (Collection<?>) value;
			final T array = newArrayInstance(c.size());
			int i = 0;
			for (final Object obj : c) {
				setArray(array, i, unitConverter.convertObject(obj, conn));
				i++;
			}
			return array;
		} else if (value instanceof Iterable) {
			final Iterable<?> c = (Iterable<?>) value;
			final List<S> list=CommonUtils.list();
			for (final Object obj : c) {
				list.add(unitConverter.convertObject(obj, conn));
			}
			final T array = newArrayInstance(list.size());
			System.arraycopy(list.toArray(), 0, array, 0, list.size());
			return array;
		} else if (value instanceof java.sql.Array) {
			final java.sql.Array c = (java.sql.Array) value;
			Object arr;
			try {
				arr = c.getArray();
				final int size = Array.getLength(arr);
				final T array = newArrayInstance(size);
				for (int i = 0; i < size; i++) {
					final Object obj = Array.get(value, i);
					setArray(array, i, unitConverter.convertObject(obj, conn));
				}
				return array;
			} catch (final SQLException e) {
				throw new SqlappDataConversionException(e);
			} finally {
				try {
					c.free();
				} catch (final SQLException e) {
				}
			}
		}
		final T array = newArrayInstance(1);
		setArray(array, 0, unitConverter.convertObject(value, conn));
		return array;
	}

	protected void setArray(final T array, final int i, final S value) {
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
	public Converter<T> setDefaultValue(final T defaultObject) {
		this.defaultObject = defaultObject;
		return this;
	}

	@Override
	public String convertString(final T value) {
		if (value == null) {
			return null;
		}
		if (!value.getClass().isArray()) {
			return null;
		}
		final Object[] arr = (Object[]) value;
		return Arrays.deepToString(arr);
	}

	@Override
	public T copy(final Object value) {
		return convertObject(value);
	}

	protected abstract T newArrayInstance(int size);

}
