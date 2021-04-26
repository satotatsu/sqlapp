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

import java.time.LocalDate;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;
import java.time.chrono.JapaneseEra;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;

/**
 * java.time.JapaneseEra converter
 * 複数の日付フォーマットをサポート
 */
public class JapaneseEraConverter extends AbstractConverter<JapaneseEra> implements NewValue<JapaneseEra>,Cloneable{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1212274814940098554L;

	private static final LocalDateConverter LOCAL_DATE_CONVERTER=new LocalDateConverter();
	
	@Override
	public JapaneseEra convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof JapaneseEra){
			return (JapaneseEra)value;
		} else if (value instanceof TemporalAccessor){
			if (value instanceof YearMonth){
				final YearMonth cst=YearMonth.class.cast(value);
				return JapaneseDate.from(LocalDate.of(cst.getYear(), cst.getMonthValue(), 1)).getEra();
			} else if (value instanceof Year){
				final Year cst=Year.class.cast(value);
				return JapaneseDate.from(LocalDate.of(cst.getValue(), 1, 1)).getEra();
			}
			return JapaneseDate.from((TemporalAccessor)value).getEra();
		} else if (value instanceof Period){
			final Period p=Period.class.cast(value);
			return JapaneseDate.from(LocalDate.of(p.getYears(), p.getMonths(), p.getDays())).getEra();
		} else if (value instanceof Calendar){
			return JapaneseDate.from(LOCAL_DATE_CONVERTER.convertObject(value)).getEra();
		} else if (value instanceof java.sql.Date){
			return JapaneseDate.from(LOCAL_DATE_CONVERTER.convertObject(value)).getEra();
		} else if (value instanceof java.util.Date){
			return JapaneseDate.from(LOCAL_DATE_CONVERTER.convertObject(value)).getEra();
		} else if (value instanceof Number){
			return JapaneseDate.from(LOCAL_DATE_CONVERTER.convertObject(value)).getEra();
		} else if (value instanceof String){
			return parse((String)value);
		}
		return parse(value.toString());
	}

	public static JapaneseEraConverter newInstance(){
		final JapaneseEraConverter dateConverter=new JapaneseEraConverter();
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
		if (!(obj instanceof JapaneseEraConverter)){
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
	public JapaneseEra newValue() {
		return JapaneseDate.now().getEra();
	}
	
	protected JapaneseEra parse(final String value) {
		if ("明治".equals(value)||"明".equals(value)||"M".equalsIgnoreCase(value)) {
			return JapaneseEra.MEIJI;
		}
		if ("大正".equals(value)||"大".equals(value)||"T".equalsIgnoreCase(value)) {
			return JapaneseEra.TAISHO;
		}
		if ("昭和".equals(value)||"昭".equals(value)||"S".equalsIgnoreCase(value)) {
			return JapaneseEra.SHOWA;
		}
		if ("平成".equals(value)||"平".equals(value)||"H".equalsIgnoreCase(value)) {
			return JapaneseEra.HEISEI;
		}
		if ("令和".equals(value)||"令".equals(value)||"R".equalsIgnoreCase(value)) {
			return JapaneseEra.REIWA;
		}
		return JapaneseEra.valueOf(value);
	}

	@Override
	public JapaneseEra copy(final Object value) {
		if (value==null){
			return null;
		}
		return (JapaneseEra)value;
	}
}
