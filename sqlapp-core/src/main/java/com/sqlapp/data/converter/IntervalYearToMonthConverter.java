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
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.time.Year;
import java.time.YearMonth;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.data.interval.IntervalYearToMonth;

/**
 * IntervalYearToMonthType Converter
 * @author SATOH
 *
 */
public class IntervalYearToMonthConverter extends AbstractConverter<IntervalYearToMonth>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -973837481680423936L;
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#convertObject(java.lang.Object)
	 */
	@Override
	public IntervalYearToMonth convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof IntervalYearToMonth){
			return ((IntervalYearToMonth)value);
		}else if (value instanceof Interval){
			return IntervalYearToMonth.toYearToMonthType(((Interval)value));
		}else if (value instanceof Year){
			return new IntervalYearToMonth(((Year)value).getValue(), 0);
		}else if (value instanceof YearMonth){
			final YearMonth cst=YearMonth.class.cast(value);
			return new IntervalYearToMonth(cst.getYear(), cst.getMonthValue());
		}else if (value instanceof String){
			return IntervalYearToMonth.parse((String)value);
		}
		return convert(value.toString());
	}

	private IntervalYearToMonth convert(final String value){
		return IntervalYearToMonth.parse(value);
	}

	@Override
	public String convertString(final IntervalYearToMonth value) {
		if (value==null){
			return null;
		}
		return value.toString();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (obj==this){
			return true;
		}
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof IntervalYearToMonthConverter)){
			return false;
		}
		final IntervalYearToMonthConverter con=cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())){
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
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public IntervalYearToMonth copy(final Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj).clone();
	}
}
