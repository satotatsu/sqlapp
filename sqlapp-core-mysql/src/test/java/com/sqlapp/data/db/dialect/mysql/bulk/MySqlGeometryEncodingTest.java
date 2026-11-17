/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.mysql.MySqlGeometryJdbcTypeHandler;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;

/**
 * MySQL の GEOMETRY の符号化(DBには接続しない)。
 *
 * MySQL は Z/M と GEOMETRYCOLLECTION 以外の空の図形を保存できず、そのまま符号化すると Z/M が黙って落ちたり
 * POINT EMPTY が GEOMETRYCOLLECTION EMPTY に変わったりする(Claude 側の検証環境の MySQL 8.4 で確認)ため、エラーにする。
 */
class MySqlGeometryEncodingTest {

	@ParameterizedTest
	@ValueSource(strings = { "POINT Z(1 2 3)", "POINT M(1 2 3)", "POINT ZM(1 2 3 4)", "LINESTRING Z(0 0 1,1 1 2)",
			"POINT EMPTY", "LINESTRING EMPTY", "MULTIPOINT EMPTY", "GEOMETRYCOLLECTION(POINT EMPTY)" })
	void rejectsValuesMySqlCannotStore(final String wkt) {
		assertThrows(IllegalArgumentException.class,
				() -> MySqlGeometryJdbcTypeHandler.toMySqlBytes(Wkt.fromWkt(wkt)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "SRID=4326;POINT(139.7 35.6)", "POINT(1 2)", "GEOMETRYCOLLECTION EMPTY",
			"GEOMETRYCOLLECTION(POINT(1 2),LINESTRING(0 0,1 1))",
			"SRID=3857;MULTIPOLYGON(((0 0,1 0,1 1,0 0)),((5 5,6 5,6 6,5 5)))" })
	void encodesValuesMySqlCanStore(final String wkt) {
		final byte[] bytes = MySqlGeometryJdbcTypeHandler.toMySqlBytes(Wkt.fromWkt(wkt));
		assertEquals(Wkt.toWkt(Wkt.fromWkt(wkt)),
				Wkt.toWkt(Wkb.newDecoder(Wkb.Dialect.MYSQL_WKB).decode(ByteBuffer.from(bytes))));
	}

	/**
	 * LOAD DATA では、通常の INSERT と同じ MySQL 内部形式を16進文字列で送る。
	 */
	@Test
	void loadDataSendsSameBytesAsInsert() throws Exception {
		final Table table = new Table("g");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getColumns().add(new Column("g").setDataType(DataType.GEOMETRY));
		table.getRows().add(row -> {
			row.put("id", 1);
			row.put("g", "SRID=4326;POINT(139.7 35.6)");
		});
		table.getRows().add(row -> row.put("id", 2));
		final String hex = HexFormat.of()
				.formatHex(MySqlGeometryJdbcTypeHandler.toMySqlBytes(Wkt.fromWkt("SRID=4326;POINT(139.7 35.6)")));
		try (MySqlBulkDataInputStream input = new MySqlBulkDataInputStream(table, BulkOption.defaults())) {
			assertEquals("1\u001f" + hex + "\u001e2\u001f\\N\u001e",
					new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
