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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;

import java.sql.Connection;

/**
 * デフォルトのコンバーター
 * @author SATOH
 *
 */
public class DefaultConverter implements Converter<Object>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5536758352929195322L;

	public DefaultConverter(){
		super();
	}
	@Override
	public Object convertObject(Object value, Connection conn) {
		return value;
	}

	@Override
	public Object convertObject(Object value) {
		return value;
	}

	@Override
	public String convertString(Object value) {
		if (value==null){
			return null;
		}
		return value.toString();
	}

	@Override
	public String getDefaultValue() {
		return null;
	}

	@Override
	public Converter<Object> setDefaultValue(Object value) {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof DefaultConverter)){
			return false;
		}
		DateConverter con=cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.getClass().getName().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Object copy(Object obj){
		return obj;
	}

}