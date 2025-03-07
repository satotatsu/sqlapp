/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.schemas;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

import com.sqlapp.data.schemas.properties.StatisticsProperty;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.util.CommonUtils;

public enum Statistics {

	ROWS(){
	},
	DATA_LENGTH_MBYTES(){
		@SuppressWarnings("unchecked")
		@Override
		public <T> T getValue(StatisticsProperty<?> obj){
			Long value=DATA_LENGTH.getValue(obj);
			if (value==null) {
				return null;
			}
			Long ret= value.longValue()/CommonUtils.LEN_1MB;
			return (T)ret;
		}
	},
	DATA_LENGTH_GBYTES(){
		@SuppressWarnings("unchecked")
		@Override
		public <T> T getValue(StatisticsProperty<?> obj){
			Long value=DATA_LENGTH.getValue(obj);
			if (value==null) {
				return null;
			}
			Long ret= value.longValue()/CommonUtils.LEN_1GB;
			return (T)ret;
		}
	}
	,AVG_ROW_LENGTH(){
	}
	,AVG_COMPRESSED_ROW_LENGTH(){
	}
	,AVG_ROW_COMPRESSION_RAITO(){
		@Override
		protected Class<?> getType() {
			return Float.class;
		}
	}
	,ROW_COMPRESSED(){
		@Override
		protected Class<?> getType() {
			return Float.class;
		}
	}
	,DATA_LENGTH(){
		@SuppressWarnings("unchecked")
		@Override
		public <T> T getValue(StatisticsProperty<?> obj){
			Long ret=getValueInternal(obj);
			if (ret!=null) {
				return (T)ret;
			}
			Long value1=ROWS.getValue(obj);
			Long value2=AVG_ROW_LENGTH.getValue(obj);
			if (value1!=null&&value2!=null) {
				ret=value1.longValue()*value2.longValue();
				return (T)ret;
			}
			return null;
		}
	}
	,MAX_DATA_LENGTH(){
	}
	,INDEX_LENGTH(){
	}
	,LOB_LENGTH(){
	}
	;

	public <T> T getValue(final StatisticsProperty<?> obj){
		return getValueInternal(obj);
	}

	public String getFormatedValue(final StatisticsProperty<?> obj, final Locale locale){
		final Object value= getValueInternal(obj);
		if (value==null) {
			return "";
		}
		if (value instanceof Integer||value instanceof Long) {
			final NumberFormat format;
			if (locale==null) {
				format=NumberFormat.getInstance();
			} else {
				format=NumberFormat.getNumberInstance(locale);
			}
			synchronized(format){
				return format.format(value);
			}
		}
		return value.toString();
	}

	@SuppressWarnings("unchecked")
	protected <T> T getValueInternal(final StatisticsProperty<?> obj){
		return obj.getStatistics().get(this.toString(), (Class<T>)getType());
	}
	
	protected Class<?> getType() {
		return Long.class;
	}
	
	public void setValue(ExResultSet rs, String columnKey, StatisticsProperty<?> obj) throws SQLException {
		setValue(rs, columnKey, this.toString(), obj);
	}

	public void setValue(StatisticsProperty<?> obj, Object value) {
		String statisticsKey=this.toString();
		obj.getStatistics().remove(statisticsKey);
		obj.getStatistics().put(statisticsKey, value);
	}
	
	protected void setValue(ExResultSet rs, String columnKey, String statisticsKey, StatisticsProperty<?> obj) throws SQLException {
		Class<?> clazz=getType();
		if (clazz==Long.class) {
			setLongValue(rs, columnKey, statisticsKey, obj);
		}else if (clazz==Float.class) {
			setFloatValue(rs, columnKey, statisticsKey, obj);
		}
	}

	protected void setLongValue(ExResultSet rs, String columnKey, String statisticsKey, StatisticsProperty<?> obj) throws SQLException {
		Long value=rs.getLongValue(columnKey);
		if (value==null) {
			obj.getStatistics().remove(statisticsKey);
		}else {
			if (value.compareTo(0L)<0) {
				obj.getStatistics().remove(statisticsKey);
			} else {
				obj.getStatistics().put(statisticsKey, value);
			}
		}
	}

	protected void setFloatValue(ExResultSet rs, String columnKey, String statisticsKey, StatisticsProperty<?> obj) throws SQLException {
		Float value=rs.getFloatValue(columnKey);
		if (value==null) {
			obj.getStatistics().remove(statisticsKey);
		}else {
			if (value.compareTo(0f)<0) {
				obj.getStatistics().remove(statisticsKey);
			} else {
				obj.getStatistics().put(statisticsKey, value);
			}
		}
	}

}
