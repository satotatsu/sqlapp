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
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Calendar;

/**
 * java.time.LocalDateTime converter
 * 複数の日付フォーマットをサポート
 */
public class LocalTimeConverter extends AbstractJava8DateConverter<LocalTime, LocalTimeConverter> implements NewValue<LocalTime>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public LocalTime convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof LocalTime){
			return (LocalTime)value;
		} else if (value instanceof Instant){
			final Instant ins= Instant.class.cast(value);
			return toZonedDateTime(ins).toLocalTime();
		} else if (value instanceof ChronoLocalDate){
			return EPOC_TIME;
		} else if (value instanceof Time){
			final Time cst=Time.class.cast(value);
			return cst.toLocalTime();
		} else if (value instanceof OffsetDateTime){
			return ((OffsetDateTime)value).toLocalTime();
		} else if (value instanceof ZonedDateTime){
			return ((ZonedDateTime)value).toLocalTime();
		} else if (value instanceof OffsetTime){
			return ((OffsetTime)value).toLocalTime();
		} else if (value instanceof Calendar){
			final Calendar cal=Calendar.class.cast(value);
			return toZonedDateTime(cal).toLocalTime();
		} else if (value instanceof java.sql.Date){
			final java.sql.Date dt= java.sql.Date.class.cast(value);
			return toZonedDateTime(Instant.ofEpochMilli(dt.getTime())).toLocalTime();
		} else if (value instanceof java.util.Date){
			final java.util.Date dt= java.util.Date.class.cast(value);
			return toZonedDateTime(dt.toInstant()).toLocalTime();
		} else if (value instanceof Number){
			return toZonedDateTime((Number)value).toLocalTime();
		} else if (value instanceof String){
			final String lowerVal=((String)value).toLowerCase();
			if(isCurrentText(lowerVal)){
				return LocalTime.now();
			} else if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				final String val=cast(value);
				return parseDate(val.substring(1, val.length()-1));
			}
			return parseDate((String)value);
		}
		return parseDate(value.toString());
	}

	public static LocalTimeConverter newInstance(){
		final LocalTimeConverter dateConverter=new LocalTimeConverter();
		return dateConverter;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof LocalTimeConverter)){
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.NewValue#newValue()
	 */
	@Override
	public LocalTime newValue() {
		return LocalTime.now();
	}

	@Override
	protected LocalTime parse(final String value, final DateTimeFormatter dateTimeFormatter) {
		final Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		if (temporal instanceof LocalTime){
			return LocalTime.class.cast(temporal);
		}
		return toZonedDateTime(temporal).toLocalTime();
	}

	@Override
	protected String format(final LocalTime temporal, final DateTimeFormatter formatter) {
		return temporal.format(formatter);
	}
}
