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

import static com.sqlapp.util.CommonUtils.last;

import java.sql.Connection;

/**
 * 複数のコンバータを合成するコンバータ
 * @author satoh
 *
 */
public class PipeConverter extends AbstractConverter<Object> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -1013527725902458784L;
	private Converter<?>[] converters=null;
	
	public PipeConverter(final Converter<?>... converters){
		this.converters=converters;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#convertObject(java.lang.Object)
	 */
	@Override
	public Object convertObject(final Object value) {
		Object ret=value;
		for(final Converter<?> converter:converters){
			ret=converter.convertObject(ret);
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.AbstractConverter#convertObject(java.lang.Object, java.sql.Connection)
	 */
	@Override
	public Object convertObject(final Object value, final Connection conn) {
		Object ret=value;
		for(final Converter<?> converter:converters){
			ret=converter.convertObject(ret, conn);
		}
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public String convertString(final Object value) {
		final Converter<?> converter= last(converters);
		return ((Converter<Object>)converter).convertString(value);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public Object copy(final Object obj){
		if (obj==null){
			return null;
		}
		return last(converters).copy(convertObject(obj));
	}
}
