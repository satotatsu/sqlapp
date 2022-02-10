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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Calendar;

import static com.sqlapp.util.CommonUtils.*;

/**
 * java.time.LocalDateTime converter
 * 複数の日付フォーマットをサポート
 */
public class LocalDateTimeConverter extends AbstractJava8DateConverter<LocalDateTime, LocalDateTimeConverter> implements NewValue<LocalDateTime>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public LocalDateTime convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof LocalDateTime){
			return (LocalDateTime)value;
		} else if (value instanceof Instant){
			Instant ins=Instant.class.cast(value);
			return toZonedDateTime(ins).toLocalDateTime();
		} else if (value instanceof ChronoLocalDate){
			return toZonedDateTime((ChronoLocalDate)value).toLocalDateTime();
		} else if (value instanceof OffsetDateTime){
			return ((OffsetDateTime)value).toLocalDateTime();
		} else if (value instanceof ZonedDateTime){
			return ((ZonedDateTime)value).toLocalDateTime();
		} else if (value instanceof Calendar){
			Calendar cal=Calendar.class.cast(value);
			return toZonedDateTime(cal).toLocalDateTime();
		} else if (value instanceof java.sql.Date){
			java.sql.Date dt= java.sql.Date.class.cast(value);
			return toZonedDateTime(Instant.ofEpochMilli(dt.getTime())).toLocalDateTime();
		} else if (value instanceof java.util.Date){
			java.util.Date dt= java.util.Date.class.cast(value);
			return toZonedDateTime(dt.toInstant()).toLocalDateTime();
		} else if (value instanceof Number){
			return toZonedDateTime((Number)value).toLocalDateTime();
		} else if (value instanceof String){
			String lowerVal=((String)value).toLowerCase();
			if(isCurrentText(lowerVal)){
				return LocalDateTime.now();
			} else if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				String val=cast(value);
				return parseDate(val.substring(1, val.length()-1));
			} else if (isNumberPattern(lowerVal)){
				Instant ins = toInstant(lowerVal);
				return toZonedDateTime(ins).toLocalDateTime();
			}
			return parseDate((String)value);
		}
		return parseDate(value.toString());
	}

	public static LocalDateTimeConverter newInstance(){
		LocalDateTimeConverter dateConverter=new LocalDateTimeConverter();
		return dateConverter;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof LocalDateTimeConverter)){
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.NewValue#newValue()
	 */
	@Override
	public LocalDateTime newValue() {
		return LocalDateTime.now();
	}

	@Override
	protected LocalDateTime parse(String value, DateTimeFormatter dateTimeFormatter) {
		Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		if (temporal instanceof LocalDateTime){
			return LocalDateTime.class.cast(temporal);
		}
		return toZonedDateTime(temporal).toLocalDateTime();
	}

	@Override
	protected String format(LocalDateTime temporal, DateTimeFormatter formatter) {
		return temporal.format(formatter);
	}
}
