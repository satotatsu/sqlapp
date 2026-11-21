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


import com.sqlapp.util.CommonUtils;

/**
 * IntegerType Converter
 * 
 * @author SATOH
 *
 */
public class IntegerConverter extends AbstractNumberConverter<Integer> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7561472272841065369L;

	protected static final Integer ZERO = Integer.valueOf(0);
	private static final Integer ONE = Integer.valueOf(1);

	@Override
	public Integer convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			// any kind of Optional: empty is the default value
			return convertObject(getOptionalValue(value));
		} else if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof Integer) {
			return (Integer) value;
		}
		return convertOther(value);
	}

	private Integer convertOther(final Object value) {
		if (value instanceof String) {
			return convert(trim((String) value));
		} else if (value instanceof Number) {
			return toInt((Number) value, value);
		} else if (value instanceof Boolean) {
			if (((Boolean) value).booleanValue()) {
				return ONE;
			} else {
				return ZERO;
			}
		} else if (value instanceof byte[]) {
			return toInt((byte[]) value);
		}
		return convert(value.toString());
	}

	private Integer convert(final String value) {
		if (CommonUtils.isEmpty(value)) {
			return null;
		}
		if (getNumberFormat() == null) {
			return (int) IntegralNumbers.parse(value, "Integer", Integer.MIN_VALUE, Integer.MAX_VALUE);
		}
		return toInt(parse(value), value);
	}

	private static Integer toInt(final Number value, final Object original) {
		return (int) IntegralNumbers.toLong(value, original, "Integer", Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public String format(final Integer value) {
		if (value == null) {
			return null;
		}
		if (getNumberFormat() == null) {
			return value.toString();
		}
		return super.format((Number) value);
	}

	public static int toInt(final byte[] bytes) {
		return (int) IntegralNumbers.fromBytes(bytes, "Integer", Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof IntegerConverter)) {
			return false;
		}
		final IntegerConverter con = cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public Integer copy(final Object obj) {
		if (obj == null) {
			return null;
		}
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return true;
	}
}
