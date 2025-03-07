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
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.sqlapp.util.CommonUtils;

/**
 * ShortType Converter
 * @author SATOH
 *
 */
public class ShortConverter extends AbstractNumberConverter<Short>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7210830756251120344L;
	
	protected static final Short ZERO=Short.valueOf((short)0);
	private static final Short ONE=Short.valueOf((short)1);
	
	@Override
	public Short convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Short){
			return (Short)value;
		}else if (value instanceof String){
			return convert(trim((String)value));
		}else if (value instanceof Number){
			return ((Number)value).shortValue();
		}else if (value instanceof Boolean){
			if (((Boolean)value).booleanValue()){
				return ONE;
			} else{
				return ZERO;				
			}
		}else if (value instanceof byte[]){
			return toShort((byte[])value);
		}
		return convert(value.toString());
	}

	private Short convert(final String value){
		if(CommonUtils.isEmpty(value)) {
			return null;
		}
		if (getNumberFormat()==null){
			return Short.valueOf(value);
		}
		return parse(value).shortValue();
	}

	@Override
	public String convertString(final Short value) {
		if (value==null){
			return null;
		}
		if (getNumberFormat()==null){
			return value.toString();
		}
		return format(value);
	}
	
	public static short toShort(final byte[] bytes){
		final ByteBuffer keyBuffer = ByteBuffer.wrap(bytes);
		keyBuffer.order(ByteOrder.BIG_ENDIAN);
		return keyBuffer.getShort();
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
		if (!(obj instanceof ShortConverter)){
			return false;
		}
		final ShortConverter con=cast(obj);
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
	public Short copy(final Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return true;
	}
}
