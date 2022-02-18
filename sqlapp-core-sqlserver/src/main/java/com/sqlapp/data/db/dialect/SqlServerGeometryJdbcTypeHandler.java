/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.db.sqlserver.Decoders;
import org.geolatte.geom.codec.db.sqlserver.Encoders;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

public class SqlServerGeometryJdbcTypeHandler  implements JdbcTypeHandler {

	public SqlServerGeometryJdbcTypeHandler() {
	}

	@Override
	public Object getObject(ResultSet rs, int columnIndex)
			throws SQLException {
		byte[] bytes=rs.getBytes(columnIndex);
		return Decoders.decode(bytes);
	}

	@Override
	public Object getObject(ResultSet rs, String columnLabel)
			throws SQLException {
		byte[] bytes=rs.getBytes(columnLabel);
		return Decoders.decode(bytes);
	}

	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex,
			Object x) throws SQLException {
		Geometry<?> geometry=Converters.getDefault().convertObject(x, org.geolatte.geom.Geometry.class);
	    byte[] bytes = Encoders.encode(geometry);
	    stmt.setObject(parameterIndex, bytes);
	}
}
