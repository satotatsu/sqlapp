/**
 * Copyright (C) 2007-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import com.sqlapp.data.db.datatype.BitStringJdbcTypeHandler;

/**
 * PostgreSQLのBIT/VARBIT用JDBCハンドラ。
 * <p>
 * {@link Types#BIT}は真偽値として送られるため、ビット文字列は型未指定({@link Types#OTHER})で送り、
 * サーバー側で列の型(bit/varbit)に変換させます。
 * </p>
 */
public class PostgresBitStringJdbcTypeHandler extends BitStringJdbcTypeHandler {

	private static final long serialVersionUID = 1L;

	@Override
	protected void setBitString(final PreparedStatement stmt, final int parameterIndex, final String bits)
			throws SQLException {
		stmt.setObject(parameterIndex, bits, Types.OTHER);
	}

	@Override
	protected void setBitNull(final PreparedStatement stmt, final int parameterIndex) throws SQLException {
		stmt.setNull(parameterIndex, Types.OTHER);
	}
}
