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

import java.util.Base64;

/**
 * @author SATOH
 *
 */
public class Base64Converter extends AbstractBinaryConverter {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2529076858508701049L;

	public Base64Converter() {
	}

	public Base64Converter(final ByteConverter unitConverter) {
		super(unitConverter);
	}

	@Override
	protected byte[] stringToBinary(final String value) {
		// MIME line breaks and other whitespace are accepted; standard and
		// URL-safe alphabets, with or without padding. Any other character is an
		// error instead of being skipped (the MIME decoder returned [] for "!!!").
		final StringBuilder builder = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (!Character.isWhitespace(c)) {
				builder.append(c);
			}
		}
		final String text = builder.toString();
		try {
			if (text.indexOf('-') >= 0 || text.indexOf('_') >= 0) {
				return Base64.getUrlDecoder().decode(text);
			}
			return Base64.getDecoder().decode(text);
		} catch (final IllegalArgumentException e) {
			throw new ConverterException("Invalid Base64 text: " + abbreviate(value), e);
		}
	}

	private static String abbreviate(final String value) {
		return value.length() <= 64 ? value : value.substring(0, 64) + "...";
	}

	/**
	 * One line without line breaks, so the value can be written to CSV, JSON or a
	 * single SQL literal.
	 */
	@Override
	protected String binaryToString(final byte[] value) {
		return Base64.getEncoder().encodeToString(value);
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
		if (!(obj instanceof Base64Converter)) {
			return false;
		}
		return true;
	}
}
