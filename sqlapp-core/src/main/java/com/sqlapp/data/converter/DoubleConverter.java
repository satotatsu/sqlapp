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


import static com.sqlapp.util.CommonUtils.isEmpty;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.sqlapp.util.CommonUtils;

/**
 * DoubleType Converter
 * @author SATOH
 *
 */
public class DoubleConverter extends AbstractNumberConverter<Double>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5367819124100202702L;
	
	protected static final Double ZERO=Double.valueOf(0);
	private static final Double ONE=Double.valueOf(1);
	
	@Override
	public Double convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Double){
			return (Double)value;
		}else if (value instanceof OptionalInt){
			final OptionalInt op=(OptionalInt)value;
			return op.isPresent()?(double)op.getAsInt():null;
		}else if (value instanceof OptionalLong){
			final OptionalLong op=(OptionalLong)value;
			return op.isPresent()?(double)op.getAsLong():null;
		}else if (value instanceof OptionalDouble){
			final OptionalDouble op=(OptionalDouble)value;
			return op.isPresent()?(double)op.getAsDouble():null;
		}else if (value instanceof String){
			return convert(trim((String)value));
		}else if (value instanceof Number){
			return ((Number)value).doubleValue();
		}else if (value instanceof Boolean){
			if (((Boolean)value).booleanValue()){
				return ONE;
			} else{
				return ZERO;				
			}
		}else if (value instanceof byte[]){
			return toDouble((byte[])value);
		}
		return convert(value.toString());
	}

	private Double convert(final String value){
		if(CommonUtils.isEmpty(value)) {
			return null;
		}
		if (getNumberFormat()==null){
			return Double.valueOf(value);
		}
		return parse(value).doubleValue();
	}

	@Override
	public String convertString(final Double value) {
		if (value==null){
			return null;
		}
		if (getNumberFormat()==null){
			return value.toString();
		}
		return format(value);
	}

	public static double toDouble(final byte[] bytes){
		final ByteBuffer keyBuffer = ByteBuffer.wrap(bytes);
		keyBuffer.order(ByteOrder.BIG_ENDIAN);
		return keyBuffer.getDouble();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof DoubleConverter)){
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
	public Double copy(final Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return false;
	}
}
