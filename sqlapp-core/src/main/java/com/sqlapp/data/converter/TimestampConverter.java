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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.regex.Pattern;

import com.sqlapp.util.DateUtils;
/**
 * TimestampType Converterー
 * @author SATOH
 *
 */
public class TimestampConverter extends AbstractDateConverter<Timestamp, TimestampConverter> implements NewValue<Timestamp>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4338676657516409581L;

	private static final Pattern TIMESTAMP_PATTERN=Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]+");

	@Override
	public Timestamp convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Timestamp){
			return Timestamp.class.cast(value);
		}else if (value instanceof java.util.Date){
			return DateUtils.toTimestamp((java.util.Date)value);
		}else if (value instanceof Calendar){
			return DateUtils.toTimestamp((Calendar)value);
		} else if (value instanceof Instant){
			return Timestamp.from((Instant)value);
		}else if (value instanceof Long){
			return DateUtils.toTimestamp(((Long)value).longValue());
		}else if (value instanceof String){
			String val=(String)value;
			if (TIMESTAMP_PATTERN.matcher(val).matches()){
				return Timestamp.valueOf(val);
			}
		}
		ZonedDateTime zonedDateTime= getZonedDateTimeConverter().convertObject(value);
		return toTimestamp(zonedDateTime);
	}
	
	/**
	 * DateTime型からTimestamp型に変換します
	 * 
	 * @param dateTime
	 * @return カレンダー型
	 */
	private Timestamp toTimestamp(final ZonedDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		Timestamp timestamp=new Timestamp(dateTime.toInstant().toEpochMilli());
		timestamp.setNanos(dateTime.getNano());
		return timestamp;
	}
	
	@Override
	public String convertString(Timestamp value) {
		if (value == null) {
			return null;
		}
		String ret= super.convertString(value);
		if (ret.endsWith(".000000000")){
			return ret.substring(0, ret.length()-10);
		}
		return ret;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof TimestampConverter)){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public java.sql.Timestamp copy(Object obj){
		if (obj==null){
			return null;
		}
		return (java.sql.Timestamp)convertObject(obj).clone();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.NewValue#newValue()
	 */
	@Override
	public Timestamp newValue() {
		return DateUtils.toTimestamp(System.currentTimeMillis());
	}
}
