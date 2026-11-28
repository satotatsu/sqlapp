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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.file.TextFileWriter;

/**
 * 幾何型の CSV 入出力の回帰テスト。
 *
 * 書き込みは ExportDataCommand / ConvertDataCommand と同じく
 * {@link DataFormat#createCsvListWriter} と {@link Column#getFormatter()}
 * を使い、読み込みは ImportDataCommand と同じく {@link FileRowIteratorFactory} を使う。
 */
public class GeometryCsvRoundTripTest {

	@TempDir
	File directory;

	private static final DataType[] TYPES = { DataType.POINT, DataType.BOX, DataType.LSEG, DataType.LINE, DataType.PATH,
			DataType.POLYGON, DataType.CIRCLE };

	private static Table newTable() {
		final Table table = new Table("GEOMETRIES");
		table.getColumns().add(new Column("ID").setDataType(DataType.INT));
		for (final DataType type : TYPES) {
			table.getColumns().add(new Column(type.name()).setDataType(type));
		}
		return table;
	}

	private static Object[] row(final int id, final Object... values) {
		final Object[] ret = new Object[values.length + 1];
		ret[0] = id;
		System.arraycopy(values, 0, ret, 1, values.length);
		return ret;
	}

	private static final List<Object[]> ROWS = List.of(
			row(1, new Point(1.5, -2), new Box(1, 1, 0, 0), new Lseg(0, 0, 1, 1), new Line(0, 0, 1, 1),
					new Path(true, 0, 0, 1, 1, 2, 0), new Polygon(0, 0, 1, 1, 1, 0), new Circle(1, 2, 3)),
			row(2, new Point(0, 0), new Box(0, 0, 0, 0), new Lseg(-1, -1, 1, 1), new Line(0, 0, 0, 1),
					new Path(false, 0, 0, 1, 1, 2, 0), new Polygon(0, 0), new Circle(0, 0, 0)),
			row(3, null, null, null, null, null, null, null),
			row(4, new Point(1e10, -1e-10), null, null, null, new Path(true, 0, 0), null, null));

	private void write(final File file, final Table table) throws Exception {
		try (TextFileWriter writer = DataFormat.CSV.createCsvListWriter(file, "UTF-8")) {
			final List<String> headers = new ArrayList<>();
			for (final Column column : table.getColumns()) {
				headers.add(column.getName());
			}
			writer.writeHeader(headers.toArray(new String[0]));
			for (final Row row : table.getRows()) {
				final String[] values = new String[table.getColumns().size()];
				int i = 0;
				for (final Column column : table.getColumns()) {
					values[i++] = column.getFormatter().format(row.get(column));
				}
				writer.writeRow(values);
			}
		}
	}

	private static List<Row> read(final File file) {
		final Table table = newTable();
		table.setRowIteratorHandler(
				FileRowIteratorFactory.create(file, "UTF-8", 1, 1, null, null, null, (r, c, v) -> v));
		final List<Row> rows = new ArrayList<>();
		for (final Row row : table.getRows()) {
			rows.add(row);
		}
		return rows;
	}

	@Test
	public void testRoundTrip() throws Exception {
		final Table table = newTable();
		for (final Object[] values : ROWS) {
			final Row row = table.newRow();
			for (int i = 0; i < values.length; i++) {
				row.put(table.getColumns().get(i), values[i]);
			}
			table.getRows().add(row);
		}
		final File file = new File(directory, "geometries.csv");
		write(file, table);
		final String csv = Files.readString(file.toPath(), StandardCharsets.UTF_8);
		// カンマを含む値は引用符で囲まれる
		assertTrue(csv.contains("\"(1.5,-2.0)\""), csv);
		// 開いたPathと閉じたPathはCSV上でも区別される
		assertTrue(csv.contains("\"[(0.0,0.0),(1.0,1.0),(2.0,0.0)]\""), csv);
		assertTrue(csv.contains("\"((0.0,0.0),(1.0,1.0),(2.0,0.0))\""), csv);

		final List<Row> rows = read(file);
		assertEquals(ROWS.size(), rows.size());
		final Table expectedTable = newTable();
		for (int r = 0; r < ROWS.size(); r++) {
			final Object[] expected = ROWS.get(r);
			for (int i = 1; i < expected.length; i++) {
				final Column column = expectedTable.getColumns().get(i);
				assertEquals(expected[i], rows.get(r).get(column.getName()),
						"row=" + r + ", column=" + column.getName());
			}
		}
		final Path open = rows.get(0).get(DataType.PATH.name());
		assertTrue(open.open);
		final Path closed = rows.get(1).get(DataType.PATH.name());
		assertEquals(false, closed.open);
	}

	/**
	 * 既存の NULL / 空文字の扱い(引用符なしの空欄も、引用符付きの空文字も幾何型では null)を固定する。
	 */
	@Test
	public void testNullAndEmpty() throws Exception {
		final File file = new File(directory, "empty.csv");
		final StringBuilder builder = new StringBuilder();
		builder.append("ID");
		for (final DataType type : TYPES) {
			builder.append(',').append(type.name());
		}
		builder.append('\n');
		builder.append("1");
		for (int i = 0; i < TYPES.length; i++) {
			builder.append(',');
		}
		builder.append('\n');
		builder.append("2");
		for (int i = 0; i < TYPES.length; i++) {
			builder.append(",\"\"");
		}
		builder.append('\n');
		Files.writeString(file.toPath(), builder.toString(), StandardCharsets.UTF_8);
		final List<Row> rows = read(file);
		assertEquals(2, rows.size());
		for (final Row row : rows) {
			for (final DataType type : TYPES) {
				assertNull(row.get(type.name()), type.name());
			}
		}
	}

	/**
	 * PostgreSQL の COPY ... CSV が出力する形式を読み込めること。
	 */
	@Test
	public void testReadPostgresCopyCsv() throws Exception {
		final File file = new File(directory, "copy.csv");
		Files.writeString(file.toPath(), String.join("\n", "ID,POINT,BOX,LSEG,LINE,PATH,POLYGON,CIRCLE",
				"1,\"(1,2)\",\"(1,1),(0,0)\",\"[(0,0),(1,1)]\",\"{1,-1,0}\",\"[(0,0),(1,1)]\",\"((0,0),(1,1),(1,0))\",\"<(0,0),1>\"",
				"2,,,,,\"((0,0),(1,1))\",,", ""), StandardCharsets.UTF_8);
		final List<Row> rows = read(file);
		assertEquals(2, rows.size());
		final Row row = rows.get(0);
		assertEquals(new Point(1, 2), row.get("POINT"));
		assertEquals(new Box(1, 1, 0, 0), row.get("BOX"));
		assertEquals(new Lseg(0, 0, 1, 1), row.get("LSEG"));
		assertEquals(new Line(0, 0, 1, 1), row.get("LINE"));
		assertEquals(new Path(true, 0, 0, 1, 1), row.get("PATH"));
		assertEquals(new Polygon(0, 0, 1, 1, 1, 0), row.get("POLYGON"));
		assertEquals(new Circle(0, 0, 1), row.get("CIRCLE"));
		assertEquals(new Path(false, 0, 0, 1, 1), rows.get(1).get("PATH"));
	}
}
