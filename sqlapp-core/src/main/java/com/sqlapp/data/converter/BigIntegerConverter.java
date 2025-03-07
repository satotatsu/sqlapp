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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * BigIntegerType Converter
 * @author SATOH
 *
 */
public class BigIntegerConverter extends AbstractNumberConverter<BigInteger>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 775619452908753182L;
	
	@Override
	public BigInteger convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof BigInteger){
			return (BigInteger)value;
		} else if (value instanceof String){
			return convert(((String)value).trim());
		}else if (value instanceof BigDecimal){
			return ((BigDecimal)value).toBigInteger();
		}else if (value instanceof Number){
			return BigInteger.valueOf(((Number)value).longValue());
		}else if (value instanceof OptionalInt){
			final OptionalInt op=(OptionalInt)value;
			return op.isPresent()?BigInteger.valueOf(op.getAsInt()):null;
		}else if (value instanceof OptionalLong){
			final OptionalLong op=(OptionalLong)value;
			return op.isPresent()?BigInteger.valueOf(op.getAsLong()):null;
		}else if (value instanceof OptionalDouble){
			final OptionalDouble op=(OptionalDouble)value;
			return op.isPresent()?BigInteger.valueOf((long)op.getAsDouble()):null;
		}else if (value instanceof Boolean){
			if (((Boolean)value).booleanValue()){
				return BigInteger.ONE;
			} else{
				return BigInteger.ZERO;
			}
		}
		return convert(value.toString());
	}

	private BigInteger convert(final String value){
		if (getNumberFormat()==null){
			return new BigInteger(value);
		}
		final DecimalFormat format=(DecimalFormat)getNumberFormat();	
		format.setParseBigDecimal(true);
		return ((BigDecimal)parse(value)).toBigInteger();
	}

	@Override
	public String convertString(final BigInteger value) {
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
		if (!(obj instanceof BigIntegerConverter)){
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
	public BigInteger copy(final Object obj){
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return true;
	}
}
