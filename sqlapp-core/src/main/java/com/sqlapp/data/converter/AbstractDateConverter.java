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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;

import java.time.ZonedDateTime;

/**
 */
public abstract class AbstractDateConverter<T,S> extends AbstractConverter<T>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	/**
	 * zoned Date time converter
	 */
	private ZonedDateTimeConverter zonedDateTimeConverter=null;

	public ZonedDateTimeConverter getZonedDateTimeConverter() {
		return zonedDateTimeConverter;
	}

	public S setZonedDateTimeConverter(ZonedDateTimeConverter zonedDateTimeConverter) {
		this.zonedDateTimeConverter = zonedDateTimeConverter;
		return instance();
	}
	
	@SuppressWarnings("unchecked")
	protected S instance(){
		return (S)this;
	}
	
	@Override
	public String convertString(T value) {
		if (value == null) {
			return null;
		}
		return convertStringInternal(value);
	}
	
	protected String convertStringInternal(Object value) {
		if (value == null) {
			return null;
		}
		ZonedDateTime zonedDateTime=getZonedDateTimeConverter().convertObject(value);
		String ret= getZonedDateTimeConverter().convertString(zonedDateTime);
		return ret;
	}

	/**
	 * 
	 * @param formats
	 */
	protected static ZonedDateTimeConverter newZonedDateTimeConverter(Object... formats) {
		ZonedDateTimeConverter dateTimeConverter = new ZonedDateTimeConverter();
		dateTimeConverter.setParseFormats(formats);
		return dateTimeConverter;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof AbstractDateConverter)){
			return false;
		}
		AbstractDateConverter<?,?> cst=cast(obj);
		if (!eq(this.getZonedDateTimeConverter(), cst.getZonedDateTimeConverter())){
			return false;
		}
		return true;
	}
}
