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
import static com.sqlapp.util.CommonUtils.lowerMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BooleanConverter extends AbstractConverter<Boolean> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5692455210592650318L;
	private static final Map<String, Boolean> BOOL_MAP;
	private static final Map<String, String> BOOL_TRUE_PARE_MAP;
	private static final Map<String, String> BOOL_FALSE_PARE_MAP;
	/**
	 * true value
	 */
	private String trueString = null;
	/**
	 * false value
	 */
	private String falseString = null;

	static {
		BOOL_MAP = lowerMap();
		BOOL_TRUE_PARE_MAP = lowerMap();
		BOOL_FALSE_PARE_MAP = lowerMap();
		registerChar("1", "0");
		register("1.0", "0.0");
		register("x'1'", "x'0'");
		registerChar("true", "false");
		registerChar("t", "f");
		registerChar("yes", "no");
		registerChar("y", "n");
		registerChar("on", "off");
		registerChar("ok", "ng");
	}

	private static void register(String trueValue, String falseValue) {
		BOOL_MAP.put(trueValue, Boolean.TRUE);
		BOOL_MAP.put(falseValue, Boolean.FALSE);
		BOOL_TRUE_PARE_MAP.put(trueValue, falseValue);
		BOOL_FALSE_PARE_MAP.put(falseValue, trueValue);
	}

	private static void registerChar(String trueValue, String falseValue) {
		register(trueValue, falseValue);
		BOOL_MAP.put("'" + trueValue + "'", Boolean.TRUE);
		BOOL_MAP.put("'" + falseValue + "'", Boolean.FALSE);
		BOOL_TRUE_PARE_MAP.put("'" + trueValue + "'", "'" + falseValue + "'");
		BOOL_FALSE_PARE_MAP.put("'" + falseValue + "'", "'" + trueValue + "'");
	}

	/**
	 * コンストラクタ
	 */
	public BooleanConverter() {
		super();
		this.setTrueString("1");
	}

	@Override
	public Boolean convertObject(Object value, Connection conn) {
		return convertObject(value);
	}

	/**
	 * Converts a value to Boolean, accepting as many representations as possible.
	 * <ul>
	 * <li>null, empty or blank values: the default value</li>
	 * <li>registered words such as true/false, yes/no, on/off, t/f, y/n, ok/ng
	 * (case-insensitive, optionally single-quoted)</li>
	 * <li>numbers and numeric text: zero is false, any other value is true (for
	 * example {@code BigDecimal("1.00")}, {@code 2} and {@code "-1"} are true,
	 * {@code "0.00"} is false)</li>
	 * </ul>
	 *
	 * @throws ConverterException when the value cannot be interpreted as Boolean
	 *                            (for example {@code "maybe"} or NaN)
	 */
	@Override
	public Boolean convertObject(Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isOptional(value)) {
			return convertObject(getOptionalValue(value));
		} else if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof Boolean bool) {
			return bool;
		} else if (value instanceof AtomicBoolean bool) {
			return bool.get();
		} else if (value instanceof Number number) {
			return numberToBoolean(number, value);
		}
		final String text = value.toString().trim();
		if (text.isEmpty()) {
			return getDefaultValue();
		}
		final Boolean bool = BOOL_MAP.get(text);
		if (bool != null) {
			return bool;
		}
		final Boolean numeric = textToBoolean(unquote(text));
		if (numeric != null) {
			return numeric;
		}
		throw new ConverterException(cannotConvertMessage(value));
	}

	private static String unquote(final String text) {
		if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
			return text.substring(1, text.length() - 1).trim();
		}
		return text;
	}

	private static Boolean textToBoolean(final String text) {
		if (text.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(text).signum() != 0;
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	private static Boolean numberToBoolean(final Number number, final Object original) {
		if (number instanceof BigDecimal decimal) {
			return decimal.signum() != 0;
		} else if (number instanceof BigInteger integer) {
			return integer.signum() != 0;
		} else if (number instanceof Integer || number instanceof Long || number instanceof Short
				|| number instanceof Byte || number instanceof AtomicInteger || number instanceof AtomicLong) {
			return number.longValue() != 0L;
		} else if (number instanceof Double || number instanceof Float) {
			final double value = number.doubleValue();
			if (Double.isNaN(value)) {
				throw new ConverterException(cannotConvertMessage(original));
			}
			return value != 0.0d;
		}
		final Boolean bool = textToBoolean(number.toString());
		if (bool != null) {
			return bool;
		}
		final double value = number.doubleValue();
		if (Double.isNaN(value)) {
			throw new ConverterException(cannotConvertMessage(original));
		}
		return value != 0.0d;
	}

	private static String cannotConvertMessage(final Object value) {
		return "Cannot convert to Boolean: " + value + " (" + value.getClass().getName() + ")";
	}

	@Override
	public String format(Boolean value) {
		if (value == null) {
			if (getDefaultValue() != null) {
				if (getDefaultValue()) {
					return getTrueString();
				} else {
					return getFalseString();
				}
			}
			return null;
		} else {
			if (value) {
				return getTrueString();
			} else {
				return getFalseString();
			}
		}
	}

	public String getTrueString() {
		return trueString;
	}

	public BooleanConverter setTrueString(String trueString) {
		if (BOOL_TRUE_PARE_MAP.containsKey(trueString)) {
			this.falseString = BOOL_TRUE_PARE_MAP.get(trueString);
		}
		this.trueString = trueString;
		return this;
	}

	public String getFalseString() {
		return falseString;
	}

	public BooleanConverter setFalseString(String falseString) {
		if (BOOL_FALSE_PARE_MAP.containsKey(falseString)) {
			this.trueString = BOOL_FALSE_PARE_MAP.get(falseString);
		}
		this.falseString = falseString;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (!super.equals(this)) {
			return false;
		}
		if (!(obj instanceof BooleanConverter)) {
			return false;
		}
		BooleanConverter con = cast(obj);
		if (!eq(this.getFalseString(), con.getFalseString())) {
			return false;
		}
		if (!eq(this.getTrueString(), con.getTrueString())) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Boolean copy(Object obj) {
		return convertObject(obj);
	}
}