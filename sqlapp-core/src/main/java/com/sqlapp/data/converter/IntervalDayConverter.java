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

import static com.sqlapp.util.CommonUtils.*;

import java.sql.Connection;

import com.sqlapp.data.interval.Interval;
import com.sqlapp.data.interval.IntervalDay;

/**
 * IntervalDayType Converter
 * @author SATOH
 *
 */
public class IntervalDayConverter extends AbstractConverter<IntervalDay>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -1050734373259054536L;
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#convertObject(java.lang.Object, java.sql.Connection)
	 */
	@Override
	public IntervalDay convertObject(Object value, Connection conn) {
		return convertObject(value);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#convertObject(java.lang.Object)
	 */
	@Override
	public IntervalDay convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof IntervalDay){
			return ((IntervalDay)value);
		}else if (value instanceof Interval){
			return IntervalDay.toDayType((Interval)value);
		}else if (value instanceof String){
			return IntervalDay.parse((String)value);
		}
		return convert(value.toString());
	}

	private IntervalDay convert(String value){
		return IntervalDay.parse((String)value);
	}

	@Override
	public String convertString(IntervalDay value) {
		if (value==null){
			return null;
		}
		return value.toString();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof IntervalDayConverter)){
			return false;
		}
		IntervalDayConverter con=cast(obj);
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
	public IntervalDay copy(Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj).clone();
	}
}
