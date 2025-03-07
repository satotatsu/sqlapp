/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.converter;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.converter.AbstractConverter;
import com.sqlapp.data.converter.ConverterException;

public abstract class AbstractFromObjectConverter<T, U> extends AbstractConverter<T> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;

	@SuppressWarnings("unchecked")
	@Override
	public T convertObject(Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		} else if (isTargetInstanceof(value)) {
			return (T) value;
		} else if (value instanceof String) {
			String val = (String) value;
			return toObjectFromString(val);
		} else if (isInstanceof(value)) {
			return toObject((U) value);
		}
		throw new ConverterException("Can't convert from " + value + " to " + getObjectClass().getName() + ".");
	}

	protected abstract boolean isTargetInstanceof(Object value);

	protected abstract boolean isInstanceof(Object value);

	protected abstract Class<U> getObjectClass();

	protected abstract T toObjectFromString(String value);

	protected abstract T toObject(U value);

	protected abstract T clone(T value);

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public T copy(Object obj) {
		if (obj == null) {
			return null;
		}
		return clone(convertObject(obj));
	}
}