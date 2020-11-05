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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;
import java.time.Instant;

import static com.sqlapp.util.CommonUtils.*;

/**
 * java.util.Dateコンバータ
 * 複数の日付フォーマットをサポート
 */
public class InstantConverter extends AbstractJava8DateConverter<Instant, InstantConverter> implements NewValue<Instant>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public Instant convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Instant){
			return (Instant)value;
		} else if (value instanceof java.sql.Date){
			return Instant.ofEpochMilli(((java.sql.Date)value).getTime());
		} else if (value instanceof Date){
			return ((Date)value).toInstant();
		} else if (value instanceof ChronoLocalDate){
			Instant instant=Instant.ofEpochMilli(((ChronoLocalDate)value).toEpochDay());
			return instant;
		} else if (value instanceof LocalDateTime){
			Instant instant=toZonedDateTime((LocalDateTime)value).toInstant();
			return instant;
		} else if (value instanceof OffsetDateTime){
			Instant instant=((OffsetDateTime)value).toInstant();
			return instant;
		} else if (value instanceof ZonedDateTime){
			Instant instant=((ZonedDateTime)value).toInstant();
			return instant;
		} else if (value instanceof LocalTime){
			LocalTime localTime=LocalTime.class.cast(value);
			LocalDateTime localDateTime=LocalDateTime.of(EPOC_DAY, localTime);
			return toZonedDateTime(localDateTime).toInstant();
		} else if (value instanceof OffsetTime){
			OffsetTime time=OffsetTime.class.cast(value);
			OffsetDateTime dateTime=OffsetDateTime.of(EPOC_DAY, time.toLocalTime(), time.getOffset());
			Instant instant=dateTime.toInstant();
			return instant;
		} else if (value instanceof Calendar){
			Calendar cal=Calendar.class.cast(value);
			return toZonedDateTime(cal).toInstant();
		} else if (value instanceof Number){
			Instant ins= Instant.ofEpochMilli(((Number)value).longValue());
			return ins;
		}
		return parseDate(value.toString());
	}
	
	@Override
	public String convertString(Instant value) {
		if (value == null) {
			return null;
		}
		DateTimeFormatter format=this.getFormat();
		if (format == null) {
			return toZonedDateTime(value).toString();
		}
		return toZonedDateTime(value).format(format);
	}
	public static InstantConverter newInstance(){
		InstantConverter dateConverter=new InstantConverter();
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
		if (!(obj instanceof InstantConverter)){
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
	public Instant newValue() {
		return Instant.now();
	}

	@Override
	protected Instant parse(String value, DateTimeFormatter dateTimeFormatter) {
		Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		return toZonedDateTime(temporal).toInstant();
	}

	@Override
	protected String format(Instant temporal, DateTimeFormatter formatter) {
		return toZonedDateTime(temporal).format(formatter);
	}
}
