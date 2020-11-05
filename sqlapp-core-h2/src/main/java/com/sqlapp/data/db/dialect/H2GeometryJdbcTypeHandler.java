/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see <http://www.gnu.org/licenses/>.
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

public class H2GeometryJdbcTypeHandler  implements JdbcTypeHandler {

	public H2GeometryJdbcTypeHandler() {
	}

	@Override
	public Object getObject(ResultSet rs, int columnIndex)
			throws SQLException {
		byte[] bytes=rs.getBytes(columnIndex);
		WkbDecoder decoder = Wkb.newDecoder(Wkb.Dialect.POSTGIS_EWKB_1);
		return decoder.decode(ByteBuffer.from(bytes));
	}

	@Override
	public Object getObject(ResultSet rs, String columnLabel)
			throws SQLException {
		byte[] bytes=rs.getBytes(columnLabel);
		WkbDecoder decoder = Wkb.newDecoder(Wkb.Dialect.POSTGIS_EWKB_1);
		return decoder.decode(ByteBuffer.from(bytes));
	}

	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex,
			Object x) throws SQLException {
		Geometry<?> geometry=Converters.getDefault().convertObject(x, org.geolatte.geom.Geometry.class);
		WkbEncoder encoder = Wkb.newEncoder(Wkb.Dialect.POSTGIS_EWKB_1);
	    ByteBuffer buffer = encoder.encode(geometry, ByteOrder.NDR);
	    stmt.setBytes(parameterIndex, buffer.toByteArray());
	}
}