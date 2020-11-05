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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.Calendar;

/**
 * 時刻Type Converterー
 * @author SATOH
 *
 */
public class TimeConverter extends AbstractDateConverter<java.sql.Time, TimeConverter> implements NewValue<java.sql.Time>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 8032929431592968750L;

	@Override
	public java.sql.Time convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof java.sql.Time){
			return java.sql.Time.class.cast(value);
		}else if (value instanceof java.util.Date){
			return DateUtils.toTime((java.util.Date)value);
		} else if (value instanceof Instant){
			return DateUtils.toTime(java.sql.Time.from((Instant)value));
		} else if (value instanceof ChronoLocalDate){
			return DateUtils.toTime(java.sql.Time.from(Instant.EPOCH));
		} else if (value instanceof LocalTime){
			LocalTime localTime= LocalTime.class.cast(value);
			return java.sql.Time.valueOf(localTime);
		} else if (value instanceof LocalDateTime){
			LocalDateTime localDateTime= LocalDateTime.class.cast(value);
			return java.sql.Time.valueOf(localDateTime.toLocalTime());
		} else if (value instanceof OffsetDateTime){
			LocalTime localTime= ((OffsetDateTime)value).toLocalTime();
			return java.sql.Time.valueOf(localTime);
		} else if (value instanceof ZonedDateTime){
			LocalTime localTime= ((ZonedDateTime)value).toLocalTime();
			return java.sql.Time.valueOf(localTime);
		}else if (value instanceof Calendar){
			return DateUtils.toTime((Calendar)value);
		}else if (value instanceof Long){
			return DateUtils.toTime(((Long)value).longValue());
		}
		ZonedDateTime zonedDateTime= getZonedDateTimeConverter().convertObject(value);
		return toTime(zonedDateTime);
	}
	
	/**
	 * ZonedDateTime型からTime型に変換します
	 * 
	 * @param dateTime
	 * @return カレンダー型
	 */
	private java.sql.Time toTime(final ZonedDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		java.sql.Time time=new java.sql.Time(dateTime.withZoneSameInstant(AbstractJava8DateConverter.INSTANT_ZONE_ID)
				.withYear(1970).withMonth(1).withDayOfMonth(1).toInstant().toEpochMilli());
		return time;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof TimeConverter)){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public java.sql.Time copy(Object obj){
		if (obj==null){
			return null;
		}
		return (java.sql.Time)convertObject(obj).clone();
	}

	@Override
	public java.sql.Time newValue() {
		return DateUtils.toTime(System.currentTimeMillis());
	}
}
