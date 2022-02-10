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
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;
import java.time.chrono.JapaneseEra;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * java.time.JapaneseDate converter
 * 複数の日付フォーマットをサポート
 */
public class JapaneseDateConverter extends AbstractJava8DateConverter<JapaneseDate, JapaneseDateConverter> implements NewValue<JapaneseDate>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	private static JapaneseEraConverter JAPANESE_ERA_CONVERTER=new JapaneseEraConverter();
	
	@Override
	public JapaneseDate convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof JapaneseDate){
			return (JapaneseDate)value;
		} else if (value instanceof TemporalAccessor){
			if (value instanceof YearMonth){
				final YearMonth cst=YearMonth.class.cast(value);
				return JapaneseDate.from(LocalDate.of(cst.getYear(), cst.getMonthValue(), 1));
			} else if (value instanceof Year){
				final Year cst=Year.class.cast(value);
				return JapaneseDate.from(LocalDate.of(cst.getValue(), 1, 1));
			}
			return JapaneseDate.from((TemporalAccessor)value);
		} else if (value instanceof Period){
			final Period p=Period.class.cast(value);
			return JapaneseDate.from(LocalDate.of(p.getYears(), p.getMonths(), p.getDays()));
		} else if (value instanceof Calendar){
			return JapaneseDate.from(toZonedDateTime((Calendar)value).toLocalDate());
		} else if (value instanceof java.sql.Date){
			final java.sql.Date dt= java.sql.Date.class.cast(value);
			return JapaneseDate.from(dt.toLocalDate());
		} else if (value instanceof java.util.Date){
			final java.util.Date dt= java.util.Date.class.cast(value);
			return JapaneseDate.from(toZonedDateTime(dt.toInstant()).toLocalDate());
		} else if (value instanceof Number){
			return JapaneseDate.from(toZonedDateTime((Number)value).toLocalDate());
		} else if (value instanceof String){
			final String lowerVal=((String)value).toLowerCase();
			if(isCurrentText(lowerVal)){
				return JapaneseDate.now();
			} else if(lowerVal.startsWith("'")&&lowerVal.endsWith("'")){
				final String val=cast(value);
				return parseDate(val.substring(1, val.length()-1));
			}
			return parseDate((String)value);
		}
		return parseDate(value.toString());
	}

	public static JapaneseDateConverter newInstance(){
		final JapaneseDateConverter dateConverter=new JapaneseDateConverter();
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
		if (!(obj instanceof JapaneseDateConverter)){
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
	public JapaneseDate newValue() {
		return JapaneseDate.now();
	}

	private final Pattern PATTERN=Pattern.compile("(?<g>明治|明|M|大正|大|T|昭和|昭|S|平成|平|H|令和|令|R)(?<y>[1-9]?[0-9])(年|/|-)(?<m>[0-1]?[0-9])(月|/|-)(?<d>[0-3]?[0-9])(日)?");
	
	@Override
	protected JapaneseDate parseDate(final String value) {
		final JapaneseDate date=parse(value);
		if (date!=null) {
			return date;
		}
		return super.parseDate(value);
	}
	
	@Override
	protected JapaneseDate parse(final String value, final DateTimeFormatter dateTimeFormatter) {
		final Temporal temporal=parseTemporal(value, dateTimeFormatter);
		if (temporal==null){
			return null;
		}
		if (temporal instanceof JapaneseDate){
			return JapaneseDate.class.cast(temporal);
		}
		return JapaneseDate.from(toZonedDateTime(temporal).toLocalDate());
	}

	protected JapaneseDate parse(final String value) {
		final Matcher matcher=PATTERN.matcher(value);
		if (matcher.matches()) {
			final String g=matcher.group("g");
			final JapaneseEra era=getEra(g);
			final String y=matcher.group("y");
			final String m=matcher.group("m");
			final String d=matcher.group("d");
			return JapaneseDate.of(era, Integer.valueOf(y), Integer.valueOf(m), Integer.valueOf(d));
		}
		return null;
	}

	private JapaneseEra getEra(final String value) {
		return JAPANESE_ERA_CONVERTER.convertObject(value);
	}

	@Override
	protected String format(final JapaneseDate temporal, final DateTimeFormatter formatter) {
		return temporal.format(formatter);
	}

}
