/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.codec.db.sqlserver.Decoders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.SqlServerGeometryJdbcTypeHandler;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;

/**
 * SQL Server の geometry の符号化(DBには接続しない)。
 */
class SqlServerGeometryEncodingTest {

	/**
	 * Geolatte のエンコーダーは SRID なしの Z/M を符号化できない(IllegalStateException)ため、その回避の確認。
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "POINT Z(1 2 3)|POINT(1 2 3)", "POINT M(1 2 3)|POINTM(1 2 3)",
			"POINT ZM(1 2 3 4)|POINT(1 2 3 4)", "LINESTRING Z(0 0 1,1 1 2)|LINESTRING(0 0 1,1 1 2)",
			"SRID=4326;POINT Z(139.7 35.6 10)|SRID=4326;POINT(139.7 35.6 10)", "POINT(1 2)|POINT(1 2)",
			"POINT EMPTY|POINT EMPTY" })
	void encodesZmWithoutSrid(final String wkt, final String expected) {
		final Geometry<?> geometry = Wkt.fromWkt(wkt);
		final Geometry<?> decoded = Decoders.decode(SqlServerGeometryJdbcTypeHandler.toSqlServerBytes(geometry));
		assertEquals(expected, Wkt.toWkt(decoded));
		assertEquals(geometry.hasZ(), decoded.hasZ());
		assertEquals(geometry.hasM(), decoded.hasM());
	}

	@Test
	void bindsAndReadsNull() throws Exception {
		final List<Object[]> calls = new ArrayList<>();
		final PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (p, method, args) -> {
					calls.add(new Object[] { method.getName(), args });
					return null;
				});
		new SqlServerGeometryJdbcTypeHandler().setObject(statement, 1, null);
		assertEquals(1, calls.size());
		assertEquals("setNull", calls.get(0)[0]);
		assertEquals(Types.VARBINARY, ((Object[]) calls.get(0)[1])[1]);
		final ResultSet rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (p, method, args) -> null);
		assertNull(new SqlServerGeometryJdbcTypeHandler().getObject(rs, 1));
		assertNull(new SqlServerGeometryJdbcTypeHandler().getObject(rs, "g"));
	}

	/**
	 * 一括コピーでは、通常の INSERT と同じシリアル化形式を VARBINARY として渡す。
	 */
	@Test
	void bulkCopySendsSameBytesAsInsert() throws Exception {
		final Table table = new Table("g");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getColumns().add(new Column("g").setDataType(DataType.GEOMETRY));
		table.getRows().add(row -> {
			row.put("id", 1);
			row.put("g", "POINT Z(1 2 3)");
		});
		table.getRows().add(row -> row.put("id", 2));
		try (BulkData data = new BulkData(table, BulkOption.defaults())) {
			assertEquals(Types.VARBINARY, data.getColumnType(2));
			assertTrue(data.next());
			assertArrayEquals(SqlServerGeometryJdbcTypeHandler.toSqlServerBytes(Wkt.fromWkt("POINT Z(1 2 3)")),
					(byte[]) data.getRowData()[1]);
			assertTrue(data.next());
			assertNull(data.getRowData()[1]);
			assertFalse(data.next());
		}
	}
}
