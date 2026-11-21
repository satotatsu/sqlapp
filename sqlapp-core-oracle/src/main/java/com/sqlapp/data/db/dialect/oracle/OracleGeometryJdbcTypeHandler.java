/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.db.oracle.ConnectionFinder;
import org.geolatte.geom.codec.db.oracle.Decoders;
import org.geolatte.geom.codec.db.oracle.DefaultConnectionFinder;
import org.geolatte.geom.codec.db.oracle.Encoders;
import org.geolatte.geom.codec.db.oracle.OracleJDBCTypeFactory;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

public class OracleGeometryJdbcTypeHandler implements JdbcTypeHandler {

	private final ConnectionFinder connectionFinder;

	private final OracleJDBCTypeFactory sqlTypeFactory;

	public OracleGeometryJdbcTypeHandler() {
		connectionFinder = new DefaultConnectionFinder();
		sqlTypeFactory = new OracleJDBCTypeFactory(connectionFinder);
	}

	@Override
	public Object getObject(ResultSet rs, int columnIndex) throws SQLException {
		final Object value = rs.getObject(columnIndex);
		return value == null ? null : Decoders.decode((java.sql.Struct) value);
	}

	@Override
	public Object getObject(ResultSet rs, String columnLabel) throws SQLException {
		final Object value = rs.getObject(columnLabel);
		return value == null ? null : Decoders.decode((java.sql.Struct) value);
	}

	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex, Object x) throws SQLException {
		Geometry<?> geo = Converters.getDefault().convertObject(x, org.geolatte.geom.Geometry.class);
		if (geo == null) {
			stmt.setNull(parameterIndex, java.sql.Types.STRUCT, "MDSYS.SDO_GEOMETRY");
			return;
		}
		if (geo.isEmpty()) {
			// SDO_GEOMETRY には空の図形の表現がない(NULL とは意味が異なるため変換しない)
			throw new IllegalArgumentException("Oracle SDO_GEOMETRY cannot represent an empty geometry. value="
					+ org.geolatte.geom.codec.Wkt.toWkt(geo));
		}
		java.sql.Struct struct = Encoders.encode(geo, stmt.getConnection(), sqlTypeFactory);
		stmt.setObject(parameterIndex, struct);
	}
}