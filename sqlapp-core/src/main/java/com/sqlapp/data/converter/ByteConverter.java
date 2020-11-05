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

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.sqlapp.util.CommonUtils;

/**
 * ByteType Converter
 * @author SATOH
 *
 */
public class ByteConverter extends AbstractNumberConverter<Byte>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4231103865018471812L;

	protected static final Byte ZERO=Byte.valueOf((byte)0);
	private static final Byte ONE=Byte.valueOf((byte)1);

	@Override
	public Byte convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof Byte){
			return (Byte)value;
		}else if (value instanceof String){
			return convert(trim((String)value));
		}else if (value instanceof Number){
			return ((Number)value).byteValue();
		}else if (value instanceof OptionalInt){
			final OptionalInt op=(OptionalInt)value;
			return op.isPresent()?Integer.valueOf(op.getAsInt()).byteValue():null;
		}else if (value instanceof OptionalLong){
			final OptionalLong op=(OptionalLong)value;
			return op.isPresent()?Long.valueOf(op.getAsLong()).byteValue():null;
		}else if (value instanceof OptionalDouble){
			final OptionalDouble op=(OptionalDouble)value;
			return op.isPresent()?Double.valueOf(op.getAsDouble()).byteValue():null;
		}else if (value instanceof Boolean){
			if (((Boolean)value).booleanValue()){
				return ONE;
			} else{
				return ZERO;				
			}
		}
		return convert(value.toString());
	}

	private Byte convert(final String value){
		if(CommonUtils.isEmpty(value)) {
			return null;
		}
		if (getNumberFormat()==null){
			return Byte.valueOf(value);
		}
		return parse(value).byteValue();
	}

	@Override
	public String convertString(final Byte value) {
		if (value==null){
			return null;
		}
		if (getNumberFormat()==null){
			return value.toString();
		}
		return format(value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof ByteConverter)){
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
	public Byte copy(final Object obj){
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return true;
	}
}
