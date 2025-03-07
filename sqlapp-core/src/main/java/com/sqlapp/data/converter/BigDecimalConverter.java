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
import java.text.DecimalFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * @author SATOH
 *
 */
public class BigDecimalConverter extends AbstractNumberConverter<BigDecimal>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1406845674338998578L;
	
	@Override
	public BigDecimal convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}
		if (value instanceof BigDecimal){
			return (BigDecimal)value;
		}else if (value instanceof String){
			return convert(((String)value).trim());
		}else if (value instanceof Float){
			return BigDecimal.valueOf(((Float)value));
		}else if (value instanceof Double){
			return BigDecimal.valueOf(((Double)value));
		}else if (value instanceof Number){
			return BigDecimal.valueOf(((Number)value).longValue());
		}else if (value instanceof OptionalInt){
			final OptionalInt op=(OptionalInt)value;
			return op.isPresent()?BigDecimal.valueOf(op.getAsInt()):null;
		}else if (value instanceof OptionalLong){
			final OptionalLong op=(OptionalLong)value;
			return op.isPresent()?BigDecimal.valueOf(op.getAsLong()):null;
		}else if (value instanceof OptionalDouble){
			final OptionalDouble op=(OptionalDouble)value;
			return op.isPresent()?BigDecimal.valueOf(op.getAsDouble()):null;
		}else if (value instanceof Boolean){
			if (((Boolean)value).booleanValue()){
				return BigDecimal.ONE;
			} else{
				return BigDecimal.ZERO;
			}
		}
		return convert(value.toString());
	}

	private BigDecimal convert(final String value){
		if (getNumberFormat()==null){
			return new BigDecimal(value);
		}
		final DecimalFormat format=(DecimalFormat)getNumberFormat();	
		format.setParseBigDecimal(true);
		return (BigDecimal)parse(value);
	}

	@Override
	public String convertString(final BigDecimal value) {
		if (value==null){
			return null;
		}
		if (getNumberFormat()==null){
			return value.toPlainString();
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
		if (!(obj instanceof BigDecimalConverter)){
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
	public BigDecimal copy(final Object obj){
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return false;
	}
}
