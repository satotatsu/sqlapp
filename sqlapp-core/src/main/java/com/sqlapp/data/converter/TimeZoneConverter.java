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

import static com.sqlapp.util.CommonUtils.*;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * TimeZoneType Converter
 * @author SATOH
 *
 */
public class TimeZoneConverter extends AbstractConverter<TimeZone>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6136157088033787646L;

	@Override
	public TimeZone convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof TimeZone){
			return (TimeZone)value;
		}else if (value instanceof ZoneId){
			return TimeZone.getTimeZone(((ZoneId)value).getId());
		}else if (value instanceof ZoneOffset){
			return TimeZone.getTimeZone(((ZoneOffset)value).getId());
		}
		return TimeZone.getTimeZone(value.toString());
	}

	@Override
	public String convertString(TimeZone value) {
		if (value==null){
			return null;
		}
		return value.getID();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof TimeZoneConverter)){
			return false;
		}
		TimeZoneConverter con=cast(obj);
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
	public TimeZone copy(Object obj){
		if (obj==null){
			return null;
		}
		return (TimeZone)convertObject(obj);
	}
}
