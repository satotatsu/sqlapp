/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Oracle の SDO_GEOMETRY の NULL と空の図形の扱い(DBには接続しない)。
 */
class OracleGeometryJdbcTypeHandlerTest {

	private final List<Object[]> calls = new ArrayList<>();

	private PreparedStatement statement() {
		return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (p, method, args) -> {
					calls.add(new Object[] { method.getName(), args });
					return null;
				});
	}

	@Test
	void bindsAndReadsNull() throws Exception {
		new OracleGeometryJdbcTypeHandler().setObject(statement(), 1, null);
		assertEquals(1, calls.size());
		assertEquals("setNull", calls.get(0)[0]);
		final Object[] args = (Object[]) calls.get(0)[1];
		assertEquals(Types.STRUCT, args[1]);
		assertEquals("MDSYS.SDO_GEOMETRY", args[2]);
		final ResultSet rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (p, method, a) -> null);
		assertNull(new OracleGeometryJdbcTypeHandler().getObject(rs, 1));
		assertNull(new OracleGeometryJdbcTypeHandler().getObject(rs, "g"));
	}

	/**
	 * SDO_GEOMETRY には空の図形の表現がないため、DB に送る前にエラーにする。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "POINT EMPTY", "LINESTRING EMPTY", "GEOMETRYCOLLECTION EMPTY" })
	void rejectsEmptyGeometry(final String wkt) {
		final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new OracleGeometryJdbcTypeHandler().setObject(statement(), 1, wkt));
		assertTrue(e.getMessage().contains("empty"), e.getMessage());
		assertTrue(calls.isEmpty());
	}
}
