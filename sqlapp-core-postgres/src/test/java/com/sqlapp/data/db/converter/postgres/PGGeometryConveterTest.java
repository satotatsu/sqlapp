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
package com.sqlapp.data.db.converter.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.geometric.PGbox;
import org.postgresql.geometric.PGcircle;
import org.postgresql.geometric.PGline;
import org.postgresql.geometric.PGlseg;
import org.postgresql.geometric.PGpath;
import org.postgresql.geometric.PGpoint;
import org.postgresql.geometric.PGpolygon;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGBoxConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGCircleConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGLineConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGLsegConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGPathConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGPointConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGPolygonConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGBoxConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGCircleConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGLineConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGLsegConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGPathConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGPointConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGPolygonConverter;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;

/**
 * PostgreSQL 組み込み幾何型の Converter の回帰テスト。
 *
 * 文字列は PostgreSQL 16 の出力形式をそのまま使用している(実DBには接続しない)。
 */
public class PGGeometryConveterTest {

	static Stream<Arguments> converters() {
		return Stream.of(
				Arguments.of(new Point(1.5, -2), new ToPGPointConverter(), new FromPGPointConverter()),
				Arguments.of(new Box(1, 1, 0, 0), new ToPGBoxConverter(), new FromPGBoxConverter()),
				Arguments.of(new Lseg(0, 0, 1, 1), new ToPGLsegConverter(), new FromPGLsegConverter()),
				Arguments.of(new Path(true, 0, 0, 1, 1, 2, 0), new ToPGPathConverter(), new FromPGPathConverter()),
				Arguments.of(new Path(false, 0, 0, 1, 1, 2, 0), new ToPGPathConverter(), new FromPGPathConverter()),
				Arguments.of(new Polygon(0, 0, 1, 1, 1, 0), new ToPGPolygonConverter(), new FromPGPolygonConverter()),
				Arguments.of(new Circle(1, 2, 3), new ToPGCircleConverter(), new FromPGCircleConverter()));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("converters")
	public void testRoundTrip(final Object value, final Converter<?> to, final Converter<?> from) {
		final Object pg = to.convertObject(value);
		assertEquals(value, from.convertObject(pg));
	}

	@Test
	public void testLineRoundTripKeepsLine() {
		// 2点 → PGline(係数) → 2点 で、直線としての意味が保たれる
		final Line line = new Line(1, 2, 3, 6);
		final PGline pg = new ToPGLineConverter().convertObject(line);
		final Line back = new FromPGLineConverter().convertObject(pg);
		for (final Point p : back.getPoints()) {
			assertEquals(0d, pg.a * p.getX() + pg.b * p.getY() + pg.c, 1e-12);
		}
		for (final Point p : line.getPoints()) {
			assertEquals(0d, pg.a * p.getX() + pg.b * p.getY() + pg.c, 1e-12);
		}
	}

	static Stream<Arguments> postgresOutput() {
		return Stream.of(
				Arguments.of(new FromPGPointConverter(), "(1,2)", new Point(1, 2)),
				Arguments.of(new FromPGBoxConverter(), "(1,1),(0,0)", new Box(1, 1, 0, 0)),
				Arguments.of(new FromPGLsegConverter(), "[(0,0),(1,1)]", new Lseg(0, 0, 1, 1)),
				Arguments.of(new FromPGLineConverter(), "{1,-1,0}", new Line(0, 0, 1, 1)),
				Arguments.of(new FromPGLineConverter(), "{2,0,-4}", new Line(2, 0, 2, 1)),
				Arguments.of(new FromPGPathConverter(), "[(0,0),(1,1)]", new Path(true, 0, 0, 1, 1)),
				Arguments.of(new FromPGPathConverter(), "((0,0),(1,1))", new Path(false, 0, 0, 1, 1)),
				Arguments.of(new FromPGPolygonConverter(), "((0,0),(1,1),(1,0))", new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of(new FromPGCircleConverter(), "<(0,0),1>", new Circle(0, 0, 1)));
	}

	@ParameterizedTest(name = "[{index}] {1}")
	@MethodSource("postgresOutput")
	public void testFromPostgresOutput(final Converter<?> from, final String text, final Object expected) {
		assertEquals(expected, from.convertObject(text));
	}

	@Test
	public void testFromPGObjects() throws Exception {
		assertEquals(new Point(1, 2), new FromPGPointConverter().convertObject(new PGpoint("(1,2)")));
		assertEquals(new Box(1, 1, 0, 0), new FromPGBoxConverter().convertObject(new PGbox("(1,1),(0,0)")));
		assertEquals(new Lseg(0, 0, 1, 1), new FromPGLsegConverter().convertObject(new PGlseg("[(0,0),(1,1)]")));
		assertEquals(new Line(0, 0, 1, 1), new FromPGLineConverter().convertObject(new PGline("{1,-1,0}")));
		assertEquals(new Path(true, 0, 0, 1, 1), new FromPGPathConverter().convertObject(new PGpath("[(0,0),(1,1)]")));
		assertEquals(new Path(false, 0, 0, 1, 1),
				new FromPGPathConverter().convertObject(new PGpath("((0,0),(1,1))")));
		assertEquals(new Polygon(0, 0, 1, 1, 1, 0),
				new FromPGPolygonConverter().convertObject(new PGpolygon("((0,0),(1,1),(1,0))")));
		assertEquals(new Circle(0, 0, 1), new FromPGCircleConverter().convertObject(new PGcircle("<(0,0),1>")));
	}

	@Test
	public void testToPGLineFromText() {
		// pgjdbc の PGline(String) は2点形式の係数計算が誤っているため、文字列からの変換でも直線を保つことを確認する
		for (final String text : new String[] { "((0,0),(1,1))", "[(1,2),(3,6)]", "0,1,1,0", "{1,-1,0}" }) {
			final PGline pg = new ToPGLineConverter().convertObject(text);
			for (final Point p : new Line().setValue(text).getPoints()) {
				assertEquals(0d, pg.a * p.getX() + pg.b * p.getY() + pg.c, 1e-12, text);
			}
		}
	}

	@Test
	public void testToPGTypes() {
		assertInstanceOf(PGpath.class, new ToPGPathConverter().convertObject(new Path(true, 0, 0, 1, 1)));
		final PGpath open = new ToPGPathConverter().convertObject(new Path(true, 0, 0, 1, 1));
		assertEquals(true, open.isOpen());
		final PGpath closed = new ToPGPathConverter().convertObject(new Path(false, 0, 0, 1, 1));
		assertEquals(false, closed.isOpen());
	}

	@Test
	public void testNull() {
		assertNull(new FromPGLineConverter().convertObject(null));
		assertNull(new FromPGPathConverter().convertObject(null));
		assertNull(new FromPGPathConverter().convertObject(new PGpath()));
	}
}
