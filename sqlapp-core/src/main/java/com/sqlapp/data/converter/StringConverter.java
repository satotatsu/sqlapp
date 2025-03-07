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

package com.sqlapp.data.converter;

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.FileUtils.read;

import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLXML;
/**
 * 文字列のコンバーター
 * @author SATOH
 *
 */
public class StringConverter extends AbstractConverter<String>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7755839975092182747L;

	public StringConverter(){
	}

	public StringConverter(final Converters converters){
		this.converters=converters;
	}

	/**
	 * コンバータコレクション
	 */
	private Converters converters=null;
	
	/**
	 * @param converters the converters to set
	 */
	public StringConverter setConverters(final Converters converters) {
		this.converters = converters;
		return this;
	}

	/**
	 * itern文字列を返す場合true
	 */
	private boolean useIntern=false;
	
	@Override
	public String convertObject(final Object value) {
		if (value==null){
			return getDefaultValue();
		}else if (value instanceof String){
			return internString((String)value);
		}else if (value instanceof Reader){
			final Reader reader=cast(value);
			return read(reader);
		}else if (value instanceof Clob){
			final Clob lob=(Clob)value;
			try {
				return lob.getSubString(1, (int)lob.length());
			} catch (final SQLException e) {
				throw new RuntimeException(e);
			}
		}else if (value instanceof SQLXML){
			final SQLXML sqlxml=(SQLXML)value;
			try {
				try {
					return sqlxml.getString();
				} catch (final SQLException e) {
					throw new RuntimeException(e);
				}
			} finally{
				try {
					sqlxml.free();
				} catch (final SQLException e) {
				}
			}
		}
		if (converters!=null){
			return internString(converters.convertString(value, value.getClass()));
		}
		return internString(value.toString());
	}

	private String internString(final String value){
		if (this.useIntern){
			return value.intern();
		}
		return value;
	}
	
	@Override
	public String convertString(final String value) {
		return internString(value);
	}

	public boolean isUseIntern() {
		return useIntern;
	}

	public void setUseIntern(final boolean useIntern) {
		this.useIntern = useIntern;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof StringConverter)){
			return false;
		}
		final StringConverter con=cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())){
			return false;
		}
		if (!eq(this.useIntern, con.useIntern)){
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
	public String copy(final Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}
}