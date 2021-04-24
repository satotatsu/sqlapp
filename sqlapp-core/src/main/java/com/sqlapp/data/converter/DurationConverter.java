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

import java.time.Duration;
import java.time.LocalTime;
import java.time.chrono.ChronoPeriod;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Calendar;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.util.DateUtils;

/**
 * java.time.Duration converter
 * 複数の日付フォーマットをサポート
 */
public class DurationConverter extends AbstractConverter<Duration> implements NewValue<Duration>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	private static LocalTimeConverter LOCAL_TIME_CONVERTER=new LocalTimeConverter();
	
	@Override
	public Duration convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Duration){
			return (Duration)value;
		} else if (value instanceof ChronoPeriod){
			final ChronoPeriod cst=ChronoPeriod.class.cast(value);
			return Duration.from(cst);
		} else if (value instanceof Temporal){
			final LocalTime localDate=LOCAL_TIME_CONVERTER.convertObject(value);
			return Duration.ofSeconds(localDate.getSecond(), localDate.getNano());
		} else if (value instanceof Calendar){
			final Calendar cst=Calendar.class.cast(value);
			return Duration.ofSeconds(toSecond(cst), toNano(cst));
		} else if (value instanceof java.util.Date){
			final java.sql.Date dt= java.sql.Date.class.cast(value);
			final Calendar cst=DateUtils.toCalendar(dt);
			return Duration.ofSeconds(toSecond(cst), toNano(cst));
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
	
	private long toNano(final Calendar cst) {
		return cst.get(Calendar.MILLISECOND)*1000000;
	}

	private long toSecond(final Calendar cst) {
		final long val=cst.get(Calendar.SECOND)+cst.get(Calendar.MINUTE)*60+cst.get(Calendar.HOUR_OF_DAY)*3600;
		return val;
	}

	private long toSecond(final long hour, final long minute, final long second) {
		final long val=second+minute*60+hour*3600;
		return val;
	}

	public static DurationConverter newInstance(){
		final DurationConverter dateConverter=new DurationConverter();
		return dateConverter;
	}
	
	private Duration parse(final String text) {
		try {
			return Duration.parse(text);
		} catch(final DateTimeParseException e) {
			final Interval interval=Interval.parse(text);
			return Duration.ofSeconds(toSecond(interval.getHours(), interval.getMinutes(), interval.getSeconds()), interval.getNanos());
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
		if (!(obj instanceof DurationConverter)){
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
	public Duration newValue() {
		return Duration.ofNanos(0);
	}

	@Override
	public Duration copy(final Object obj) {
		if (obj==null){
			return null;
		}
		return (Duration)obj;
	}
}
