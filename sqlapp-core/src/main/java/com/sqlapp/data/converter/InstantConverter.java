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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;

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
	public Instant convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Temporal){
			if (value instanceof Instant){
				return (Instant)value;
			} else if (value instanceof ChronoLocalDate){
				final Instant instant=Instant.ofEpochMilli(((ChronoLocalDate)value).toEpochDay());
				return instant;
			} else if (value instanceof LocalDateTime){
				final Instant instant=toZonedDateTime((LocalDateTime)value).toInstant();
				return instant;
			} else if (value instanceof OffsetDateTime){
				final Instant instant=((OffsetDateTime)value).toInstant();
				return instant;
			} else if (value instanceof ZonedDateTime){
				final Instant instant=((ZonedDateTime)value).toInstant();
				return instant;
			} else if (value instanceof LocalTime){
				final LocalTime localTime=LocalTime.class.cast(value);
				final LocalDateTime localDateTime=LocalDateTime.of(EPOC_DAY, localTime);
				return toZonedDateTime(localDateTime).toInstant();
			} else if (value instanceof OffsetTime){
				final OffsetTime time=OffsetTime.class.cast(value);
				final OffsetDateTime dateTime=OffsetDateTime.of(EPOC_DAY, time.toLocalTime(), time.getOffset());
				final Instant instant=dateTime.toInstant();
				return instant;
			} else if (value instanceof YearMonth){
				final YearMonth time=YearMonth.class.cast(value);
				final LocalDateTime localDateTime=LocalDateTime.of(time.getYear(), time.getMonthValue(), 1, 0, 0);
				return toZonedDateTime(localDateTime).toInstant();
			} else if (value instanceof Year){
				final Year time=Year.class.cast(value);
				final LocalDateTime localDateTime=LocalDateTime.of(time.getValue(), 1, 1, 0, 0);
				return toZonedDateTime(localDateTime).toInstant();
			}
		} else if (value instanceof java.sql.Date){
			return Instant.ofEpochMilli(((java.sql.Date)value).getTime());
		} else if (value instanceof Date){
			return ((Date)value).toInstant();
		} else if (value instanceof Calendar){
			final Calendar cal=Calendar.class.cast(value);
			return toZonedDateTime(cal).toInstant();
		} else if (value instanceof Number){
			final Instant ins= Instant.ofEpochMilli(((Number)value).longValue());
			return ins;
		}
		return parseDate(value.toString());
	}
	
	@Override
	public String convertString(final Instant value) {
		if (value == null) {
			return null;
		}
		final DateTimeFormatter format=this.getFormat();
		if (format == null) {
			return toZonedDateTime(value).toString();
		}
		return toZonedDateTime(value).format(format);
	}
	public static InstantConverter newInstance(){
		final InstantConverter dateConverter=new InstantConverter();
		return dateConverter;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
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
	protected Instant parse(final String value, final DateTimeFormatter dateTimeFormatter) {
		final Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		return toZonedDateTime(temporal).toInstant();
	}

	@Override
	protected String format(final Instant temporal, final DateTimeFormatter formatter) {
		return toZonedDateTime(temporal).format(formatter);
	}
}
