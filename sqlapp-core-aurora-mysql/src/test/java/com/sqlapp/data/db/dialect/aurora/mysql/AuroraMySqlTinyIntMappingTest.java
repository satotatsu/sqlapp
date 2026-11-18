package com.sqlapp.data.db.dialect.aurora.mysql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class AuroraMySqlTinyIntMappingTest {
	@Test
	void configuresNumericModeWithoutChangingDefaultOrProduct() {
		var resolver = DialectResolver.getInstance();
		for (int[] v : new int[][] { { 8, 0 }, { 8, 4 } }) {
			var normal = resolver.getDialect("aurora-mysql", v[0], v[1], 0);
			var numeric = resolver.getDialect("aurora-mysql", v[0], v[1], 0,
					Map.of("mysql.tinyInt1Mapping", "NUMERIC"));
			assertEquals(normal.getClass(), numeric.getClass());
			assertEquals(normal.getProductName(), numeric.getProductName());
			var c = new Column("v");
			numeric.setDbType("tinyint(1)", null, null, c);
			assertEquals(DataType.TINYINT, c.getDataType());
			normal.setDbType("tinyint(1)", null, null, c);
			assertEquals(DataType.BOOLEAN, c.getDataType());
			assertSame(normal, resolver.applyDialectOptions(numeric, Map.of("mysql.tinyInt1Mapping", "BOOLEAN")));
		}
	}
}
