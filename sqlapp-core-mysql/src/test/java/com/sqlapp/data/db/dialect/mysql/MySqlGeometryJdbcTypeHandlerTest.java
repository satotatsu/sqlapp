package com.sqlapp.data.db.dialect.mysql;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;

import org.geolatte.geom.Geometry;
import org.geolatte.geom.codec.Wkt;
import org.junit.jupiter.api.Test;

class MySqlGeometryJdbcTypeHandlerTest {
	private final MySqlGeometryJdbcTypeHandler handler = new MySqlGeometryJdbcTypeHandler();

	private PreparedStatement statement(AtomicReference<byte[]> bytes) {
		return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (p, method, args) -> {
					assertEquals("setBytes", method.getName());
					assertEquals(2, args[0]);
					bytes.set((byte[]) args[1]);
					return null;
				});
	}

	@Test
	void bindsNullWithoutEncoding() throws Exception {
		var bytes = new AtomicReference<>(new byte[] { 1 });
		handler.setObject(statement(bytes), 2, null);
		assertNull(bytes.get());
	}

	@Test
	void preservesGeometryAndSrid() throws Exception {
		Geometry<?> geometry = Wkt.fromWkt("SRID=4326;POINT(1 2)");
		var bytes = new AtomicReference<byte[]>();
		handler.setObject(statement(bytes), 2, geometry);
		assertNotNull(bytes.get());
		var rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
				(p, method, args) -> bytes.get());
		assertEquals(geometry, handler.getObject(rs, 1));
		assertEquals(geometry, handler.getObject(rs, "geometry"));
	}

	@Test
	void rejectsInvalidNonNullGeometry() {
		var bytes = new AtomicReference<>(new byte[] { 1 });
		assertThrows(RuntimeException.class, () -> handler.setObject(statement(bytes), 2, ""));
		assertArrayEquals(new byte[] { 1 }, bytes.get());
	}
}
