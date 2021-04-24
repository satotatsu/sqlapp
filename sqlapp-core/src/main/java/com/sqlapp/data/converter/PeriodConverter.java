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

import java.time.LocalDate;
import java.time.Period;
import java.time.chrono.ChronoPeriod;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Calendar;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.util.DateUtils;

/**
 * java.time.Period converter
 * 複数の日付フォーマットをサポート
 */
public class PeriodConverter extends AbstractConverter<Period> implements NewValue<Period>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	private static LocalDateConverter LOCAL_DATE_CONVERTER=new LocalDateConverter();
	
	@Override
	public Period convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Period){
			return (Period)value;
		} else if (value instanceof ChronoPeriod){
			final ChronoPeriod cst=ChronoPeriod.class.cast(value);
			return Period.from(cst);
		} else if (value instanceof Temporal){
			final LocalDate localDate=LOCAL_DATE_CONVERTER.convertObject(value);
			return Period.of(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
		} else if (value instanceof Calendar){
			final Calendar cst=Calendar.class.cast(value);
			return Period.of(cst.get(Calendar.YEAR), cst.get(Calendar.MONTH)+1, cst.get(Calendar.DAY_OF_MONTH));
		} else if (value instanceof java.util.Date){
			final java.sql.Date dt= java.sql.Date.class.cast(value);
			final Calendar cst=DateUtils.toCalendar(dt);
			return Period.of(cst.get(Calendar.YEAR), cst.get(Calendar.MONTH)+1, cst.get(Calendar.DAY_OF_MONTH));
		} else if (value instanceof String){
			final String lowerVal=((String)value).toLowerCase();
			if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				final String val=cast(value);
				return parse(val.substring(1, val.length()-1));
			}
			return parse((String)value);
		}
		return parse(value.toString());
	}

	public static PeriodConverter newInstance(){
		final PeriodConverter dateConverter=new PeriodConverter();
		return dateConverter;
	}
	
	private Period parse(final String text) {
		try {
			return Period.parse(text);
		} catch(final DateTimeParseException e) {
			final Interval interval=Interval.parse(text);
			return Period.of(interval.getYears(), interval.getMonths(), interval.getDays());
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof PeriodConverter)){
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
	public Period newValue() {
		return Period.of(0, 0, 0);
	}

	@Override
	public Period copy(final Object obj) {
		if (obj==null){
			return null;
		}
		return (Period)obj;
	}
}
