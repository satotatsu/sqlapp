/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SapHanaSpatialTest {
	private final Dialect[] dialects = { DialectHolder.defaultDialect, DialectHolder.cloudDialect };

	@Test
	void mapsNativeSpatialTypesToGeometry() {
		for (Dialect dialect : dialects) {
			for (String name : new String[] { "ST_GEOMETRY", "ST_POINT" }) {
				Column column = new Column("G");
				dialect.setDbType(name, null, null, column);
				assertEquals(DataType.GEOMETRY, column.getDataType(), name);
				assertEquals("ST_GEOMETRY", column.getDataTypeName());
			}
			assertInstanceOf(SapHanaGeometryJdbcTypeHandler.class,
					dialect.getDbDataTypes().getDbType(DataType.GEOMETRY).getJdbcTypeHandler());
		}
	}

	@Test
	void writesSpatialColumnDdlWithSrid() {
		for (Dialect dialect : dialects) {
			Table table = new Table("PLACES").setDialect(dialect);
			table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
			table.getColumns().add("SHAPE", c -> {
				c.setDataType(DataType.GEOMETRY);
				c.getSpecifics().put(SapHanaSpatial.SRID, "4326");
			});
			table.getColumns().add("LOCATION", c -> {
				c.setDataType(DataType.GEOMETRY);
				c.getSpecifics().put(SapHanaSpatial.TYPE, "st_point");
				c.getSpecifics().put(SapHanaSpatial.SRID, "3857");
			});
			// A geometry column coming from another database has no HANA specifics.
			table.getColumns().add("OTHER", c -> c.setDataType(DataType.GEOMETRY));
			String sql = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE).getFirst().getSqlText();
			assertTrue(sql.contains("SHAPE ST_GEOMETRY(4326)"), sql);
			assertTrue(sql.contains("LOCATION ST_POINT(3857)"), sql);
			assertTrue(sql.contains("OTHER ST_GEOMETRY"), sql);
			table.getColumns().get("SHAPE").getSpecifics().put(SapHanaSpatial.SRID, "EPSG:4326");
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
			table.getColumns().get("SHAPE").getSpecifics().put(SapHanaSpatial.SRID, "4326");
			table.getColumns().get("LOCATION").getSpecifics().put(SapHanaSpatial.TYPE, "ST_POLYGON");
			assertThrows(IllegalArgumentException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		}
	}

	@Test
	void decodesDriverWkbAndEncodesSridAwareEwkb() {
		// Value returned by ngdbc for NEW ST_POINT('POINT(1 2)', 4326).
		Geometry<?> read = SapHanaGeometryJdbcTypeHandler
				.decode(HexFormat.of().parseHex("0101000000000000000000f03f0000000000000040"));
		assertEquals("POINT(1 2)", Wkt.toWkt(read).replace(" (", "("));
		assertNull(SapHanaGeometryJdbcTypeHandler.decode(null));
		// Without an SRID the value is plain WKB, as returned by the driver.
		assertEquals("0101000000000000000000f03f0000000000000040",
				HexFormat.of().formatHex(SapHanaGeometryJdbcTypeHandler.encode(Wkt.fromWkt("POINT(1 2)"))));
		for (String wkt : new String[] { "SRID=4326;POINT(1 2)", "POLYGON((0 0,1 0,1 1,0 0))",
				"SRID=4326;POINT Z(1 2 3)", "POINT Z(1 2 3)" }) {
			Geometry<?> source = Wkt.fromWkt(wkt);
			Geometry<?> roundTrip = SapHanaGeometryJdbcTypeHandler
					.decode(SapHanaGeometryJdbcTypeHandler.encode(source));
			assertEquals(source, roundTrip, wkt);
			assertEquals(source.getSRID(), roundTrip.getSRID(), wkt);
		}
	}
}
