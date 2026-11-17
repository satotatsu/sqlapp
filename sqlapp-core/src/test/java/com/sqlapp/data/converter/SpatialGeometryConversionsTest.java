/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */
package com.sqlapp.data.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.geometry.AbstractGeometry;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Box3D;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Circle3D;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Line3D;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Lseg3D;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Path3D;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Point3D;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.geometry.Polygon3D;

/**
 * sqlapp の幾何型と空間型の相互変換({@link SpatialGeometryConversions})のテスト。
 */
public class SpatialGeometryConversionsTest {

	private static String wkt(final Geometry<?> geometry) {
		return Wkt.toWkt(geometry, Wkt.Dialect.POSTGIS_EWKT_1);
	}

	/** sqlapp → 空間型 の期待値と、空間型 → sqlapp で元に戻ること */
	static Stream<Arguments> lossless() {
		return Stream.of(
				Arguments.of(new Point(1.5, -2), "POINT(1.5 -2)"),
				Arguments.of(new Point3D(1, 2, 3), "POINT(1 2 3)"),
				Arguments.of(new Lseg(0, 0, 1, 1), "LINESTRING(0 0,1 1)"),
				Arguments.of(new Lseg3D(0, 0, 1, 1, 1, 2), "LINESTRING(0 0 1,1 1 2)"),
				Arguments.of(new Path(true, 0, 0, 1, 1, 2, 0), "LINESTRING(0 0,1 1,2 0)"),
				Arguments.of(new Path(false, 0, 0, 1, 1, 2, 0), "LINESTRING(0 0,1 1,2 0,0 0)"),
				// 閉じた Path の終点が始点と同じでも、加えた1点だけを除くので戻る
				Arguments.of(new Path(false, 0, 0, 1, 1, 0, 0), "LINESTRING(0 0,1 1,0 0,0 0)"),
				Arguments.of(new Path(false, 0, 0), "LINESTRING(0 0,0 0)"),
				Arguments.of(new Path3D(true, 0, 0, 1, 1, 1, 2), "LINESTRING(0 0 1,1 1 2)"),
				Arguments.of(new Path3D(false, 0, 0, 1, 1, 1, 2, 2, 0, 3), "LINESTRING(0 0 1,1 1 2,2 0 3,0 0 1)"),
				Arguments.of(new Polygon(0, 0, 1, 1, 1, 0), "POLYGON((0 0,1 1,1 0,0 0))"),
				Arguments.of(new Polygon3D(0, 0, 1, 1, 1, 2, 1, 0, 3), "POLYGON((0 0 1,1 1 2,1 0 3,0 0 1))"),
				Arguments.of(new Box(2, 3, 0, 1), "POLYGON((2 3,0 3,0 1,2 1,2 3))"));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("lossless")
	public void testLossless(final AbstractGeometry value, final String expected) {
		final Geometry<?> spatial = SpatialGeometryConversions.toSpatial(value);
		assertEquals(expected, wkt(spatial));
		assertEquals(value instanceof Point3D || value instanceof Lseg3D || value instanceof Path3D
				|| value instanceof Polygon3D, spatial.hasZ());
		assertFalse(spatial.hasM());
		assertTrue(spatial.getSRID() <= 0, "no SRID");
		assertEquals(value, SpatialGeometryConversions.fromSpatial(spatial, value.getClass()));
	}

	/** Box は PostgreSQL と同じく(右上, 左下)の順に戻る */
	@Test
	public void testBoxIsNormalized() {
		final Geometry<?> spatial = SpatialGeometryConversions.toSpatial(new Box(0, 1, 2, 3));
		assertEquals("POLYGON((0 1,2 1,2 3,0 3,0 1))", wkt(spatial));
		assertEquals(new Box(2, 3, 0, 1), SpatialGeometryConversions.fromSpatial(spatial, Box.class));
		assertEquals(new Box(2, 3, 0, 1), SpatialGeometryConversions
				.fromSpatial(Wkt.fromWkt("POLYGON((0 1,0 3,2 3,2 1,0 1))"), Box.class));
	}

	@Test
	public void testCircleApproximation() {
		final Geometry<?> spatial = SpatialGeometryConversions.toSpatial(new Circle(1, 2, 3));
		assertEquals("POLYGON", spatial.getGeometryType().name());
		assertFalse(spatial.hasZ());
		assertEquals(SpatialGeometryConversions.CIRCLE_SEGMENTS + 1, spatial.getNumPositions());
		for (int i = 0; i < spatial.getNumPositions(); i++) {
			final double[] p = spatial.getPositionN(i).toArray(null);
			assertEquals(3d, Math.hypot(p[0] - 1, p[1] - 2), 1e-12);
		}
		final Geometry<?> spatial3d = SpatialGeometryConversions.toSpatial(new Circle3D(1, 2, 5, 3));
		assertTrue(spatial3d.hasZ());
		for (int i = 0; i < spatial3d.getNumPositions(); i++) {
			assertEquals(5d, spatial3d.getPositionN(i).toArray(null)[2]);
		}
		// 近似のため円には戻せない
		assertThrows(IllegalArgumentException.class, () -> SpatialGeometryConversions.fromSpatial(spatial, Circle.class));
	}

	static Stream<Arguments> notConvertibleToSpatial() {
		return Stream.of(Arguments.of(new Line(0, 0, 1, 1)), Arguments.of(new Line3D(0, 0, 0, 1, 1, 1)),
				Arguments.of(new Box3D(0, 0, 0, 1, 1, 1)),
				// 始点と終点が同じ開いた Path は閉じた Path と区別できない
				Arguments.of(new Path(true, 0, 0, 1, 1, 0, 0)), Arguments.of(new Path(true, 0, 0)),
				Arguments.of(new Polygon(0, 0, 1, 1)), Arguments.of(new Circle(0, 0, 0)),
				Arguments.of(new Circle(0, 0, -1)), Arguments.of(new Point(Double.NaN, 1)),
				Arguments.of(new Point(Double.POSITIVE_INFINITY, 1)));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("notConvertibleToSpatial")
	public void testNotConvertibleToSpatial(final AbstractGeometry value) {
		assertThrows(IllegalArgumentException.class, () -> SpatialGeometryConversions.toSpatial(value));
	}

	static Stream<Arguments> notConvertibleFromSpatial() {
		return Stream.of(
				Arguments.of("POINT Z(1 2 3)", Point.class), Arguments.of("POINT M(1 2 3)", Point3D.class),
				Arguments.of("POINT EMPTY", Point.class), Arguments.of("LINESTRING(0 0,1 1)", Point.class),
				Arguments.of("LINESTRING(0 0,1 1,2 2)", Lseg.class), Arguments.of("POINT(1 2)", Path.class),
				Arguments.of("POLYGON((0 0,10 0,10 10,0 0),(1 1,2 1,2 2,1 1))", Polygon.class),
				Arguments.of("MULTIPOINT((0 0),(1 1))", Path.class),
				Arguments.of("GEOMETRYCOLLECTION(POINT(1 2))", Point.class),
				Arguments.of("POLYGON((0 0,1 1,1 0,0 0))", Box.class),
				Arguments.of("POLYGON((0 0,2 0,2 1,1 2,0 1,0 0))", Box.class),
				Arguments.of("LINESTRING(0 0,1 1)", Line.class), Arguments.of("POLYGON((0 0,1 1,1 0,0 0))", Circle.class),
				Arguments.of("LINESTRING Z(0 0 1,1 1 2)", Path.class));
	}

	@ParameterizedTest(name = "[{index}] {0} -> {1}")
	@MethodSource("notConvertibleFromSpatial")
	public void testNotConvertibleFromSpatial(final String wkt, final Class<? extends AbstractGeometry> type) {
		assertThrows(IllegalArgumentException.class,
				() -> SpatialGeometryConversions.fromSpatial(Wkt.fromWkt(wkt), type));
	}

	@Test
	public void testSridIsDropped() {
		assertEquals(new Point(139.7, 35.6),
				SpatialGeometryConversions.fromSpatial(Wkt.fromWkt("SRID=4326;POINT(139.7 35.6)"), Point.class));
		assertEquals(new Path(true, 0, 0, 1, 1), SpatialGeometryConversions
				.fromSpatial(Wkt.fromWkt("SRID=3857;LINESTRING(0 0,1 1)"), Path.class));
	}

	@Test
	public void test2dTo3dUsesZeroZ() {
		assertEquals(new Point3D(1, 2, 0), SpatialGeometryConversions.fromSpatial(Wkt.fromWkt("POINT(1 2)"), Point3D.class));
		assertEquals(new Path3D(false, 0, 0, 0, 1, 1, 0), SpatialGeometryConversions
				.fromSpatial(Wkt.fromWkt("LINESTRING(0 0,1 1,0 0)"), Path3D.class));
	}

	@Test
	public void testNull() {
		assertNull(SpatialGeometryConversions.toSpatial(null));
		assertNull(SpatialGeometryConversions.fromSpatial(null, Point.class));
	}

	/** Converter から使われること */
	@Test
	public void testConverters() {
		final Geometry<?> spatial = new GeometryConverter().convertObject(new Path(false, 0, 0, 1, 1, 2, 0));
		assertEquals("LINESTRING(0 0,1 1,2 0,0 0)", wkt(spatial));
		assertEquals(new Path(false, 0, 0, 1, 1, 2, 0), new PathConverter().convertObject(spatial));
		assertEquals(new Point(1, 2), new PointConverter().convertObject(Wkt.fromWkt("SRID=4326;POINT(1 2)")));
		assertEquals(new Polygon(0, 0, 1, 1, 1, 0),
				new PolygonConverter().convertObject(Wkt.fromWkt("POLYGON((0 0,1 1,1 0,0 0))")));
		assertEquals(new Lseg(0, 0, 1, 1), new LsegConverter().convertObject(Wkt.fromWkt("LINESTRING(0 0,1 1)")));
		assertEquals(new Box(1, 1, 0, 0),
				new BoxConverter().convertObject(Wkt.fromWkt("POLYGON((0 0,1 0,1 1,0 1,0 0))")));
		assertThrows(IllegalArgumentException.class,
				() -> new CircleConverter().convertObject(Wkt.fromWkt("POINT(1 2)")));
		assertThrows(IllegalArgumentException.class,
				() -> new LineConverter().convertObject(Wkt.fromWkt("LINESTRING(0 0,1 1)")));
		assertThrows(IllegalArgumentException.class, () -> new GeometryConverter().convertObject(new Line(0, 0, 1, 1)));
	}
}
