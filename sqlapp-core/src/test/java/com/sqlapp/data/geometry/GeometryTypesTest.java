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
package com.sqlapp.data.geometry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 幾何型の構築・等価性・複製・計算に関する回帰テスト
 */
public class GeometryTypesTest {

	// ---- 数値コンストラクタ ----

	@Test
	public void testLineNumericConstructor() {
		final Line line = assertDoesNotThrow(() -> new Line(0, 0, 1, 1));
		assertEquals(new Point(0, 0), line.getPoints()[0]);
		assertEquals(new Point(1, 1), line.getPoints()[1]);
	}

	@Test
	public void testLine3DNumericConstructor() {
		final Line3D line = assertDoesNotThrow(() -> new Line3D(0, 0, 0, 1, 1, 1));
		assertEquals(new Point3D(0, 0, 0), line.getPoints()[0]);
		assertEquals(new Point3D(1, 1, 1), line.getPoints()[1]);
	}

	@Test
	public void testLineRejectsIdenticalPoints() {
		assertThrows(IllegalArgumentException.class, () -> new Line(1, 2, 1, 2));
		assertThrows(IllegalArgumentException.class, () -> new Line(new Point(1, 2), new Point(1, 2)));
		assertThrows(IllegalArgumentException.class, () -> new Line3D(1, 2, 3, 1, 2, 3));
	}

	// ---- 点数違いの等価性 ----

	@Test
	public void testEqualsWithDifferentPointCount() {
		final Path shorter = new Path(0, 0, 1, 1);
		final Path longer = new Path(0, 0, 1, 1, 2, 2);
		assertFalse(shorter.equals(longer));
		assertFalse(longer.equals(shorter));
		final Polygon p1 = new Polygon(0, 0, 1, 1, 1, 0);
		final Polygon p2 = new Polygon(0, 0, 1, 1, 1, 0, 0, 1);
		assertFalse(p1.equals(p2));
		assertFalse(p2.equals(p1));
		final Path3D q1 = new Path3D(0, 0, 0, 1, 1, 1);
		final Path3D q2 = new Path3D(0, 0, 0, 1, 1, 1, 2, 2, 2);
		assertFalse(q1.equals(q2));
		assertFalse(q2.equals(q1));
	}

	@Test
	public void testEqualsAndHashCode() {
		assertEquals(new Path(0, 0, 1, 1), new Path(0, 0, 1, 1));
		assertEquals(new Path(0, 0, 1, 1).hashCode(), new Path(0, 0, 1, 1).hashCode());
		assertNotEquals(new Path(true, 0, 0, 1, 1), new Path(false, 0, 0, 1, 1));
		assertEquals(new Circle(1, 2, 3), new Circle(1, 2, 3));
		assertEquals(new Circle(1, 2, 3).hashCode(), new Circle(1, 2, 3).hashCode());
		assertNotEquals(new Circle(1, 2, 3), new Circle(9, 2, 3));
		assertEquals(new Circle3D(1, 2, 3, 4).hashCode(), new Circle3D(1, 2, 3, 4).hashCode());
		assertEquals(new Box(), new Box());
	}

	// ---- 面積 ----

	@Test
	public void testAreaOfSquare() {
		assertEquals(1d, new Path(0, 0, 1, 0, 1, 1, 0, 1).getArea(), 1e-12);
	}

	@Test
	public void testAreaOfTriangle() {
		assertEquals(2d, new Path(0, 0, 2, 0, 0, 2).getArea(), 1e-12);
		// 時計回りでも同じ面積
		assertEquals(2d, new Path(0, 0, 0, 2, 2, 0).getArea(), 1e-12);
	}

	@Test
	public void testAreaOfDegeneratePath() {
		assertEquals(0d, new Path(0, 0).getArea(), 1e-12);
		assertEquals(0d, new Path(0, 0, 1, 1).getArea(), 1e-12);
	}

	@Test
	public void testAreaOfOpenPathIsNaN() {
		assertTrue(Double.isNaN(new Path(true, 0, 0, 2, 0, 0, 2).getArea()));
	}

	// ---- clone の独立性 ----

	@Test
	public void testCloneIsIndependent() {
		assertCloneIndependent(new Box(0, 0, 1, 1));
		assertCloneIndependent(new Line(0, 0, 1, 1));
		assertCloneIndependent(new Lseg(0, 0, 1, 1));
		assertCloneIndependent(new Path(true, 0, 0, 1, 1, 2, 2));
		assertCloneIndependent(new Polygon(0, 0, 1, 1, 1, 0));
		assertCloneIndependent(new Box3D(0, 0, 0, 1, 1, 1));
		assertCloneIndependent(new Line3D(0, 0, 0, 1, 1, 1));
		assertCloneIndependent(new Lseg3D(0, 0, 0, 1, 1, 1));
		assertCloneIndependent(new Path3D(true, 0, 0, 0, 1, 1, 1));
		assertCloneIndependent(new Polygon3D(0, 0, 0, 1, 1, 1, 1, 0, 0));
	}

	@Test
	public void testClonePreservesPathOpen() {
		assertTrue(new Path(true, 0, 0, 1, 1).clone().open);
		assertTrue(new Path3D(true, 0, 0, 0, 1, 1, 1).clone().open);
	}

	private static void assertCloneIndependent(final AbstractMultiPoint<?> original) {
		final String before = original.toString();
		final AbstractMultiPoint<?> copy = original.clone();
		assertEquals(original, copy);
		assertNotSame(original.getPoints(), copy.getPoints());
		for (int i = 0; i < original.getPoints().length; i++) {
			assertNotSame(original.getPoints()[i], copy.getPoints()[i]);
		}
		copy.getPoints()[0].setX(99);
		assertEquals(before, original.toString());
	}

	// ---- 次元変換で状態を保持する ----

	@Test
	public void testDimensionConversionPreservesPathOpen() {
		final Path open = new Path(true, 0, 0, 1, 1);
		assertTrue(open.toUpperDimension().open);
		assertTrue(open.toUpperDimension().toLowerDimension().open);
		assertEquals(open, open.toUpperDimension().toLowerDimension());
		final Path closed = new Path(false, 0, 0, 1, 1);
		assertFalse(closed.toUpperDimension().open);
		final Path3D open3 = new Path3D(true, 0, 0, 0, 1, 1, 1);
		assertTrue(open3.toLowerDimension().open);
	}

	@Test
	public void testDimensionConversion() {
		assertEquals(new Point3D(1, 2, 0), new Point(1, 2).toUpperDimension());
		assertEquals(new Point(1, 2), new Point3D(1, 2, 3).toLowerDimension());
		assertEquals(new Circle3D(1, 2, 0, 3), new Circle(1, 2, 3).toUpperDimension());
		assertEquals(new Circle(1, 2, 4), new Circle3D(1, 2, 3, 4).toLowerDimension());
		assertEquals(new Box3D(0, 0, 0, 1, 1, 0), new Box(0, 0, 1, 1).toUpperDimension());
		assertEquals(new Polygon(0, 0, 1, 1, 1, 0), new Polygon3D(0, 0, 5, 1, 1, 5, 1, 0, 5).toLowerDimension());
	}

	// ---- 不正な座標数・点数の拒否 ----

	@Test
	public void testRejectCoordinateCountNotDivisibleByDimension() {
		assertThrows(IllegalArgumentException.class, () -> new Polygon(0, 0, 1, 1, 99));
		assertThrows(IllegalArgumentException.class, () -> new Path(0, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new Path3D(0, 0, 0, 1));
		assertThrows(IllegalArgumentException.class, () -> new Polygon3D(0, 0, 0, 1, 1));
	}

	@Test
	public void testRejectWrongPointCountForTwoPointTypes() {
		final Point p = new Point(0, 0);
		assertThrows(IllegalArgumentException.class, () -> new Box().setPoints(p, new Point(1, 1), new Point(2, 2)));
		assertThrows(IllegalArgumentException.class, () -> new Lseg().setPoints(p));
		assertThrows(IllegalArgumentException.class, () -> new Line().setPoints(p, new Point(1, 1), new Point(2, 2)));
		assertThrows(IllegalArgumentException.class,
				() -> new Box3D().setPoints(new Point3D(0, 0, 0), new Point3D(1, 1, 1), new Point3D(2, 2, 2)));
	}

	@Test
	public void testRejectEmptyPointList() {
		assertThrows(IllegalArgumentException.class, () -> new Path().setPoints(new Point[0]));
		assertThrows(IllegalArgumentException.class, () -> new Polygon().setPoints(new Point[0]));
	}
}
