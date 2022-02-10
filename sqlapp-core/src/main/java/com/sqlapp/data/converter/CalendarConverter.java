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

import com.sqlapp.util.DateUtils;
import static com.sqlapp.util.CommonUtils.*;

import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * カレンダーType Converterー
 * @author SATOH
 *
 */
public class CalendarConverter extends AbstractDateConverter<Calendar, CalendarConverter> implements NewValue<Calendar>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2011988656316982455L;
	
	@Override
	public Calendar convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Calendar){
			return (Calendar)value;
		}else if (value instanceof java.util.Date){
			return DateUtils.toCalendar((java.util.Date)value);
		}else if (value instanceof Number){
			return DateUtils.toCalendar(((Number)value).longValue());
		}
		ZonedDateTime zonedDateTime= getZonedDateTimeConverter().convertObject(value);
		return toCalender(zonedDateTime);
	}

	public static CalendarConverter newInstance(Object... formats) {
		ZonedDateTimeConverter dateTimeConverter = newZonedDateTimeConverter(formats);
		CalendarConverter dateConverter = new CalendarConverter();
		dateConverter.setZonedDateTimeConverter(dateTimeConverter);
		return dateConverter;
	}
	
	private Calendar toCalender(ZonedDateTime zonedDateTime){
		Calendar cal= GregorianCalendar.from(zonedDateTime);
		return cal;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof CalendarConverter)){
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
	public Calendar copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Calendar)convertObject(obj).clone();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.NewValue#newValue()
	 */
	@Override
	public Calendar newValue() {
		return Calendar.getInstance();
	}
}
