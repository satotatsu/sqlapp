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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.Calendar;

/**
 * java.time.Year converter
 * 複数の日付フォーマットをサポート
 */
public class YearConverter extends AbstractJava8DateConverter<Year, YearConverter> implements NewValue<Year>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public Year convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Year){
			return (Year)value;
		} else if (value instanceof YearMonth){
			final YearMonth cst=YearMonth.class.cast(value);
			return toYear(cst);
		} else if (value instanceof Instant){
			final Instant cst=Instant.class.cast(value);
			return toYear(toZonedDateTime(cst));
		} else if (value instanceof LocalDateTime){
			final LocalDateTime cst=LocalDateTime.class.cast(value);
			return toYear(toZonedDateTime(cst));
		} else if (value instanceof ChronoLocalDate){
			final ChronoLocalDate cst=ChronoLocalDate.class.cast(value);
			return toYear(toZonedDateTime(cst));
		} else if (value instanceof OffsetDateTime){
			final OffsetDateTime cst=OffsetDateTime.class.cast(value);
			return toYear(toZonedDateTime(cst));
		} else if (value instanceof ZonedDateTime){
			final ZonedDateTime cst=ZonedDateTime.class.cast(value);
			return toYear(toZonedDateTime(cst));
		} else if (value instanceof Calendar){
			final Calendar cst=Calendar.class.cast(value);
			return toYear(toZonedDateTime(cst));
		} else if (value instanceof java.sql.Date){
			final java.sql.Date cst= java.sql.Date.class.cast(value);
			return toYear(toZonedDateTime(cst.getTime()));
		} else if (value instanceof java.util.Date){
			final java.util.Date cst= java.util.Date.class.cast(value);
			return toYear(toZonedDateTime(cst.getTime()));
		} else if (value instanceof Number){
			return toYear(toZonedDateTime((Number)value));
		} else if (value instanceof String){
			final String lowerVal=((String)value).toLowerCase();
			if(isCurrentText(lowerVal)){
				return Year.now();
			} else if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				final String val=cast(value);
				return parseDate(val.substring(1, val.length()-1));
			}
			return parseDate((String)value);
		}
		return parseDate(value.toString());
	}

	public static YearConverter newInstance(){
		final YearConverter dateConverter=new YearConverter();
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
		if (!(obj instanceof YearConverter)){
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
	public Year newValue() {
		return Year.now();
	}

	@Override
	protected Year parse(final String value, final DateTimeFormatter dateTimeFormatter) {
		final Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		if (temporal instanceof Year){
			return Year.class.cast(temporal);
		}
		return Year.of(temporal.get(ChronoField.YEAR));
	}

	private Year toYear(final ZonedDateTime zd) {
		return Year.of(zd.getYear());
	}

	private Year toYear(final YearMonth zd) {
		return Year.of(zd.getYear());
	}

	@Override
	protected String format(final Year temporal, final DateTimeFormatter formatter) {
		return temporal.format(formatter);
	}

}
