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

import static com.sqlapp.util.BinaryUtils.toHexString;

/**
 * 16進byte配列のコンバータ抽象クラス
 * 
 * @author SATOH
 *
 */
public class HexBinaryConverter extends AbstractBinaryConverter {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2529076858508701049L;

	@Override
	protected byte[] stringToBinary(String value) {
		return parseHex(value);
	}

	/**
	 * Reads hexadecimal text: optional {@code 0x}, {@code \\x} (PostgreSQL bytea)
	 * or {@code X'...'} (SQL literal) notation and whitespace are accepted, and an
	 * odd number of digits is read as a number with a leading zero ("ABC" is
	 * 0x0ABC). Other characters are an error.
	 */
	static byte[] parseHex(final String value) {
		String text = value.trim();
		if (text.length() >= 3 && (text.charAt(0) == 'X' || text.charAt(0) == 'x') && text.charAt(1) == '\''
				&& text.charAt(text.length() - 1) == '\'') {
			text = text.substring(2, text.length() - 1);
		} else if (text.startsWith("0x") || text.startsWith("0X") || text.startsWith("\\x")) {
			text = text.substring(2);
		}
		final StringBuilder digits = new StringBuilder(text.length() + 1);
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				continue;
			}
			if (Character.digit(c, 16) < 0) {
				throw new ConverterException("Invalid hexadecimal text: " + value);
			}
			digits.append(c);
		}
		if (digits.length() % 2 != 0) {
			digits.insert(0, '0');
		}
		final byte[] bytes = new byte[digits.length() / 2];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) ((Character.digit(digits.charAt(i * 2), 16) << 4)
					| Character.digit(digits.charAt(i * 2 + 1), 16));
		}
		return bytes;
	}

	@Override
	protected String binaryToString(byte[] value) {
		return toHexString(value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof HexBinaryConverter)) {
			return false;
		}
		return true;
	}

}
