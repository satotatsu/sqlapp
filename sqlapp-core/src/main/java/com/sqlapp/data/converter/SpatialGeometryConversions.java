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

import java.util.ArrayList;
import java.util.List;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.GeometryType;
import org.geolatte.geom.codec.Wkt;

import com.sqlapp.data.geometry.AbstractGeometry;
import com.sqlapp.data.geometry.AbstractPoint2D;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;

/**
 * sqlapp の幾何型(PostgreSQL の組み込み幾何型)と、空間型(Geolatte の Geometry)の相互変換。
 *
 * <h2>sqlapp の幾何型 → 空間型</h2>
 * <table border="1">
 * <caption>変換規則</caption>
 * <tr><th>sqlapp</th><th>空間型</th></tr>
 * <tr><td>Point</td><td>POINT</td></tr>
 * <tr><td>Lseg</td><td>2点の LINESTRING</td></tr>
 * <tr><td>開いた Path</td><td>LINESTRING(2点以上。始点と終点が同じものは閉じた Path と区別できないためエラー)</td></tr>
 * <tr><td>閉じた Path</td><td>終点に始点を加えた LINESTRING(環)</td></tr>
 * <tr><td>Polygon</td><td>外周1つの POLYGON(終点に始点を加える。3点以上)</td></tr>
 * <tr><td>Box</td><td>軸に平行な長方形の POLYGON</td></tr>
 * <tr><td>Circle</td><td>{@value #CIRCLE_SEGMENTS} 角形で近似した POLYGON。近似のため円には戻せない</td></tr>
 * <tr><td>Line</td><td>無限直線に対応する空間型がないためエラー</td></tr>
 * </table>
 * SRID は持たない。座標に NaN / Infinity を含む値は WKT で表せないためエラーにする。
 *
 * <h2>空間型 → sqlapp の幾何型</h2>
 * 変換先の型ごとに、上の規則の逆で変換する。閉じた Path は「始点と終点が同じ LINESTRING」から作り、最後の点を除く。 Box
 * は軸に平行な長方形の POLYGON からだけ作り、PostgreSQL と同じく(右上, 左下)の順にする。
 * <ul>
 * <li>SRID は捨てる(sqlapp の幾何型は SRID を持たない)。</li>
 * <li>Z / M を持つ値、空の図形、穴のある POLYGON、Multi 系 / GeometryCollection はエラーにする(sqlapp の幾何型は2次元)。</li>
 * <li>Circle / Line へは変換できない。</li>
 * </ul>
 */
public final class SpatialGeometryConversions {

	/** Circle を近似する多角形の頂点数 */
	public static final int CIRCLE_SEGMENTS = 64;

	private SpatialGeometryConversions() {
	}

	// ---- sqlapp → 空間型 ----

	/**
	 * sqlapp の幾何型を空間型に変換します。
	 *
	 * @param value
	 *            sqlapp の幾何型
	 * @return 空間型
	 * @throws IllegalArgumentException
	 *             対応する空間型がない場合
	 */
	public static Geometry<?> toSpatial(final AbstractGeometry value) {
		if (value == null) {
			return null;
		}
		return Wkt.fromWkt(toWkt(value), Wkt.Dialect.POSTGIS_EWKT_1);
	}

	static String toWkt(final AbstractGeometry value) {
		if (value instanceof Circle circle) {
			return "POLYGON((" + circle(circle.getX(), circle.getY(), circle.getR()) + "))";
		}
		if (value instanceof Point point) {
			return "POINT(" + coordinates(point) + ")";
		}
		if (value instanceof Lseg lseg) {
			return "LINESTRING(" + coordinates(lseg.getPoints()) + ")";
		}
		if (value instanceof Path path) {
			return "LINESTRING(" + pathCoordinates(path.getPoints(), path.open, "Path") + ")";
		}
		if (value instanceof Polygon polygon) {
			return "POLYGON((" + ring(polygon.getPoints(), "Polygon") + "))";
		}
		if (value instanceof Box box) {
			final Point p1 = box.getPoints()[0];
			final Point p2 = box.getPoints()[1];
			final Point[] corners = { p1, new Point(p2.getX(), p1.getY()), p2, new Point(p1.getX(), p2.getY()) };
			return "POLYGON((" + ring(corners, "Box") + "))";
		}
		if (value instanceof Line) {
			throw new IllegalArgumentException(
					"Line is an infinite line and has no spatial geometry equivalent. value=" + value);
		}
		throw new IllegalArgumentException("Unsupported geometry type: " + value.getClass().getName());
	}

	private static String pathCoordinates(final AbstractPoint2D[] points, final boolean open, final String type) {
		if (open) {
			if (points.length < 2) {
				throw new IllegalArgumentException(
						"An open " + type + " needs 2 or more points to be a LINESTRING. points=" + points.length);
			}
			if (points[0].equals(points[points.length - 1])) {
				throw new IllegalArgumentException("An open " + type
						+ " whose start and end points are equal can't be distinguished from a closed one. start="
						+ points[0]);
			}
			return coordinates(points);
		}
		return coordinates(closed(points));
	}

	private static String ring(final AbstractPoint2D[] points, final String type) {
		if (points.length < 3) {
			throw new IllegalArgumentException(type + " needs 3 or more points to be a POLYGON. points=" + points.length);
		}
		return coordinates(closed(points));
	}

	private static AbstractPoint2D[] closed(final AbstractPoint2D[] points) {
		final AbstractPoint2D[] ret = new AbstractPoint2D[points.length + 1];
		System.arraycopy(points, 0, ret, 0, points.length);
		ret[points.length] = points[0];
		return ret;
	}

	private static String circle(final double x, final double y, final double r) {
		if (!(r > 0) || Double.isInfinite(r)) {
			throw new IllegalArgumentException("Circle radius must be positive and finite to approximate. r=" + r);
		}
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
			// 最後の点は始点と同じ座標にする(三角関数の誤差で閉じなくならないように)
			final double angle = 2 * Math.PI * (i % CIRCLE_SEGMENTS) / CIRCLE_SEGMENTS;
			if (i > 0) {
				builder.append(',');
			}
			builder.append(number(x + r * Math.cos(angle))).append(' ').append(number(y + r * Math.sin(angle)));
		}
		return builder.toString();
	}

	private static String coordinates(final AbstractPoint2D... points) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < points.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			final AbstractPoint2D point = points[i];
			builder.append(number(point.getX())).append(' ').append(number(point.getY()));
		}
		return builder.toString();
	}

	private static String number(final double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new IllegalArgumentException("Coordinates must be finite to convert to a spatial geometry. value=" + value);
		}
		return Double.toString(value);
	}

	// ---- 空間型 → sqlapp ----

	/**
	 * 空間型を sqlapp の幾何型に変換します。SRID は捨てます。
	 *
	 * @param value
	 *            空間型
	 * @param type
	 *            変換先の型
	 * @return sqlapp の幾何型
	 * @throws IllegalArgumentException
	 *             変換先の型で表せない場合
	 */
	public static <T extends AbstractGeometry> T fromSpatial(final Geometry<?> value, final Class<T> type) {
		if (value == null) {
			return null;
		}
		if (value.hasZ() || value.hasM()) {
			throw new IllegalArgumentException(
					type.getSimpleName() + " is two-dimensional and can't hold Z or M coordinates. value=" + wkt(value));
		}
		if (value.isEmpty()) {
			throw new IllegalArgumentException(type.getSimpleName() + " can't be empty. value=" + wkt(value));
		}
		final GeometryType geometryType = value.getGeometryType();
		final Object ret;
		if (type == Point.class) {
			require(geometryType == GeometryType.POINT, value, type);
			ret = point(value, 0);
		} else if (type == Lseg.class) {
			require(geometryType == GeometryType.LINESTRING && value.getNumPositions() == 2, value, type);
			ret = new Lseg(point(value, 0), point(value, 1));
		} else if (type == Path.class) {
			require(geometryType == GeometryType.LINESTRING && value.getNumPositions() >= 2, value, type);
			final int n = value.getNumPositions();
			final boolean closed = java.util.Arrays.equals(position(value, 0), position(value, n - 1));
			final int size = closed ? n - 1 : n;
			ret = new Path(!closed, points(value, size));
		} else if (type == Polygon.class) {
			final int n = singleRing(value, type);
			ret = new Polygon(points(value, n - 1));
		} else if (type == Box.class) {
			final int n = singleRing(value, type);
			ret = box(value, n);
		} else {
			throw new IllegalArgumentException(
					"Spatial geometry can't be converted to " + type.getSimpleName() + ". value=" + wkt(value));
		}
		return type.cast(ret);
	}

	private static int singleRing(final Geometry<?> value, final Class<?> type) {
		require(value.getGeometryType() == GeometryType.POLYGON, value, type);
		final org.geolatte.geom.Polygon<?> polygon = (org.geolatte.geom.Polygon<?>) value;
		if (polygon.getNumInteriorRing() > 0) {
			throw new IllegalArgumentException(
					type.getSimpleName() + " can't hold a POLYGON with holes. value=" + wkt(value));
		}
		final int n = value.getNumPositions();
		if (n < 4) {
			throw new IllegalArgumentException(
					type.getSimpleName() + " needs 3 or more points. value=" + wkt(value));
		}
		return n;
	}

	private static Box box(final Geometry<?> value, final int n) {
		final List<double[]> points = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			points.add(position(value, i));
		}
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (final double[] p : points) {
			minX = Math.min(minX, p[0]);
			minY = Math.min(minY, p[1]);
			maxX = Math.max(maxX, p[0]);
			maxY = Math.max(maxY, p[1]);
		}
		boolean rectangle = n == 5;
		if (rectangle) {
			for (int i = 0; i < 4; i++) {
				final double[] p = points.get(i);
				final double[] q = points.get(i + 1);
				// 各頂点が外接矩形の角にあり、各辺が軸に平行であること
				rectangle &= (p[0] == minX || p[0] == maxX) && (p[1] == minY || p[1] == maxY)
						&& (p[0] == q[0] || p[1] == q[1]);
			}
		}
		if (!rectangle) {
			throw new IllegalArgumentException("Box needs an axis-aligned rectangle POLYGON. value=" + wkt(value));
		}
		return new Box(maxX, maxY, minX, minY);
	}

	private static void require(final boolean condition, final Geometry<?> value, final Class<?> type) {
		if (!condition) {
			throw new IllegalArgumentException(
					value.getGeometryType() + " can't be converted to " + type.getSimpleName() + ". value=" + wkt(value));
		}
	}

	private static double[] position(final Geometry<?> value, final int index) {
		final double[] coordinates = value.getPositionN(index).toArray(null);
		return new double[] { coordinates[0], coordinates[1] };
	}

	private static Point point(final Geometry<?> value, final int index) {
		final double[] p = position(value, index);
		return new Point(p[0], p[1]);
	}

	private static Point[] points(final Geometry<?> value, final int size) {
		final Point[] ret = new Point[size];
		for (int i = 0; i < size; i++) {
			ret[i] = point(value, i);
		}
		return ret;
	}

	private static String wkt(final Geometry<?> value) {
		return Wkt.toWkt(value, Wkt.Dialect.POSTGIS_EWKT_1);
	}
}
