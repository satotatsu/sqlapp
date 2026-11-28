/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import java.sql.ResultSet;
import java.sql.SQLException;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.ByteOrder;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.WkbDecoder;
import org.geolatte.geom.codec.WkbEncoder;
import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.codec.WktDecoder;
import org.postgresql.util.PGobject;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

public class PostgresGeometryJdbcTypeHandler implements JdbcTypeHandler {

	public PostgresGeometryJdbcTypeHandler() {
	}

	@Override
	public Object getObject(ResultSet rs, int columnIndex) throws SQLException {
		Object obj = rs.getObject(columnIndex);
		return convertInternal(obj);
	}

	private Object convertInternal(Object obj) {
		if (obj == null) {
			return null;
		}
		if (obj instanceof PGobject) {
			String pgValue = ((PGobject) obj).getValue();
			if (pgValue.charAt(0) == 'S') {
				WktDecoder decoder = Wkt.newDecoder(Wkt.Dialect.POSTGIS_EWKT_1);
				return decoder.decode(pgValue);
			}
			ByteBuffer buffer = ByteBuffer.from(pgValue);
			WkbDecoder decoder = Wkb.newDecoder(Wkb.Dialect.POSTGIS_EWKB_1);
			return decoder.decode(buffer);
		}
		byte[] bytes = Converters.getDefault().convertObject(obj, byte[].class);
		WkbDecoder decoder = Wkb.newDecoder(Wkb.Dialect.POSTGIS_EWKB_1);
		return decoder.decode(ByteBuffer.from(bytes));
	}

	@Override
	public Object getObject(ResultSet rs, String columnLabel) throws SQLException {
		Object obj = rs.getObject(columnLabel);
		return convertInternal(obj);
	}

	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex, Object x) throws SQLException {
		Geometry<?> geometry = Converters.getDefault().convertObject(x, org.geolatte.geom.Geometry.class);
		if (geometry == null) {
			stmt.setNull(parameterIndex, java.sql.Types.OTHER);
			return;
		}
		stmt.setBytes(parameterIndex, toEwkb(geometry));
	}

	/**
	 * PostGIS へ送る EWKB(リトルエンディアン)に変換します。通常の INSERT と COPY で同じ表現を使うために共有します。
	 * 
	 * @param geometry 図形
	 * @return EWKB
	 */
	public static byte[] toEwkb(final Geometry<?> geometry) {
		if (geometry instanceof org.geolatte.geom.Point && geometry.isEmpty()) {
			// Geolatte は空の Point を GEOMETRYCOLLECTION EMPTY として符号化するため、
			// PostGIS と同じく座標を NaN とした Point として符号化する
			return emptyPointEwkb(geometry);
		}
		final WkbEncoder encoder = Wkb.newEncoder(Wkb.Dialect.POSTGIS_EWKB_1);
		return encoder.encode(geometry, ByteOrder.NDR).toByteArray();
	}

	private static byte[] emptyPointEwkb(final Geometry<?> geometry) {
		final boolean hasSrid = geometry.getSRID() > 0;
		final int dimension = 2 + (geometry.hasZ() ? 1 : 0) + (geometry.hasM() ? 1 : 0);
		final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1 + 4 + (hasSrid ? 4 : 0) + 8 * dimension)
				.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		buffer.put((byte) 1);
		int type = 1;
		if (geometry.hasZ()) {
			type |= 0x80000000;
		}
		if (geometry.hasM()) {
			type |= 0x40000000;
		}
		if (hasSrid) {
			type |= 0x20000000;
		}
		buffer.putInt(type);
		if (hasSrid) {
			buffer.putInt(geometry.getSRID());
		}
		for (int i = 0; i < dimension; i++) {
			buffer.putDouble(Double.NaN);
		}
		return buffer.array();
	}
}