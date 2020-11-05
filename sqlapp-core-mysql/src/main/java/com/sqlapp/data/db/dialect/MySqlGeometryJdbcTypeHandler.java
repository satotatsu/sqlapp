/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.ByteOrder;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.WkbDecoder;
import org.geolatte.geom.codec.WkbEncoder;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

public class MySqlGeometryJdbcTypeHandler  implements JdbcTypeHandler {

	public MySqlGeometryJdbcTypeHandler() {
	}

	@Override
	public Object getObject(ResultSet rs, int columnIndex)
			throws SQLException {
		byte[] bytes=rs.getBytes(columnIndex);
		if (bytes==null){
		    return null;
		}
		return toGeometry(bytes);
	}
	
	private Object toGeometry(byte[] bytes){
		ByteBuffer buffer = ByteBuffer.from(bytes);
		WkbDecoder decoder = Wkb.newDecoder(Wkb.Dialect.MYSQL_WKB);
		return decoder.decode(buffer);
	}

	@Override
	public Object getObject(ResultSet rs, String columnLabel)
			throws SQLException {
		byte[] bytes=rs.getBytes(columnLabel);
		if (bytes==null){
		    return null;
		}
		return toGeometry(bytes);
	}

	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex,
			Object x) throws SQLException {
		WkbEncoder encoder = Wkb.newEncoder(Wkb.Dialect.MYSQL_WKB);
		Geometry<?> geometry = Converters.getDefault().convertObject(x, Geometry.class);
		ByteBuffer buffer = encoder.encode(geometry, ByteOrder.NDR);
		byte[] bytes = buffer == null ? null : buffer.toByteArray();
		stmt.setBytes(parameterIndex, bytes);
	}
}
