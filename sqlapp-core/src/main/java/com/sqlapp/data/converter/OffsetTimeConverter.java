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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Calendar;

import static com.sqlapp.util.CommonUtils.*;

/**
 * java.time.OffsetTime converter
 * 複数の日付フォーマットをサポート
 */
public class OffsetTimeConverter extends AbstractJava8OffsetConverter<OffsetTime, OffsetTimeConverter> implements NewValue<OffsetTime>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public OffsetTime convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof OffsetTime){
			return (OffsetTime)value;
		} else if (value instanceof OffsetDateTime){
			return ((OffsetDateTime)value).toOffsetTime();
		} else if (value instanceof Instant){
			return  toOffsetTime(toZonedDateTime((Instant)value));
		} else if (value instanceof ChronoLocalDate){
			return  toOffsetTime(toZonedDateTime((ChronoLocalDate)value));
		} else if (value instanceof LocalDateTime){
			return  toOffsetTime(toZonedDateTime((LocalDateTime)value));
		} else if (value instanceof OffsetDateTime){
			return  toOffsetTime(toZonedDateTime((OffsetDateTime)value));
		} else if (value instanceof ZonedDateTime){
			return toOffsetTime((ZonedDateTime)value);
		} else if (value instanceof Calendar){
			return  toOffsetTime(toZonedDateTime((Calendar)value));
		} else if (value instanceof java.sql.Date){
			java.sql.Date dt= java.sql.Date.class.cast(value);
			return toOffsetTime(toZonedDateTime(Instant.ofEpochMilli(dt.getTime())));
		} else if (value instanceof java.util.Date){
			java.util.Date dt= java.util.Date.class.cast(value);
			return toOffsetTime(toZonedDateTime(dt.toInstant()));
		} else if (value instanceof Number){
			return toOffsetTime(toZonedDateTime((Number)value));
		} else if (value instanceof String){
			String lowerVal=((String)value).toLowerCase();
			if(isCurrentText(lowerVal)){
				return OffsetTime.now();
			} else if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				String val=cast(value);
				return parseDate(val.substring(1, val.length()-1));
			} else if (isNumberPattern(lowerVal)){
				Instant ins = toInstant(lowerVal);
				return  toOffsetTime(toZonedDateTime(ins));
			} else {
				return parseDate((String) value);
			}
		}
		return parseDate(value.toString());
	}
	
	@Override
	protected OffsetTime toUtc(OffsetTime dateTime) {
		if (this.isUtc()) {
			if (dateTime == null) {
				return null;
			}
			return dateTime.withOffsetSameInstant(INSTANT_ZONE_OFFSET);
		} else {
			return dateTime;
		}
	}
	
	public static OffsetTimeConverter newInstance(){
		OffsetTimeConverter dateConverter=new OffsetTimeConverter();
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
		if (!(obj instanceof OffsetTimeConverter)){
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
	 * @see com.sqlapp.data.converter.NewValue#newValue()
	 */
	@Override
	public OffsetTime newValue() {
		return OffsetTime.now();
	}

	@Override
	protected OffsetTime parse(String value, DateTimeFormatter dateTimeFormatter) {
		Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		if (temporal instanceof OffsetTime){
			return OffsetTime.class.cast(temporal);
		}
		return toOffsetTime(toZonedDateTime(temporal));
	}

	@Override
	protected String format(OffsetTime temporal, DateTimeFormatter formatter) {
		return temporal.format(formatter);
	}

}
