/**
 * Copyright (C) 2007-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.sqlapp.data.db.datatype.BitStringJdbcTypeHandler;

/**
 * MySQLのBIT(n)用JDBCハンドラ。
 * <p>
 * MySQLは文字列をバイト列としてBIT列に格納するため、ビット文字列は整数値(最大64ビット)として設定します。
 * 読み込みはドライバが返すBoolean(BIT(1))/byte[]を列のビット数に合わせてビット文字列に変換します。
 * </p>
 */
public class MySqlBitStringJdbcTypeHandler extends BitStringJdbcTypeHandler {

	private static final long serialVersionUID = 1L;

	@Override
	protected void setBitString(final PreparedStatement stmt, final int parameterIndex, final String bits)
			throws SQLException {
		if (bits.length() > 64) {
			throw new IllegalArgumentException("MySQL BIT supports at most 64 bits: " + bits.length());
		}
		stmt.setLong(parameterIndex, bits.isEmpty() ? 0L : new BigInteger(bits, 2).longValue());
	}
}
