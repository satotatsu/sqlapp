/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.ByteOrder;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.GeometryCollection;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.WkbDecoder;
import org.geolatte.geom.codec.WkbEncoder;
import org.geolatte.geom.codec.Wkt;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

public class MySqlGeometryJdbcTypeHandler implements JdbcTypeHandler {

	public MySqlGeometryJdbcTypeHandler() {
	}

	@Override
	public Object getObject(ResultSet rs, int columnIndex) throws SQLException {
		byte[] bytes = rs.getBytes(columnIndex);
		if (bytes == null) {
			return null;
		}
		return toGeometry(bytes);
	}

	private Object toGeometry(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.from(bytes);
		WkbDecoder decoder = Wkb.newDecoder(Wkb.Dialect.MYSQL_WKB);
		return decoder.decode(buffer);
	}

	@Override
	public Object getObject(ResultSet rs, String columnLabel) throws SQLException {
		byte[] bytes = rs.getBytes(columnLabel);
		if (bytes == null) {
			return null;
		}
		return toGeometry(bytes);
	}

	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex, Object x) throws SQLException {
		if (x == null) {
			stmt.setBytes(parameterIndex, null);
			return;
		}
		Geometry<?> geometry = Converters.getDefault().convertObject(x, Geometry.class);
		if (geometry == null) {
			throw new IllegalArgumentException("Non-null MySQL geometry value cannot be converted to null");
		}
		stmt.setBytes(parameterIndex, toMySqlBytes(geometry));
	}

	/**
	 * MySQL の内部形式(SRID + WKB)に変換します。通常の INSERT と LOAD DATA で同じ表現を使うために共有します。
	 * <p>
	 * MySQL は Z/M 座標と、空の Point などの GEOMETRYCOLLECTION 以外の空の図形を保存できません。 符号化すると Z/M
	 * が黙って落ちたり、別の種類の図形に変わったりするため、変換せずにエラーにします。
	 * </p>
	 * 
	 * @param geometry
	 *            図形
	 * @return MySQL の内部形式
	 * @throws IllegalArgumentException
	 *             MySQL で表現できない図形の場合
	 */
	public static byte[] toMySqlBytes(final Geometry<?> geometry) {
		if (geometry.hasZ() || geometry.hasM()) {
			throw new IllegalArgumentException(
					"MySQL geometry does not support Z or M coordinates. value=" + Wkt.toWkt(geometry));
		}
		validateEmpty(geometry);
		final WkbEncoder encoder = Wkb.newEncoder(Wkb.Dialect.MYSQL_WKB);
		final ByteBuffer buffer = encoder.encode(geometry, ByteOrder.NDR);
		return buffer == null ? null : buffer.toByteArray();
	}

	private static void validateEmpty(final Geometry<?> geometry) {
		if (geometry instanceof GeometryCollection<?> collection) {
			for (int i = 0; i < collection.getNumGeometries(); i++) {
				validateEmpty(collection.getGeometryN(i));
			}
			if (geometry.getGeometryType() == org.geolatte.geom.GeometryType.GEOMETRYCOLLECTION) {
				return;
			}
		}
		if (geometry.isEmpty()) {
			throw new IllegalArgumentException(
					"MySQL geometry supports only GEOMETRYCOLLECTION EMPTY as an empty value. value="
							+ Wkt.toWkt(geometry));
		}
	}
}
