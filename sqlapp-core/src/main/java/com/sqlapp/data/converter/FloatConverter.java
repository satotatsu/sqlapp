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

import com.sqlapp.util.CommonUtils;

/**
 * FloatType Converter
 * 
 * @author SATOH
 *
 */
public class FloatConverter extends AbstractNumberConverter<Float> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -1020330152422724190L;

	protected static final Float ZERO = Float.valueOf(0);
	private static final Float ONE = Float.valueOf(1);

	@Override
	public Float convertObject(final Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		}
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			// any kind of Optional: empty is the default value
			return convertObject(getOptionalValue(value));
		} else if (value instanceof Float) {
			return (Float) value;
		} else if (value instanceof String) {
			return convert(trim((String) value));
		} else if (value instanceof Number) {
			return toFloat(((Number) value).doubleValue(), value);
		} else if (value instanceof Boolean) {
			if (((Boolean) value).booleanValue()) {
				return ONE;
			} else {
				return ZERO;
			}
		} else if (value instanceof byte[]) {
			return toFloat((byte[]) value);
		}
		return convert(value.toString());
	}

	private Float convert(final String value) {
		if (CommonUtils.isEmpty(value)) {
			return null;
		}
		if (getNumberFormat() == null) {
			try {
				return toFloat(Double.parseDouble(value), value);
			} catch (final NumberFormatException e) {
				throw new ConverterException("Cannot convert to Float: " + value, e);
			}
		}
		return toFloat(parse(value).doubleValue(), value);
	}

	/**
	 * A finite value beyond the float range is an error, as for the integral
	 * types, instead of silently becoming Infinity. NaN and Infinity themselves
	 * are kept.
	 */
	private static Float toFloat(final double number, final Object original) {
		final float result = (float) number;
		if (Float.isInfinite(result) && !Double.isInfinite(number)) {
			throw new ConverterException("Value out of range for Float: " + original);
		}
		return result;
	}

	@Override
	public String format(final Float value) {
		if (value == null) {
			return null;
		}
		if (getNumberFormat() == null) {
			return value.toString();
		}
		return super.format((Number) value);
	}

	public static float toFloat(final byte[] bytes) {
		final ByteBuffer keyBuffer = ByteBuffer.wrap(bytes);
		keyBuffer.order(ByteOrder.BIG_ENDIAN);
		if (bytes.length == Float.BYTES) {
			return keyBuffer.getFloat();
		} else if (bytes.length == Double.BYTES) {
			return (float) keyBuffer.getDouble();
		}
		throw new ConverterException("Cannot convert " + bytes.length + " bytes to Float (4 or 8 bytes expected)");
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
		if (!(obj instanceof FloatConverter)) {
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
	public Float copy(final Object obj) {
		if (obj == null) {
			return null;
		}
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return false;
	}
}
