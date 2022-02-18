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

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.converter.AbstractConverter;
import com.sqlapp.data.converter.Converter;

public abstract class AbstractToObjectConverter<T,U> extends AbstractConverter<T>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6488632910509733050L;
	
	private Converter<U> converter;
	
	protected AbstractToObjectConverter(Converter<U> converter) {
		this.converter=converter;
	}
	
	@SuppressWarnings("unchecked")
	@Override	
	public T convertObject(Object value){
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (isTargetInstanceof(value)){
			return (T)value;
		}
		U obj=converter.convertObject(value);
		return toDbType(obj);
	}
	
	protected abstract boolean isTargetInstanceof(Object value);
	
	protected abstract T newInstance();
	
	protected abstract T toDbType(U obj);

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public T copy(Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}
}
