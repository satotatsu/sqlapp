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

import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

import com.sqlapp.util.DateUtils;

import static com.sqlapp.util.CommonUtils.*;

/**
 * java.util.Dateコンバータ
 */
public class DateConverter extends AbstractDateConverter<Date, DateConverter> implements NewValue<Date>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public Date convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Date){
			return new Date(((Date)value).getTime());
		} else if (value instanceof Calendar){
			return ((Calendar)value).getTime();
		} else if (value instanceof Number){
			return DateUtils.toDate(((Number)value).longValue());
		}
		ZonedDateTime zonedDateTime= getZonedDateTimeConverter().convertObject(value);
		return toDate(zonedDateTime);
	}
	
	private Date toDate(final ZonedDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return DateUtils.toDate(dateTime.toInstant().toEpochMilli());
	}

	public static DateConverter newInstance() {
		DateConverter dateConverter = new DateConverter();
		return dateConverter;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof DateConverter)){
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
	public Date copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Date)convertObject(obj).clone();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.NewValue#newValue()
	 */
	@Override
	public Date newValue() {
		return new Date();
	}
}
