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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.GeometryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

/**
 * 汎用 GEOMETRY 型の文字列変換({@link GeometryConverter})の回帰テスト。
 *
 * 往復結果の equals だけでは、出力と再読込の双方で同じ情報を失う問題を検出できないため、 図形の種類・SRID・Z/M・空状態・各座標値を直接確認する。
 * 16進EWKB の入力値は PostgreSQL 16 + PostGIS の COPY ... CSV が出力した値(Claude 側の検証環境で取得)。
 */
public class GeometryConverterWktTest {

	/** SRID を持たない図形の SRID 値 */
	private static final int NO_SRID = converter().convertObject("POINT(0 0)").getSRID();

	private static GeometryConverter converter() {
		return new GeometryConverter();
	}

	/**
	 * 期待値
	 */
	private static Arguments expect(final String text, final GeometryType type, final int srid, final boolean z,
			final boolean m, final boolean empty, final double[]... coordinates) {
		return Arguments.of(text, type, srid, z, m, empty, coordinates);
	}

	private static final double[][] NONE = new double[0][];

	static Stream<Arguments> accepted() {
		final int none = Integer.MIN_VALUE; // NO_SRID を使う印
		return Stream.of(
				expect("POINT(139 35)", GeometryType.POINT, none, false, false, false, new double[] { 139, 35 }),
				expect("SRID=4326;POINT(139 35)", GeometryType.POINT, 4326, false, false, false,
						new double[] { 139, 35 }),
				expect("SRID=4326;POINT Z(139 35 10)", GeometryType.POINT, 4326, true, false, false,
						new double[] { 139, 35, 10 }),
				expect("SRID=4326;POINT(139 35 10)", GeometryType.POINT, 4326, true, false, false,
						new double[] { 139, 35, 10 }),
				expect("POINT M(1 2 3)", GeometryType.POINT, none, false, true, false, new double[] { 1, 2, 3 }),
				expect("POINTM(1 2 3)", GeometryType.POINT, none, false, true, false, new double[] { 1, 2, 3 }),
				expect("POINT ZM(1 2 3 4)", GeometryType.POINT, none, true, true, false, new double[] { 1, 2, 3, 4 }),
				expect("POINT(1 2 3 4)", GeometryType.POINT, none, true, true, false, new double[] { 1, 2, 3, 4 }),
				expect("SRID=0;POINT(1 2)", GeometryType.POINT, none, false, false, false, new double[] { 1, 2 }),
				expect("  point ( 1.5 -2.25 )  ", GeometryType.POINT, none, false, false, false,
						new double[] { 1.5, -2.25 }),
				expect("POINT(1e-7 2E3)", GeometryType.POINT, none, false, false, false, new double[] { 1e-7, 2000 }),
				expect("POINT EMPTY", GeometryType.POINT, none, false, false, true, NONE),
				expect("LINESTRING EMPTY", GeometryType.LINESTRING, none, false, false, true, NONE),
				expect("SRID=4326;LINESTRING(0 0,1 1)", GeometryType.LINESTRING, 4326, false, false, false,
						new double[] { 0, 0 }, new double[] { 1, 1 }),
				expect("LINESTRING Z(0 0 1,1 1 2)", GeometryType.LINESTRING, none, true, false, false,
						new double[] { 0, 0, 1 }, new double[] { 1, 1, 2 }),
				expect("SRID=3857;POLYGON((0 0,1 0,1 1,0 0))", GeometryType.POLYGON, 3857, false, false, false,
						new double[] { 0, 0 }, new double[] { 1, 0 }, new double[] { 1, 1 }, new double[] { 0, 0 }),
				expect("MULTIPOINT((0 0),(1 1))", GeometryType.MULTIPOINT, none, false, false, false,
						new double[] { 0, 0 }, new double[] { 1, 1 }),
				expect("MULTIPOINT(0 0,1 1)", GeometryType.MULTIPOINT, none, false, false, false, new double[] { 0, 0 },
						new double[] { 1, 1 }),
				expect("MULTILINESTRING((0 0,1 1),(2 2,3 3))", GeometryType.MULTILINESTRING, none, false, false, false,
						new double[] { 0, 0 }, new double[] { 1, 1 }, new double[] { 2, 2 }, new double[] { 3, 3 }),
				expect("MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((5 5,6 5,6 6,5 5)))", GeometryType.MULTIPOLYGON, none, false,
						false, false, new double[] { 0, 0 }, new double[] { 1, 0 }, new double[] { 1, 1 },
						new double[] { 0, 0 }, new double[] { 5, 5 }, new double[] { 6, 5 }, new double[] { 6, 6 },
						new double[] { 5, 5 }),
				expect("GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,1 1))", GeometryType.GEOMETRYCOLLECTION, none,
						false, false, false, new double[] { 1, 2 }, new double[] { 0, 0 }, new double[] { 1, 1 }),
				expect("GEOMETRYCOLLECTION EMPTY", GeometryType.GEOMETRYCOLLECTION, none, false, false, true, NONE),
				// 16進EWKB(PostGIS の COPY 出力)
				expect("0101000020E610000000000000006061400000000000804140", GeometryType.POINT, 4326, false, false,
						false, new double[] { 139, 35 }),
				expect("0101000020e610000000000000006061400000000000804140", GeometryType.POINT, 4326, false, false,
						false, new double[] { 139, 35 }),
				expect("01010000A0E6100000000000000060614000000000008041400000000000002440", GeometryType.POINT, 4326,
						true, false, false, new double[] { 139, 35, 10 }),
				expect("0101000040000000000000F03F00000000000000400000000000000840", GeometryType.POINT, none, false,
						true, false, new double[] { 1, 2, 3 }),
				expect("0101000000000000000000F87F000000000000F87F", GeometryType.POINT, none, false, false, true,
						NONE),
				expect("0102000020E61000000200000000000000000000000000000000000000000000000000F03F000000000000F03F",
						GeometryType.LINESTRING, 4326, false, false, false, new double[] { 0, 0 }, new double[] { 1, 1 }),
				// ビッグエンディアン(XDR)
				expect("0020000002000010E600000002000000000000000000000000000000003FF00000000000003FF0000000000000",
						GeometryType.LINESTRING, 4326, false, false, false, new double[] { 0, 0 }, new double[] { 1, 1 }),
				expect("0106000000020000000103000000010000000400000000000000000000000000000000000000000000000000F03F"
						+ "0000000000000000000000000000F03F000000000000F03F00000000000000000000000000000000"
						+ "0103000000010000000400000000000000000014400000000000001440000000000000184000000000000014400000"
						+ "000000001840000000000000184000000000000014400000000000001440", GeometryType.MULTIPOLYGON, none,
						false, false, false, new double[] { 0, 0 }, new double[] { 1, 0 }, new double[] { 1, 1 },
						new double[] { 0, 0 }, new double[] { 5, 5 }, new double[] { 6, 5 }, new double[] { 6, 6 },
						new double[] { 5, 5 }),
				expect("0107000000020000000101000000000000000000F03F000000000000004001020000000200000000000000000000"
						+ "000000000000000000000000000000F03F000000000000F03F", GeometryType.GEOMETRYCOLLECTION, none,
						false, false, false, new double[] { 1, 2 }, new double[] { 0, 0 }, new double[] { 1, 1 }),
				expect("01010000C0000000000000F03F000000000000004000000000000008400000000000001040", GeometryType.POINT,
						none, true, true, false, new double[] { 1, 2, 3, 4 }),
				expect("010700000000000000", GeometryType.GEOMETRYCOLLECTION, none, false, false, true, NONE));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("accepted")
	public void testParse(final String text, final GeometryType type, final int srid, final boolean z,
			final boolean m, final boolean empty, final double[][] coordinates) {
		final Geometry<?> geometry = converter().convertObject(text);
		assertGeometry(geometry, type, srid, z, m, empty, coordinates);
	}

	/**
	 * 出力した文字列を再読込しても、種類・SRID・Z/M・空状態・座標が保たれること。
	 */
	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("accepted")
	public void testFormatAndParse(final String text, final GeometryType type, final int srid, final boolean z,
			final boolean m, final boolean empty, final double[][] coordinates) {
		final GeometryConverter converter = converter();
		final String formatted = converter.format(converter.convertObject(text));
		assertGeometry(converter.convertObject(formatted), type, srid, z, m, empty, coordinates);
	}

	private static void assertGeometry(final Geometry<?> geometry, final GeometryType type, final int srid,
			final boolean z, final boolean m, final boolean empty, final double[][] coordinates) {
		assertNotNull(geometry);
		assertEquals(type, geometry.getGeometryType(), "type");
		assertEquals(srid == Integer.MIN_VALUE ? NO_SRID : srid, geometry.getSRID(), "srid");
		assertEquals(z, geometry.hasZ(), "hasZ");
		assertEquals(m, geometry.hasM(), "hasM");
		assertEquals(empty, geometry.isEmpty(), "isEmpty");
		assertEquals(coordinates.length, geometry.getNumPositions(), "number of positions");
		for (int i = 0; i < coordinates.length; i++) {
			assertArrayEquals(coordinates[i], geometry.getPositionN(i).toArray(null), "position " + i);
		}
	}

	/**
	 * 出力形式(PostGIS EWKT)を仕様として固定する。
	 */
	@Test
	public void testFormat() {
		final GeometryConverter converter = converter();
		assertEquals("POINT(139 35)", converter.format(converter.convertObject("POINT(139 35)")));
		assertEquals("SRID=4326;POINT(139 35)", converter.format(converter.convertObject("SRID=4326;POINT(139 35)")));
		assertEquals("SRID=4326;POINT(139 35 10)",
				converter.format(converter.convertObject("SRID=4326;POINT Z(139 35 10)")));
		assertEquals("POINTM(1 2 3)", converter.format(converter.convertObject("POINT M(1 2 3)")));
		assertEquals("POINT(1 2 3 4)", converter.format(converter.convertObject("POINT ZM(1 2 3 4)")));
		assertEquals("POINT(1 2)", converter.format(converter.convertObject("SRID=0;POINT(1 2)")));
		assertEquals("POINT EMPTY", converter.format(converter.convertObject("POINT EMPTY")));
		assertEquals("SRID=4326;LINESTRING(0 0,1 1)",
				converter.format(converter.convertObject("SRID=4326;LINESTRING(0 0,1 1)")));
		assertEquals("SRID=4326;POINT(139 35)",
				converter.format(converter.convertObject("0101000020E610000000000000006061400000000000804140")));
		assertNull(converter.format(null));
	}

	@ParameterizedTest(name = "[{index}] \"{0}\"")
	@ValueSource(strings = { "POINT(1 2) junk", "POINT(1 2))", "POINT(1 2", "POINT(1)", "POINT Z(1 2)",
			"POINT ZM(1 2 3)", "POINTM(1 2)", "LINESTRING(0 0,1 1 1)", "POINT(1 2 3 4 5)", "SRID=x;POINT(1 2)",
			"SRID=4326POINT(1 2)", "SRID=4326;", "FOO(1 2)", "POINT(1,2)", "POINT(1 2);", "   ", "POINT(a b)",
			"GEOMETRYCOLLECTION(POINT(1 2)",
			// 16進EWKB: 奇数桁 / 末尾の余分なバイト / 途中で切れたデータ / 不正なバイト順
			"0101000020E61000000000000000606140000000000080414",
			"0101000020E61000000000000000606140000000000080414000",
			"0101000020E6100000000000000060614000000000008041",
			"0201000020E610000000000000006061400000000000804140",
			// 構成要素の途中で切れたコレクション / 末尾に余分なデータがあるコレクション
			"0107000000020000000101000000000000000000F03F0000000000000040",
			"01070000000000000000" })
	public void testRejected(final String text) {
		assertThrows(IllegalArgumentException.class, () -> converter().convertObject(text));
	}

	@Test
	public void testNullAndEmpty() {
		assertNull(converter().convertObject(null));
		assertNull(converter().convertObject(""));
	}

	/**
	 * GEOMETRY 列の変換に GeometryConverter が使われること。
	 */
	@Test
	public void testRegistration() {
		assertInstanceOf(GeometryConverter.class, Converters.getDefault().getConverter(Geometry.class));
		final Column column = new Column("G").setDataType(DataType.GEOMETRY);
		assertInstanceOf(GeometryConverter.class, column.getConverter());
		assertInstanceOf(GeometryConverter.class, column.getFormatter());
		assertInstanceOf(Geometry.class, column.getConverter().convertObject("SRID=4326;POINT(1 2)"));
		final Geometry<?> geometry = Converters.getDefault().convertObject("SRID=4326;POINT(1 2)", Geometry.class);
		assertEquals(4326, geometry.getSRID());
	}
}
