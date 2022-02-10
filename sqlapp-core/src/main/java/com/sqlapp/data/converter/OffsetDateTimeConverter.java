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
import java.time.ZoneId;
import java.time.ZoneOffset;
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
public class OffsetDateTimeConverter extends AbstractJava8OffsetConverter<OffsetDateTime, OffsetDateTimeConverter> implements NewValue<OffsetDateTime>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public OffsetDateTime convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof OffsetDateTime){
			return (OffsetDateTime)value;
		} else if (value instanceof Instant){
			return  toZonedDateTime((Instant)value).toOffsetDateTime();
		} else if (value instanceof ChronoLocalDate){
			ChronoLocalDate localDate= ChronoLocalDate.class.cast(value);
			Instant ins= Instant.ofEpochMilli(localDate.toEpochDay());
			return toZonedDateTime(ins).toOffsetDateTime();
		} else if (value instanceof LocalDateTime){
			return  toZonedDateTime((LocalDateTime)value).toOffsetDateTime();
		} else if (value instanceof OffsetDateTime){
			return  toZonedDateTime((OffsetDateTime)value).toOffsetDateTime();
		} else if (value instanceof ZonedDateTime){
			ZonedDateTime zonedDateTime= ZonedDateTime.class.cast(value);
			return zonedDateTime.toOffsetDateTime();
		} else if (value instanceof Calendar){
			return  toZonedDateTime((Calendar)value).toOffsetDateTime();
		} else if (value instanceof java.sql.Date){
			java.sql.Date dt= java.sql.Date.class.cast(value);
			return toZonedDateTime(Instant.ofEpochMilli(dt.getTime())).toOffsetDateTime();
		} else if (value instanceof java.util.Date){
			java.util.Date dt= java.util.Date.class.cast(value);
			return toZonedDateTime(dt.toInstant()).toOffsetDateTime();
		} else if (value instanceof Number){
			return toZonedDateTime((Number)value).toOffsetDateTime();
		} else if (value instanceof String){
			String lowerVal=((String)value).toLowerCase();
			if(isCurrentText(lowerVal)){
				return OffsetDateTime.now();
			} else if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				String val=cast(value);
				return parseDate(val.substring(1, val.length()-1));
			} else if (isNumberPattern(lowerVal)){
				Instant ins = toInstant(lowerVal);
				return toZonedDateTime(ins).toOffsetDateTime();
			} else {
				return parseDate((String) value);
			}
		}
		return parseDate(value.toString());
	}

	protected ZoneOffset getDefaultZoneOffset(){
		return ZoneId.systemDefault().getRules().getOffset(Instant.now());
	}

	@Override
	protected OffsetDateTime toUtc(OffsetDateTime dateTime) {
		if (this.isUtc()) {
			if (dateTime == null) {
				return null;
			}
			return dateTime.withOffsetSameInstant(INSTANT_ZONE_OFFSET);
		} else {
			return dateTime;
		}
	}
	
	public static OffsetDateTimeConverter newInstance(){
		OffsetDateTimeConverter dateConverter=new OffsetDateTimeConverter();
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
		if (!(obj instanceof OffsetDateTimeConverter)){
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.NewValue#newValue()
	 */
	@Override
	public OffsetDateTime newValue() {
		return OffsetDateTime.now();
	}

	@Override
	protected OffsetDateTime parse(String value, DateTimeFormatter dateTimeFormatter) {
		Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		if (temporal instanceof OffsetDateTime){
			return OffsetDateTime.class.cast(temporal);
		}
		return toZonedDateTime(temporal).toOffsetDateTime();
	}

	@Override
	protected String format(OffsetDateTime temporal, DateTimeFormatter formatter) {
		return temporal.format(formatter);
	}

}
