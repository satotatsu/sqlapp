/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;

import org.geolatte.geom.Geometry;
import org.junit.jupiter.api.Test;

class H2GeometryJdbcTypeHandlerTest {
	@Test
	void readsAndWritesNullAndNonNullGeometry() throws Exception {
		var handler = new H2GeometryJdbcTypeHandler();
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE G(ID INT PRIMARY KEY, SHAPE GEOMETRY)");
			statement.execute("INSERT INTO G VALUES(1, 'POINT (1 2)')");
			Object point;
			try (var rows = statement.executeQuery("SELECT SHAPE FROM G WHERE ID=1")) {
				assertTrue(rows.next());
				point = handler.getObject(rows, 1);
				assertInstanceOf(Geometry.class, point);
				assertEquals(point, handler.getObject(rows, "SHAPE"));
			}
			try (var insert = connection.prepareStatement("INSERT INTO G VALUES(?, ?)")) {
				insert.setInt(1, 2);
				handler.setObject(insert, 2, null);
				insert.executeUpdate();
				insert.setInt(1, 3);
				handler.setObject(insert, 2, point);
				insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT ID, SHAPE FROM G WHERE ID IN (2, 3) ORDER BY ID")) {
				assertTrue(rows.next());
				assertNull(handler.getObject(rows, 2));
				assertNull(handler.getObject(rows, "SHAPE"));
				assertTrue(rows.next());
				assertEquals(point, handler.getObject(rows, 2));
			}
		}
	}
}
