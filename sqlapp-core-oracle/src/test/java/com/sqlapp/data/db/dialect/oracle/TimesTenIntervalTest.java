/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class TimesTenIntervalTest {
	@Test
	void registersYearAndSecondWithoutOverwritingMinute() {
		var dialect = DialectResolver.getInstance().getDialect("TimesTen", 11, 2, null);
		for (var type : new DataType[] { DataType.INTERVAL_YEAR, DataType.INTERVAL_MONTH, DataType.INTERVAL_DAY,
				DataType.INTERVAL_HOUR, DataType.INTERVAL_MINUTE, DataType.INTERVAL_SECOND }) {
			var nativeType = dialect.getDbDataTypes().getDbTypeStrict(type);
			assertNotNull(nativeType, type.name());
			assertInstanceOf(IntervalTypeHandler.class, nativeType.getJdbcTypeHandler());
			assertEquals(type.name().replace('_', ' '), nativeType.getCreateFormat());
		}
		var year = new Column("C").setDataType(DataType.INTERVAL_YEAR).setLength(9);
		assertEquals("INTERVAL '+1' YEAR(9)", dialect.getSqlValueDefinition(year, IntervalValue.parse("P1Y")));
	}
}
