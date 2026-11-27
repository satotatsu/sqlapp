/**
 * Copyright (C) 2007-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.datatype;

import java.io.Serializable;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.BitSet;

/**
 * BIT/VARBITのJDBCハンドラ。値は"0101"形式の文字列で扱います。
 * <p>
 * 読み込みでは、ドライバが返す文字列、真偽値(BIT(1))、byte[](右詰め)、数値をビット文字列に変換します。
 * byte[]と数値は列の精度(ビット数)に合わせて桁を揃えます。書き込みは既定では文字列として設定します。
 * DB固有の設定方法が必要な場合は{@link #setBitString(PreparedStatement, int, String)}を上書きします。
 * </p>
 */
public class BitStringJdbcTypeHandler implements JdbcTypeHandler, Serializable {

	private static final long serialVersionUID = 1L;

	@Override
	public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
		final Object value = rs.getObject(columnIndex);
		if (value == null) {
			return null;
		}
		return toBitString(value, precision(rs, columnIndex));
	}

	@Override
	public Object getObject(final ResultSet rs, final String columnLabel) throws SQLException {
		return getObject(rs, rs.findColumn(columnLabel));
	}

	@Override
	public void setObject(final PreparedStatement stmt, final int parameterIndex, final Object x)
			throws SQLException {
		if (x == null) {
			setBitNull(stmt, parameterIndex);
		} else {
			setBitString(stmt, parameterIndex, toBitString(x, 0));
		}
	}

	/**
	 * ビット文字列を設定します。
	 *
	 * @param stmt           PreparedStatement
	 * @param parameterIndex パラメータ位置
	 * @param bits           "0101"形式のビット文字列
	 */
	protected void setBitString(final PreparedStatement stmt, final int parameterIndex, final String bits)
			throws SQLException {
		stmt.setString(parameterIndex, bits);
	}

	/**
	 * NULLを設定します。
	 */
	protected void setBitNull(final PreparedStatement stmt, final int parameterIndex) throws SQLException {
		stmt.setNull(parameterIndex, Types.VARCHAR);
	}

	private static int precision(final ResultSet rs, final int columnIndex) {
		try {
			return Math.max(rs.getMetaData().getPrecision(columnIndex), 0);
		} catch (final SQLException e) {
			return 0;
		}
	}

	/**
	 * 値を"0101"形式のビット文字列に変換します。
	 *
	 * @param value  値
	 * @param length ビット数(0以下なら桁を揃えない)
	 */
	public static String toBitString(final Object value, final int length) {
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean) {
			return pad(((Boolean) value).booleanValue() ? "1" : "0", length);
		}
		if (value instanceof byte[]) {
			final byte[] bytes = (byte[]) value;
			final StringBuilder builder = new StringBuilder(bytes.length * 8);
			for (final byte b : bytes) {
				for (int bit = 7; bit >= 0; bit--) {
					builder.append(((b >> bit) & 1) == 0 ? '0' : '1');
				}
			}
			// Bytes are right-aligned (MySQL): the value is in the last bits.
			if (length > 0 && builder.length() > length) {
				return builder.substring(builder.length() - length);
			}
			return pad(builder.toString(), length);
		}
		if (value instanceof BitSet) {
			final BitSet bitSet = (BitSet) value;
			final int size = Math.max(length, bitSet.length());
			final StringBuilder builder = new StringBuilder(size);
			for (int i = 0; i < size; i++) {
				builder.append(bitSet.get(i) ? '1' : '0');
			}
			return builder.toString();
		}
		if (value instanceof Number) {
			final BigInteger number = value instanceof BigInteger ? (BigInteger) value
					: BigInteger.valueOf(((Number) value).longValue());
			if (number.signum() < 0) {
				throw new IllegalArgumentException("A bit string value must not be negative: " + value);
			}
			return pad(number.toString(2), length);
		}
		final String text = value.toString().trim();
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (c != '0' && c != '1') {
				throw new IllegalArgumentException("A bit string must consist of 0 and 1: " + text);
			}
		}
		return text;
	}

	private static String pad(final String bits, final int length) {
		if (length <= bits.length()) {
			return bits;
		}
		return "0".repeat(length - bits.length()) + bits;
	}
}
