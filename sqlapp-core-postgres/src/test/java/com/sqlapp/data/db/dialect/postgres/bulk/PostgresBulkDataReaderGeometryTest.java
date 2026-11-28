/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.postgres.PostgresGeometryJdbcTypeHandler;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;

/**
 * COPY で送る幾何型の形式を固定する(DBには接続しない)。
 *
 * <ul>
 * <li>汎用 GEOMETRY: 通常の INSERT が送るものと同じ EWKB を16進文字列にしたもの</li>
 * <li>組み込みの幾何型: sqlapp の文字列表現(PostgreSQL の入力形式として有効)</li>
 * </ul>
 */
class PostgresBulkDataReaderGeometryTest {

	private static String copyText(final Table table) throws Exception {
		try (PostgresBulkDataReader reader = new PostgresBulkDataReader(table, BulkOption.defaults())) {
			final StringWriter writer = new StringWriter();
			reader.transferTo(writer);
			return writer.toString();
		}
	}

	@Test
	void geometryIsSentAsSameEwkbAsInsert() throws Exception {
		final Table table = new Table("g");
		table.setDialect(DialectHolder.postgreSQL180);
		table.getColumns().add(new Column("g").setDataType(DataType.GEOMETRY));
		table.getRows().add(row -> row.put("g", "SRID=4326;POINT Z(139 35 10)"));
		table.getRows().add(row -> row.put("g", null));
		table.getRows().add(row -> row.put("g", "POINT EMPTY"));
		final String hex = toHex(PostgresGeometryJdbcTypeHandler.toEwkb(Wkt.fromWkt("SRID=4326;POINT Z(139 35 10)")));
		final String emptyHex = toHex(PostgresGeometryJdbcTypeHandler.toEwkb(Wkt.fromWkt("POINT EMPTY")));
		assertEquals("\"" + hex + "\"\n\n\"" + emptyHex + "\"\n", copyText(table));
		// 16進EWKB を読み戻すと元の値になる
		assertEquals("SRID=4326;POINT(139 35 10)",
				Wkt.toWkt(Wkb.fromWkb(ByteBuffer.from(hex), Wkb.Dialect.POSTGIS_EWKB_1)));
	}

	@Test
	void builtInGeometricTypesUseSqlappText() throws Exception {
		final Table table = new Table("g");
		table.setDialect(DialectHolder.postgreSQL180);
		table.getColumns().add(new Column("point").setDataType(DataType.POINT));
		table.getColumns().add(new Column("box").setDataType(DataType.BOX));
		table.getColumns().add(new Column("lseg").setDataType(DataType.LSEG));
		table.getColumns().add(new Column("line").setDataType(DataType.LINE));
		table.getColumns().add(new Column("path").setDataType(DataType.PATH));
		table.getColumns().add(new Column("polygon").setDataType(DataType.POLYGON));
		table.getColumns().add(new Column("circle").setDataType(DataType.CIRCLE));
		table.getRows().add(row -> {
			row.put("point", new Point(1, 2));
			row.put("box", new Box(1, 1, 0, 0));
			row.put("lseg", new Lseg(0, 0, 1, 1));
			row.put("line", new Line(0, 0, 1, 1));
			row.put("path", new Path(true, 0, 0, 1, 1));
			row.put("polygon", new Polygon(0, 0, 1, 1, 1, 0));
			row.put("circle", new Circle(1, 2, 3));
		});
		assertEquals(
				"\"(1.0,2.0)\",\"((1.0,1.0),(0.0,0.0))\",\"((0.0,0.0),(1.0,1.0))\",\"((0.0,0.0),(1.0,1.0))\","
						+ "\"[(0.0,0.0),(1.0,1.0)]\",\"((0.0,0.0),(1.0,1.0),(1.0,0.0))\",\"<(1.0,2.0),3.0>\"\n",
				copyText(table));
	}

	private static String toHex(final byte[] bytes) {
		final StringBuilder builder = new StringBuilder();
		for (final byte b : bytes) {
			builder.append(Character.forDigit((b >>> 4) & 0x0f, 16)).append(Character.forDigit(b & 0x0f, 16));
		}
		return builder.toString();
	}
}
