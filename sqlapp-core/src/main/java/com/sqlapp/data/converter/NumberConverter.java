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

import java.math.BigDecimal;

import static com.sqlapp.util.CommonUtils.*;
/**
 * Numberのコンバーター
 * @author SATOH
 *
 */
public class NumberConverter extends AbstractConverter<Number>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7755839975092182747L;

	public NumberConverter(){
	}

	public NumberConverter(Converters converters){
		this.converters=converters;
	}

	/**
	 * コンバータコレクション
	 */
	private Converters converters=null;
	/**
	 * 変換先のデフォルトクラス
	 */
	private Class<? extends Number> defaultClass=BigDecimal.class;
	
	/**
	 * @param converters the converters to set
	 */
	public NumberConverter setConverters(Converters converters) {
		this.converters = converters;
		return this;
	}

	@Override
	public Number convertObject(Object value) {
		if (value==null){
			return getDefaultValue();
		}else if (value instanceof Number){
			return (Number)value;
		}
		return this.converters.convertObject(value, defaultClass);
	}

	/**
	 * @return the defaultClass
	 */
	public Class<? extends Number> getDefaultClass() {
		return defaultClass;
	}

	/**
	 * @param defaultClass the defaultClass to set
	 */
	public NumberConverter setDefaultClass(Class<? extends Number> defaultClass) {
		this.defaultClass = defaultClass;
		return this;
	}

	@Override
	public String convertString(Number value) {
		if (value==null){
			return null;
		}
		return this.converters.convertString(value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof NumberConverter)){
			return false;
		}
		NumberConverter con=cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())){
			return false;
		}
		if (!eq(this.getDefaultClass(), con.getDefaultClass())){
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
	public Number copy(Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}
}