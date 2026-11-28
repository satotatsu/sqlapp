package com.sqlapp.data.db.dialect.mariadb;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class MariadbTinyIntMappingTest {
	@Test
	void configuresNumericModeWithoutChangingDefaultOrProduct() {
		var resolver = DialectResolver.getInstance();
		for (int[] v : new int[][] { { 5, 5, 0 }, { 10, 0, 0 }, { 10, 0, 5 }, { 10, 2, 0 }, { 10, 2, 5 }, { 10, 2, 7 },
				{ 10, 3, 0 }, { 10, 5, 0 }, { 11, 4, 0 }, { 11, 5, 0 }, { 11, 8, 0 }, { 12, 1, 0 } }) {
			var normal = resolver.getDialect("mariadb", v[0], v[1], v[2]);
			var numeric = resolver.getDialect("mariadb", v[0], v[1], v[2], Map.of("mysql.tinyInt1Mapping", "NUMERIC"));
			assertEquals(normal.getClass(), numeric.getClass());
			assertEquals(normal.getProductName(), numeric.getProductName());
			assertNotEquals(normal, numeric);
			var next = numeric;
			for (int count = 0; next != null; count++) {
				assertTrue(count < 30);
				assertEquals("NUMERIC", ((com.sqlapp.data.db.dialect.mysql.MySql) next).getTinyInt1Mapping().name());
				next = next.getNextVersionDialect();
			}
			var c = new Column("v");
			numeric.setDbType("tinyint(1)", null, null, c);
			assertEquals(DataType.TINYINT, c.getDataType());
			normal.setDbType("tinyint(1)", null, null, c);
			assertEquals(DataType.BOOLEAN, c.getDataType());
			assertSame(normal, resolver.applyDialectOptions(numeric, Map.of("mysql.tinyInt1Mapping", "BOOLEAN")));
		}
	}
}
