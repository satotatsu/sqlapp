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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author SATOH
 *
 */
public class BigDecimalConverter extends AbstractNumberConverter<BigDecimal> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1406845674338998578L;

	@Override
	public BigDecimal convertObject(final Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		}
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			// any kind of Optional: empty is the default value
			return convertObject(getOptionalValue(value));
		} else if (value instanceof BigDecimal) {
			return (BigDecimal) value;
		} else if (value instanceof String) {
			return convert(((String) value).trim());
		} else if (value instanceof Number) {
			return toBigDecimal((Number) value);
		} else if (value instanceof Boolean) {
			if (((Boolean) value).booleanValue()) {
				return BigDecimal.ONE;
			} else {
				return BigDecimal.ZERO;
			}
		}
		return convert(value.toString());
	}

	/**
	 * Exact conversion: a BigInteger beyond the long range keeps its value
	 * (instead of the wrapped {@code longValue()}), and float/double use their
	 * shortest decimal text as before.
	 */
	private static BigDecimal toBigDecimal(final Number value) {
		if (value instanceof BigInteger integer) {
			return new BigDecimal(integer);
		} else if (value instanceof Integer || value instanceof Long || value instanceof Short
				|| value instanceof Byte || value instanceof AtomicInteger || value instanceof AtomicLong) {
			return BigDecimal.valueOf(value.longValue());
		} else if (value instanceof Double || value instanceof Float) {
			final double number = value.doubleValue();
			if (Double.isNaN(number) || Double.isInfinite(number)) {
				throw new ConverterException("Cannot convert to BigDecimal: " + value);
			}
			return value instanceof Float ? new BigDecimal(value.toString()) : BigDecimal.valueOf(number);
		}
		try {
			return new BigDecimal(value.toString());
		} catch (final NumberFormatException e) {
			final double number = value.doubleValue();
			if (Double.isNaN(number) || Double.isInfinite(number)) {
				throw new ConverterException("Cannot convert to BigDecimal: " + value, e);
			}
			return BigDecimal.valueOf(number);
		}
	}

	private BigDecimal convert(final String value) {
		if (getNumberFormat() == null) {
			try {
				return new BigDecimal(value);
			} catch (final NumberFormatException e) {
				throw new ConverterException("Cannot convert to BigDecimal: " + value, e);
			}
		}
		if (getNumberFormat() instanceof DecimalFormat format) {
			format.setParseBigDecimal(true);
			return (BigDecimal) parse(value);
		}
		return toBigDecimal(parse(value));
	}

	@Override
	public String format(final BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (getNumberFormat() == null) {
			return value.toPlainString();
		}
		return super.format((Number) value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof BigDecimalConverter)) {
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
	public BigDecimal copy(final Object obj) {
		return convertObject(obj);
	}

	@Override
	protected boolean getParseIntegerOnly() {
		return false;
	}
}
