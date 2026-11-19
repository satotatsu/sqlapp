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
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.GeometryType;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.file.TextFileWriter;

/**
 * 汎用 GEOMETRY 型の CSV 入出力の回帰テスト。
 *
 * 書き込みは ExportDataCommand と同じく {@link DataFormat#createCsvListWriter} と {@link Column#getFormatter()}
 * を使い、読み込みは ImportDataCommand と同じく {@link FileRowIteratorFactory} を使う。
 */
public class GeometryWktCsvRoundTripTest {

	@TempDir
	File directory;

	private static Table newTable() {
		final Table table = new Table("GEOMETRIES");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		table.getColumns().add(new Column("G").setDataType(DataType.GEOMETRY));
		return table;
	}

	private static List<Row> read(final File file) {
		final Table table = newTable();
		table.setRowIteratorHandler(FileRowIteratorFactory.create(file, "UTF-8", 1, 1, null, null, null,
				(r, c, v) -> v));
		final List<Row> rows = new ArrayList<>();
		for (final Row row : table.getRows()) {
			rows.add(row);
		}
		return rows;
	}

	private static final String[] SOURCES = { "SRID=4326;POINT(139 35)", "SRID=4326;POINT Z(139 35 10)",
			"POINT M(1 2 3)", "POINT ZM(1 2 3 4)", "POINT EMPTY", "SRID=4326;LINESTRING(0 0,1 1)",
			"POLYGON((0 0,1 0,1 1,0 0))", "MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((5 5,6 5,6 6,5 5)))",
			"GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,1 1))", null };

	@Test
	public void testRoundTrip() throws Exception {
		final Table table = newTable();
		for (int i = 0; i < SOURCES.length; i++) {
			final Row row = table.newRow();
			row.put(table.getColumns().get("ID"), i);
			row.put(table.getColumns().get("G"), SOURCES[i]);
			table.getRows().add(row);
		}
		final File file = new File(directory, "geometries.csv");
		try (TextFileWriter writer = DataFormat.CSV.createCsvListWriter(file, "UTF-8")) {
			writer.writeHeader("ID", "G");
			for (final Row row : table.getRows()) {
				writer.writeRow(table.getColumns().get("ID").getFormatter().format(row.get("ID")),
						table.getColumns().get("G").getFormatter().format(row.get("G")));
			}
		}
		final String csv = Files.readString(file.toPath(), StandardCharsets.UTF_8);
		// 出力形式(PostGIS EWKT)と、カンマを含む値の引用
		assertTrue(csv.contains("0,SRID=4326;POINT(139 35)\n") || csv.contains("0,SRID=4326;POINT(139 35)\r\n"),
				csv);
		assertTrue(csv.contains("\"SRID=4326;LINESTRING(0 0,1 1)\""), csv);
		assertTrue(csv.contains("POINT EMPTY"), csv);

		final List<Row> rows = read(file);
		assertEquals(SOURCES.length, rows.size());
		assertGeometry(rows.get(0).get("G"), GeometryType.POINT, 4326, false, false, false, new double[] { 139, 35 });
		assertGeometry(rows.get(1).get("G"), GeometryType.POINT, 4326, true, false, false,
				new double[] { 139, 35, 10 });
		assertGeometry(rows.get(2).get("G"), GeometryType.POINT, noSrid(), false, true, false,
				new double[] { 1, 2, 3 });
		assertGeometry(rows.get(3).get("G"), GeometryType.POINT, noSrid(), true, true, false,
				new double[] { 1, 2, 3, 4 });
		// EMPTY は null と区別される
		assertGeometry(rows.get(4).get("G"), GeometryType.POINT, noSrid(), false, false, true);
		assertGeometry(rows.get(5).get("G"), GeometryType.LINESTRING, 4326, false, false, false,
				new double[] { 0, 0 }, new double[] { 1, 1 });
		assertGeometry(rows.get(6).get("G"), GeometryType.POLYGON, noSrid(), false, false, false,
				new double[] { 0, 0 }, new double[] { 1, 0 }, new double[] { 1, 1 }, new double[] { 0, 0 });
		assertEquals(GeometryType.MULTIPOLYGON, ((Geometry<?>) rows.get(7).get("G")).getGeometryType());
		assertEquals(8, ((Geometry<?>) rows.get(7).get("G")).getNumPositions());
		assertGeometry(rows.get(8).get("G"), GeometryType.GEOMETRYCOLLECTION, noSrid(), false, false, false,
				new double[] { 1, 2 }, new double[] { 0, 0 }, new double[] { 1, 1 });
		// SQL の NULL
		assertNull(rows.get(9).get("G"));
	}

	/**
	 * PostgreSQL + PostGIS の COPY ... CSV 出力(16進EWKB)を読み込めること。
	 * 値は Claude 側の検証環境(PostgreSQL 16 + PostGIS)で取得したもの。
	 */
	@Test
	public void testReadPostgisCopyCsv() throws Exception {
		final File file = new File(directory, "copy.csv");
		Files.writeString(file.toPath(), String.join("\n", "ID,G",
				"1,0101000020E610000000000000006061400000000000804140",
				"2,01010000A0E6100000000000000060614000000000008041400000000000002440",
				"3,0101000040000000000000F03F00000000000000400000000000000840",
				"4,0101000000000000000000F87F000000000000F87F", "5,", ""), StandardCharsets.UTF_8);
		final List<Row> rows = read(file);
		assertEquals(5, rows.size());
		assertGeometry(rows.get(0).get("G"), GeometryType.POINT, 4326, false, false, false, new double[] { 139, 35 });
		assertGeometry(rows.get(1).get("G"), GeometryType.POINT, 4326, true, false, false,
				new double[] { 139, 35, 10 });
		assertGeometry(rows.get(2).get("G"), GeometryType.POINT, noSrid(), false, true, false,
				new double[] { 1, 2, 3 });
		assertGeometry(rows.get(3).get("G"), GeometryType.POINT, noSrid(), false, false, true);
		assertNull(rows.get(4).get("G"));
	}

	/**
	 * 壊れた値はエラーになり、黙って読み飛ばしたり文字列のまま通したりしない。
	 */
	@Test
	public void testRejectBrokenValue() throws Exception {
		final File file = new File(directory, "broken.csv");
		Files.writeString(file.toPath(), "ID,G\n1,\"POINT(1 2) junk\"\n", StandardCharsets.UTF_8);
		assertThrows(RuntimeException.class, () -> read(file));
	}

	private static int noSrid() {
		return Wkt.fromWkt("POINT(0 0)").getSRID();
	}

	@Test
	public void testEmptyCoordinateDimensionsThroughCsv() throws Exception {
		final Table table = newTable();
		final String[] sources = { "SRID=4326;POINT Z EMPTY", "POINT M EMPTY", "POINT ZM EMPTY",
				"LINESTRING ZM EMPTY", "POLYGON Z EMPTY", "MULTIPOINT Z EMPTY",
				"GEOMETRYCOLLECTION Z(POINT Z EMPTY,POINT Z(1 2 3))" };
		final List<Geometry<?>> expected = new ArrayList<>();
		final File file = new File(directory, "empty-dimensions.csv");
		try (TextFileWriter writer = DataFormat.CSV.createCsvListWriter(file, "UTF-8")) {
			writer.writeHeader("ID", "G");
			for (int i = 0; i < sources.length; i++) {
				final Row row = table.newRow();
				row.put(table.getColumns().get("G"), sources[i]);
				final Geometry<?> geometry = row.get("G");
				expected.add(geometry);
				writer.writeRow(Integer.toString(i), table.getColumns().get("G").getFormatter().format(geometry));
			}
		}
		final List<Row> rows = read(file);
		assertEquals(sources.length, rows.size());
		for (int i = 0; i < sources.length; i++) {
			final Geometry<?> before = expected.get(i);
			final Geometry<?> after = rows.get(i).get("G");
			assertEquals(before.hasZ(), after.hasZ(), sources[i]);
			assertEquals(before.hasM(), after.hasM(), sources[i]);
			assertEquals(before.getSRID(), after.getSRID(), sources[i]);
			assertEquals(before.isEmpty(), after.isEmpty(), sources[i]);
			assertEquals(before, after, sources[i]);
		}
	}

	private static void assertGeometry(final Object value, final GeometryType type, final int srid, final boolean z,
			final boolean m, final boolean empty, final double[]... coordinates) {
		final Geometry<?> geometry = assertInstanceOf(Geometry.class, value);
		assertEquals(type, geometry.getGeometryType(), "type");
		assertEquals(srid, geometry.getSRID(), "srid");
		assertEquals(z, geometry.hasZ(), "hasZ");
		assertEquals(m, geometry.hasM(), "hasM");
		assertEquals(empty, geometry.isEmpty(), "isEmpty");
		assertEquals(coordinates.length, geometry.getNumPositions(), "number of positions");
		for (int i = 0; i < coordinates.length; i++) {
			assertArrayEquals(coordinates[i], geometry.getPositionN(i).toArray(null), "position " + i);
		}
	}
}
