/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.ByteOrder;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkb;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;

/**
 * JDBC handler for SAP HANA {@code ST_GEOMETRY} and {@code ST_POINT}.
 * <p>
 * The SAP HANA JDBC driver returns spatial values as WKB bytes without an SRID,
 * so values read through this handler carry no coordinate reference system; the
 * SRID is a column property ({@link SapHanaSpatial#SRID}). A geometry with an
 * SRID is written as SAP HANA EWKB and must match the column SRID; a geometry
 * without an SRID is written without one and takes the column SRID.
 */
public class SapHanaGeometryJdbcTypeHandler implements JdbcTypeHandler {

	@Override
	public Object getObject(final ResultSet rs, final int columnIndex) throws SQLException {
		return decode(rs.getBytes(columnIndex));
	}

	@Override
	public Object getObject(final ResultSet rs, final String columnLabel) throws SQLException {
		return decode(rs.getBytes(columnLabel));
	}

	@Override
	public void setObject(final PreparedStatement stmt, final int parameterIndex, final Object x) throws SQLException {
		final Geometry<?> geometry = x == null ? null : Converters.getDefault().convertObject(x, Geometry.class);
		if (geometry == null) {
			stmt.setNull(parameterIndex, Types.VARBINARY);
			return;
		}
		stmt.setBytes(parameterIndex, encode(geometry));
	}

	static Geometry<?> decode(final byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		return Wkb.newDecoder(Wkb.Dialect.HANA_EWKB).decode(ByteBuffer.from(bytes));
	}

	static byte[] encode(final Geometry<?> geometry) {
		// The HANA EWKB encoder writes SRID 0 explicitly, which SAP HANA rejects for
		// a column with an SRID; PostGIS EWKB omits an unset SRID.
		final Wkb.Dialect dialect = geometry.getSRID() > 0 ? Wkb.Dialect.HANA_EWKB : Wkb.Dialect.POSTGIS_EWKB_1;
		return Wkb.newEncoder(dialect).encode(geometry, ByteOrder.NDR).toByteArray();
	}
}
