/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.converter;

import java.sql.Connection;

import com.sqlapp.util.CommonUtils;

public abstract class AbstractConverter<T> implements Converter<T>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 3523823400881627578L;
	private T defaultValue=null;

	@Override
	public T convertObject(Object value, Connection conn) {
		return convertObject(value);
	}

	@Override
	public T getDefaultValue() {
		return defaultValue;
	}

	@Override
	public Converter<T> setDefaultValue(T value) {
		this.defaultValue=value;
		return this;
	}

	@Override
	public String convertString(T value) {
		return null;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.getClass().getName().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (obj==null){
			return false;
		}
		if (!(obj instanceof AbstractConverter<?>)){
			return false;
		}
		AbstractConverter<?> cst=AbstractConverter.class.cast(obj);
		if (!CommonUtils.eq(this.getDefaultValue(), cst.getDefaultValue())){
			return false;
		}
		return true;
	}
}
