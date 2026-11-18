package com.sqlapp.data.db.dialect.aurora.mysql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;

class AuroraMySqlTimestampPrecisionTest {
	@Test
	void preservesInheritedTimestampPrecision() {
		for (int[] v : new int[][] { { 8, 0 }, { 8, 4 } }) {
			var dialect = DialectResolver.getInstance().getDialect("aurora-mysql", v[0], v[1], 0);
			for (int p = 0; p <= 6; p++) {
				var column = new Column("created");
				dialect.setDbType("timestamp(" + p + ")", null, null, column);
				assertEquals(DataType.TIMESTAMPVERSION, column.getDataType());
				assertEquals((long) p, column.getLength());
				assertEquals("TIMESTAMP(" + p + ")", dialect.createSqlBuilder().addTypeDefinition(column).toString());
			}
		}
	}
}
