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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Calendar;

/**
 * java.time.LocalDate converter
 * 複数の日付フォーマットをサポート
 */
public class LocalDateConverter extends AbstractJava8DateConverter<LocalDate, LocalDateConverter> implements NewValue<LocalDate>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	@Override
	public LocalDate convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof LocalDate){
			return (LocalDate)value;
		} else if (value instanceof Temporal){
			if (value instanceof Instant){
				final Instant ins=Instant.class.cast(value);
				return toZonedDateTime(ins).toLocalDate();
			} else if (value instanceof LocalDateTime){
				return ((LocalDateTime)value).toLocalDate();
			} else if (value instanceof ChronoLocalDate){
				return toZonedDateTime((ChronoLocalDate)value).toLocalDate();
			} else if (value instanceof OffsetDateTime){
				return ((OffsetDateTime)value).toLocalDate();
			} else if (value instanceof ZonedDateTime){
				return ((ZonedDateTime)value).toLocalDate();
			} else if (value instanceof YearMonth){
				final YearMonth cst=YearMonth.class.cast(value);
				return LocalDate.of(cst.getYear(), cst.getMonthValue(), 1);
			} else if (value instanceof Year){
				final Year cst=Year.class.cast(value);
				return LocalDate.of(cst.getValue(), 1, 1);
			}
		} else if (value instanceof Period){
			final Period p=Period.class.cast(value);
			return LocalDate.of(p.getYears(), p.getMonths(), p.getDays());
		} else if (value instanceof Calendar){
			return toZonedDateTime((Calendar)value).toLocalDate();
		} else if (value instanceof java.sql.Date){
			final java.sql.Date dt= java.sql.Date.class.cast(value);
			return dt.toLocalDate();
		} else if (value instanceof java.util.Date){
			final java.util.Date dt= java.util.Date.class.cast(value);
			return toZonedDateTime(dt.toInstant()).toLocalDate();
		} else if (value instanceof Number){
			return toZonedDateTime((Number)value).toLocalDate();
		} else if (value instanceof String){
			final String lowerVal=((String)value).toLowerCase();
			if(isCurrentText(lowerVal)){
				return LocalDate.now();
			} else if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				final String val=cast(value);
				return parseDate(val.substring(1, val.length()-1));
			}
			return parseDate((String)value);
		}
		return parseDate(value.toString());
	}

	public static LocalDateConverter newInstance(){
		final LocalDateConverter dateConverter=new LocalDateConverter();
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
		if (!(obj instanceof LocalDateConverter)){
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
	public LocalDate newValue() {
		return LocalDate.now();
	}

	@Override
	protected LocalDate parse(final String value, final DateTimeFormatter dateTimeFormatter) {
		final Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		if (temporal instanceof LocalDate){
			return LocalDate.class.cast(temporal);
		}
		return toZonedDateTime(temporal).toLocalDate();
	}

	@Override
	protected String format(final LocalDate temporal, final DateTimeFormatter formatter) {
		return temporal.format(formatter);
	}

}
