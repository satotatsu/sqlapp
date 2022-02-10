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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.Calendar;

import com.sqlapp.util.DateUtils;
/**
 * SQL日付Type Converterー
 * @author SATOH
 *
 */
public class SqlDateConverter extends AbstractDateConverter<java.sql.Date, SqlDateConverter>{

	/**serialVersionUID
	 * 
	 */
	private static final long serialVersionUID = 7689922259052268965L;
	
	@Override
	public java.sql.Date convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof java.sql.Date){
			return new java.sql.Date(((java.sql.Date)value).getTime());
		}else if (value instanceof java.util.Date){
			return DateUtils.toSqlDate((java.util.Date)value);
		} else if (value instanceof LocalDate){
			return java.sql.Date.valueOf((LocalDate)value);
		}else if (value instanceof ChronoLocalDate){
			return DateUtils.toSqlDate(((ChronoLocalDate)value).toEpochDay());
		}else if (value instanceof LocalDateTime){
			return DateUtils.toSqlDate(((LocalDateTime)value).toLocalDate().toEpochDay());
		}else if (value instanceof OffsetDateTime){
			final OffsetDateTime dateTime=OffsetDateTime.class.cast(value);
			return DateUtils.toSqlDate(dateTime.toLocalDate().toEpochDay());
		}else if (value instanceof ZonedDateTime){
			final ZonedDateTime dateTime=ZonedDateTime.class.cast(value);
			return DateUtils.toSqlDate(dateTime.toLocalDate().toEpochDay());
		}else if (value instanceof Calendar){
			return DateUtils.toSqlDate((Calendar)value);
		}else if (value instanceof Long){
			return DateUtils.toSqlDate(((Long)value).longValue());
		}
		final ZonedDateTime zonedDateTime= getZonedDateTimeConverter().convertObject(value);
		return toDate(zonedDateTime);
	}
	
	/**
	 * DateTime型からカレンダー型に変換
	 * 
	 * @param dateTime
	 * @return カレンダー型
	 */
	public java.sql.Date toDate(final ZonedDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return DateUtils.toSqlDate(dateTime.toInstant().toEpochMilli());
	}

	public static SqlDateConverter newInstance() {
		final ZonedDateTimeConverter dateTimeConverter = ZonedDateTimeConverter.newInstance();
		final SqlDateConverter dateConverter = new SqlDateConverter();
		dateConverter.setZonedDateTimeConverter(dateTimeConverter);
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
		if (!(obj instanceof SqlDateConverter)){
			return false;
		}
		final SqlDateConverter con=cast(obj);
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
	public java.sql.Date copy(final Object obj){
		if (obj==null){
			return null;
		}
		return (java.sql.Date)convertObject(obj).clone();
	}
}
