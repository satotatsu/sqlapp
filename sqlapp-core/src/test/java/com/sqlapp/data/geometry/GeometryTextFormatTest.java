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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 幾何型の文字列表現(setValue / toString)の回帰テスト。
 *
 * 受理形式は PostgreSQL 16 の入力関数で実際に確認した形式と、 既存実装が受理していた形式を合わせたもの。
 * 3D型は同じ構文で1点あたりの座標数を3としたもの。
 */
public class GeometryTextFormatTest {

	static Stream<Arguments> accepted() {
		return Stream.of(
				// point
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "(1,2)", new Point(1, 2)),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "1,2", new Point(1, 2)),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, " ( 1 , 2 ) ", new Point(1, 2)),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "(1.5,-2.25)", new Point(1.5, -2.25)),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "1e2,-Infinity",
						new Point(100, Double.NEGATIVE_INFINITY)),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "NaN,2", new Point(Double.NaN, 2)),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "(1.0E10,2.0)", new Point(1e10, 2)),
				// point3d
				Arguments.of((Supplier<AbstractGeometry>) Point3D::new, "(1,2,3)", new Point3D(1, 2, 3)),
				Arguments.of((Supplier<AbstractGeometry>) Point3D::new, "1,2,3", new Point3D(1, 2, 3)),
				Arguments.of((Supplier<AbstractGeometry>) Point3D::new, " ( 1 , 2 , 3 ) ", new Point3D(1, 2, 3)),
				// lseg
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "[(0,0),(1,1)]", new Lseg(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "((0,0),(1,1))", new Lseg(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "(0,0),(1,1)", new Lseg(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "0,0,1,1", new Lseg(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "(0,0,1,1)", new Lseg(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "[0,0,1,1]", new Lseg(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg3D::new, "[(0,0,0),(1,1,1)]",
						new Lseg3D(0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg3D::new, "0,0,0,1,1,1", new Lseg3D(0, 0, 0, 1, 1, 1)),
				// box
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "((0,0),(1,1))", new Box(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "(0,0),(1,1)", new Box(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "0,0,1,1", new Box(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "(0,0,1,1)", new Box(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "(1,1),(0,0)", new Box(1, 1, 0, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Box3D::new, "((0,0,0),(1,1,1))",
						new Box3D(0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Box3D::new, "0,0,0,1,1,1", new Box3D(0, 0, 0, 1, 1, 1)),
				// line (2点形式)
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "[(0,0),(1,1)]", new Line(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "((0,0),(1,1))", new Line(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "(0,0),(1,1)", new Line(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "0,0,1,1", new Line(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "(0,0,1,1)", new Line(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Line3D::new, "((0,0,0),(1,1,1))",
						new Line3D(0, 0, 0, 1, 1, 1)),
				// path
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "[(0,0),(1,1)]", new Path(true, 0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "[0,0,1,1]", new Path(true, 0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "[(0,0)]", new Path(true, 0, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, " [ ( 0 , 0 ) , ( 1 , 1 ) ] ",
						new Path(true, 0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "((0,0),(1,1))", new Path(false, 0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "(0,0),(1,1)", new Path(false, 0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "(0,0,1,1)", new Path(false, 0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "0,0,1,1", new Path(false, 0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path3D::new, "[(0,0,0),(1,1,1)]",
						new Path3D(true, 0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path3D::new, "((0,0,0),(1,1,1))",
						new Path3D(false, 0, 0, 0, 1, 1, 1)),
				// polygon
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, "((0,0),(1,1),(1,0))",
						new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, "(0,0),(1,1),(1,0)",
						new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, "(0,0,1,1,1,0)",
						new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, "0,0,1,1,1,0", new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, "((0,0))", new Polygon(0, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Polygon3D::new, "((0,0,0),(1,1,1),(1,0,0))",
						new Polygon3D(0, 0, 0, 1, 1, 1, 1, 0, 0)),
				// circle
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "<(0,0),1>", new Circle(0, 0, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "((0,0),1)", new Circle(0, 0, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "(0,0),1", new Circle(0, 0, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "0,0,1", new Circle(0, 0, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, " < ( 1 , 2 ) , 3 > ", new Circle(1, 2, 3)),
				// 既存実装が受理していた形式(PostgreSQLは受理しない)
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "<0,0,1>", new Circle(0, 0, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "(0,0,1)", new Circle(0, 0, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Circle3D::new, "<(1,2,3),4>", new Circle3D(1, 2, 3, 4)),
				Arguments.of((Supplier<AbstractGeometry>) Circle3D::new, "1,2,3,4", new Circle3D(1, 2, 3, 4)));
	}

	@ParameterizedTest(name = "[{index}] {1}")
	@MethodSource("accepted")
	public void testAccepted(final Supplier<AbstractGeometry> factory, final String text,
			final AbstractGeometry expected) {
		assertEquals(expected, factory.get().setValue(text));
	}

	static Stream<Arguments> rejected() {
		return Stream.of(
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "[1,2]"),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "(1,2,3)"),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "(1,2)x"),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "(1,2"),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "1"),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "a,b"),
				Arguments.of((Supplier<AbstractGeometry>) Point::new, "1,,2"),
				Arguments.of((Supplier<AbstractGeometry>) Point3D::new, "(1,2)"),
				Arguments.of((Supplier<AbstractGeometry>) Point3D::new, "(1,2,3,4)"),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "[(0,0),(1,1),(2,2)]"),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "[(0,0)]"),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, "((0,0),(1,1)"),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "[(0,0),(1,1)]"),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "(0,0),(1,1),(2,2)"),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, "0,0,1"),
				Arguments.of((Supplier<AbstractGeometry>) Box3D::new, "((0,0),(1,1))"),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "{0,0,3}"),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "((0,0),(0,0))"),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "{1,2}"),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "{1,2,3,4}"),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, "{1,2,3"),
				Arguments.of((Supplier<AbstractGeometry>) Line3D::new, "{1,2,3}"),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "((0,0),(1,1)"),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "[(0,0),(1,1))"),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "0,0,1"),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "[]"),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, "[(0,0),(1,1)]x"),
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, "[(0,0),(1,1)]"),
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, "0,0,1"),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "<(0,0),1)"),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "<(0,0)>"),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, "<(0,0),1,2>"),
				Arguments.of((Supplier<AbstractGeometry>) Circle3D::new, "<(0,0),1>"));
	}

	@ParameterizedTest(name = "[{index}] {1}")
	@MethodSource("rejected")
	public void testRejected(final Supplier<AbstractGeometry> factory, final String text) {
		assertThrows(IllegalArgumentException.class, () -> factory.get().setValue(text));
	}

	// ---- PostgreSQL の line 係数表現 {A,B,C} ----

	@Test
	public void testLineFromCoefficients() {
		assertOnLine(new Line().setValue("{1,-1,0}"), 1, -1, 0);
		assertOnLine(new Line().setValue("{1,2,3}"), 1, 2, 3);
		assertOnLine(new Line().setValue(" { 2 , 0 , -4 } "), 2, 0, -4);
		assertOnLine(new Line().setValue("{0,5,10}"), 0, 5, 10);
		// B≠0 のときは x=0, x=1 の2点を使う
		assertEquals(new Line(0, 0, 1, 1), new Line().setValue("{1,-1,0}"));
		// B=0 のとき(垂直線)は y=0, y=1 の2点を使う
		assertEquals(new Line(2, 0, 2, 1), new Line().setValue("{2,0,-4}"));
	}

	@Test
	public void testLineFromCoefficientsFactory() {
		assertEquals(new Line(0, 0, 1, 1), Line.fromCoefficients(1, -1, 0));
		// -0.0 は 0.0 にそろえる
		assertEquals(new Line(0, 0, 1, 0), Line.fromCoefficients(0, 5, 0));
		assertEquals(new Line(0, 0, 0, 1), Line.fromCoefficients(-3, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> Line.fromCoefficients(0, 0, 1));
	}

	private static void assertOnLine(final Line line, final double a, final double b, final double c) {
		final Point p1 = line.getPoints()[0];
		final Point p2 = line.getPoints()[1];
		assertNotEquals(p1, p2);
		assertEquals(0d, a * p1.getX() + b * p1.getY() + c, 1e-12);
		assertEquals(0d, a * p2.getX() + b * p2.getY() + c, 1e-12);
	}

	// ---- 自身の出力の読み戻し ----

	static Stream<Arguments> roundTrip() {
		return Stream.of(
				Arguments.of((Supplier<AbstractGeometry>) Point::new, new Point(1.5, -2)),
				Arguments.of((Supplier<AbstractGeometry>) Point3D::new, new Point3D(1.5, -2, 3)),
				Arguments.of((Supplier<AbstractGeometry>) Circle::new, new Circle(1, 2, 3.5)),
				Arguments.of((Supplier<AbstractGeometry>) Circle3D::new, new Circle3D(1, 2, 3, 4.5)),
				Arguments.of((Supplier<AbstractGeometry>) Box::new, new Box(1, 1, 0, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Box3D::new, new Box3D(0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg::new, new Lseg(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Lseg3D::new, new Lseg3D(0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Line::new, new Line(0, 0, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Line3D::new, new Line3D(0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, new Path(true, 0, 0, 1, 1, 2, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, new Path(false, 0, 0, 1, 1, 2, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Path::new, new Path(true, 0, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Path3D::new, new Path3D(true, 0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Path3D::new, new Path3D(false, 0, 0, 0, 1, 1, 1)),
				Arguments.of((Supplier<AbstractGeometry>) Polygon::new, new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Polygon3D::new,
						new Polygon3D(0, 0, 0, 1, 1, 1, 1, 0, 0)),
				Arguments.of((Supplier<AbstractGeometry>) Point::new,
						new Point(Double.NaN, Double.POSITIVE_INFINITY)));
	}

	@ParameterizedTest(name = "[{index}] {1}")
	@MethodSource("roundTrip")
	public void testRoundTrip(final Supplier<AbstractGeometry> factory, final AbstractGeometry value) {
		assertEquals(value, factory.get().setValue(value.toString()));
	}

	@Test
	public void testPathOpenStateFollowsText() {
		final Path path = new Path(true, 0, 0, 1, 1);
		path.setValue("((0,0),(1,1))");
		assertFalse(path.open);
		path.setValue("[(0,0),(1,1)]");
		assertTrue(path.open);
	}

	@Test
	public void testFailedParseDoesNotModify() {
		final Path path = new Path(true, 0, 0, 1, 1);
		assertThrows(IllegalArgumentException.class, () -> path.setValue("((5,5),(6,6)"));
		assertEquals(new Path(true, 0, 0, 1, 1), path);
		final Point point = new Point(1, 2);
		assertThrows(IllegalArgumentException.class, () -> point.setValue("(9,9)x"));
		assertEquals(new Point(1, 2), point);
	}
}
