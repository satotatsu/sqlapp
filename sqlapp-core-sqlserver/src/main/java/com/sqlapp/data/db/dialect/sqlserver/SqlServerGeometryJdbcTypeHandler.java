/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.crs.CoordinateReferenceSystem;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.geolatte.geom.crs.LinearUnit;
import org.geolatte.geom.codec.db.sqlserver.Decoders;
import org.geolatte.geom.codec.db.sqlserver.Encoders;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

public class SqlServerGeometryJdbcTypeHandler implements JdbcTypeHandler {

	public SqlServerGeometryJdbcTypeHandler() {
	}

	@Override
	public Object getObject(ResultSet rs, int columnIndex) throws SQLException {
		byte[] bytes = rs.getBytes(columnIndex);
		return bytes == null ? null : Decoders.decode(bytes);
	}

	@Override
	public Object getObject(ResultSet rs, String columnLabel) throws SQLException {
		byte[] bytes = rs.getBytes(columnLabel);
		return bytes == null ? null : Decoders.decode(bytes);
	}

	@Override
	public void setObject(PreparedStatement stmt, int parameterIndex, Object x) throws SQLException {
		Geometry<?> geometry = Converters.getDefault().convertObject(x, org.geolatte.geom.Geometry.class);
		if (geometry == null) {
			stmt.setNull(parameterIndex, java.sql.Types.VARBINARY);
			return;
		}
		stmt.setObject(parameterIndex, toSqlServerBytes(geometry));
	}

	/**
	 * SQL Server の geometry / geography のシリアル化形式に変換します。通常の INSERT と一括コピーで同じ表現を使うために共有します。
	 * <p>
	 * Geolatte のエンコーダーは SRID が 0 以下の図形で Z/M の指定を書き込めない(IllegalStateException になる)ため、
	 * その場合は一時的な SRID で符号化してから、先頭4バイトの SRID を 0 に戻します。
	 * </p>
	 * 
	 * @param geometry
	 *            図形
	 * @return SQL Server のシリアル化形式
	 */
	public static byte[] toSqlServerBytes(final Geometry<?> geometry) {
		if (geometry.getSRID() > 0 || !(geometry.hasZ() || geometry.hasM())) {
			return Encoders.encode(geometry);
		}
		final CoordinateReferenceSystem<?> crs = CoordinateReferenceSystems.mkCoordinateReferenceSystem(
				CoordinateReferenceSystems.WEB_MERCATOR, geometry.hasZ() ? LinearUnit.METER : null,
				geometry.hasM() ? LinearUnit.METER : null);
		final byte[] bytes = Encoders.encode(Geometry.forceToCrs(geometry, crs));
		// SRID(リトルエンディアンの int)を 0 にする
		bytes[0] = 0;
		bytes[1] = 0;
		bytes[2] = 0;
		bytes[3] = 0;
		return bytes;
	}
}
