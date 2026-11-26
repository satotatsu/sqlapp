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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;

/**
 * 幾何型の文字列Converterの回帰テスト。
 *
 * PostgreSQL の出力形式は PostgreSQL 16 の出力(COPY / psql と同じテキスト表現)をそのまま使用している。
 */
public class GeometryConverterTest {

	static Stream<Arguments> postgresOutput() {
		return Stream.of(Arguments.of(Point.class, "(1,2)", new Point(1, 2)),
				Arguments.of(Point.class, "(100,-Infinity)", new Point(100, Double.NEGATIVE_INFINITY)),
				Arguments.of(Box.class, "(1,1),(0,0)", new Box(1, 1, 0, 0)),
				Arguments.of(Lseg.class, "[(0,0),(1,1)]", new Lseg(0, 0, 1, 1)),
				Arguments.of(Line.class, "{1,-1,0}", new Line(0, 0, 1, 1)),
				Arguments.of(Path.class, "[(0,0),(1,1)]", new Path(true, 0, 0, 1, 1)),
				Arguments.of(Path.class, "((0,0),(1,1))", new Path(false, 0, 0, 1, 1)),
				Arguments.of(Polygon.class, "((0,0),(1,1),(1,0))", new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of(Circle.class, "<(0,0),1>", new Circle(0, 0, 1)));
	}

	@ParameterizedTest(name = "[{index}] {0} {1}")
	@MethodSource("postgresOutput")
	public void testConvertPostgresOutput(final Class<?> type, final String text, final Object expected) {
		assertEquals(expected, converter(type).convertObject(text));
	}

	static Stream<Arguments> values() {
		return Stream.of(Arguments.of(Point.class, new Point(1.5, -2)), Arguments.of(Box.class, new Box(1, 1, 0, 0)),
				Arguments.of(Lseg.class, new Lseg(0, 0, 1, 1)), Arguments.of(Line.class, new Line(0, 0, 1, 1)),
				Arguments.of(Path.class, new Path(true, 0, 0, 1, 1)),
				Arguments.of(Path.class, new Path(false, 0, 0, 1, 1)),
				Arguments.of(Polygon.class, new Polygon(0, 0, 1, 1, 1, 0)),
				Arguments.of(Circle.class, new Circle(0, 0, 1)));
	}

	@ParameterizedTest(name = "[{index}] {1}")
	@MethodSource("values")
	public void testFormatAndConvert(final Class<?> type, final Object value) {
		final Converter<Object> converter = converter(type);
		final String text = converter.format(value);
		assertEquals(value, converter.convertObject(text));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("values")
	public void testEmptyIsNull(final Class<?> type, final Object value) {
		final Converter<Object> converter = converter(type);
		assertNull(converter.convertObject(null));
		assertNull(converter.convertObject(""));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Converter<Object> converter(final Class<?> type) {
		return (Converter<Object>) Converters.getDefault().getConverter((Class) type);
	}
}
