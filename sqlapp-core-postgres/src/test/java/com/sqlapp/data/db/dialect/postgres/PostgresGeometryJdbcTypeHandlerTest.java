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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;

/**
 * {@link PostgresGeometryJdbcTypeHandler#setObject} の回帰テスト(DBには接続しない)。
 *
 * CSV から読み込んだ値など、文字列で渡された GEOMETRY 値を EWKB として送ること。
 */
public class PostgresGeometryJdbcTypeHandlerTest {

	private static List<Object[]> calls = new ArrayList<>();

	private static PreparedStatement statement() {
		calls = new ArrayList<>();
		return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
					calls.add(new Object[] { method.getName(), args });
					return null;
				});
	}

	private static Geometry<?> sentGeometry() {
		assertEquals(1, calls.size());
		assertEquals("setBytes", calls.get(0)[0]);
		final Object[] args = (Object[]) calls.get(0)[1];
		assertEquals(1, args[0]);
		return Wkb.fromWkb(ByteBuffer.from((byte[]) args[1]), Wkb.Dialect.POSTGIS_EWKB_1);
	}

	@Test
	public void testSetObjectFromEwktString() throws Exception {
		new PostgresGeometryJdbcTypeHandler().setObject(statement(), 1, "SRID=4326;POINT Z(139 35 10)");
		final Geometry<?> geometry = sentGeometry();
		assertEquals(4326, geometry.getSRID());
		assertTrue(geometry.hasZ());
		assertArrayEquals(new double[] { 139, 35, 10 }, geometry.getPositionN(0).toArray(null));
	}

	@Test
	public void testSetObjectFromHexEwkbString() throws Exception {
		new PostgresGeometryJdbcTypeHandler().setObject(statement(), 1,
				"0101000040000000000000F03F00000000000000400000000000000840");
		final Geometry<?> geometry = sentGeometry();
		assertTrue(geometry.hasM());
		assertArrayEquals(new double[] { 1, 2, 3 }, geometry.getPositionN(0).toArray(null));
	}

	/**
	 * 空の Point は PostGIS と同じ EWKB(座標が NaN)にする。期待値は PostGIS の ST_AsHEXEWKB の出力
	 * (Claude 側の検証環境で取得)。
	 */
	@Test
	public void testEmptyPointEwkb() {
		assertEquals("0101000000000000000000F87F000000000000F87F", hex("POINT EMPTY"));
		assertEquals("0101000020E6100000000000000000F87F000000000000F87F", hex("SRID=4326;POINT EMPTY"));
		assertEquals("0101000080000000000000F87F000000000000F87F000000000000F87F", hex("POINT Z EMPTY"));
		assertEquals("01010000C0000000000000F87F000000000000F87F000000000000F87F000000000000F87F",
				hex("POINT ZM EMPTY"));
	}

	private static String hex(final String wkt) {
		final StringBuilder builder = new StringBuilder();
		for (final byte b : PostgresGeometryJdbcTypeHandler.toEwkb(Wkt.fromWkt(wkt))) {
			builder.append(String.format("%02X", b));
		}
		return builder.toString();
	}

	@Test
	public void testGetObjectNull() throws Exception {
		final java.sql.ResultSet rs = (java.sql.ResultSet) Proxy.newProxyInstance(
				java.sql.ResultSet.class.getClassLoader(), new Class<?>[] { java.sql.ResultSet.class },
				(proxy, method, args) -> null);
		org.junit.jupiter.api.Assertions.assertNull(new PostgresGeometryJdbcTypeHandler().getObject(rs, 1));
		org.junit.jupiter.api.Assertions.assertNull(new PostgresGeometryJdbcTypeHandler().getObject(rs, "g"));
	}

	@Test
	public void testSetObjectNull() throws Exception {
		new PostgresGeometryJdbcTypeHandler().setObject(statement(), 1, null);
		assertEquals(1, calls.size());
		assertEquals("setNull", calls.get(0)[0]);
		assertEquals(java.sql.Types.OTHER, ((Object[]) calls.get(0)[1])[1]);
	}

	@Test
	public void testSetObjectFromGeometry() throws Exception {
		new PostgresGeometryJdbcTypeHandler().setObject(statement(), 1, Wkt.fromWkt("SRID=3857;LINESTRING(0 0,1 1)"));
		final Geometry<?> geometry = sentGeometry();
		assertEquals(3857, geometry.getSRID());
		assertEquals(2, geometry.getNumPositions());
	}
}
